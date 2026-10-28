package ua.kyiv.putivnyk.ui.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the camera sees right now: an appearance fingerprint and ML Kit scene labels. */
data class FrameFeatures(
    val fingerprint: VisualFingerprint?,
    val labels: List<String>,
    val timestamp: Long
)

/**
 * Recognises landmarks by appearance: reference photos of nearby places are turned into
 * [VisualFingerprint]s and compared with the camera view. Combined with the GPS + compass
 * projection this tells apart neighbouring buildings that the geometry alone would confuse.
 */
class LandmarkRecognizer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val references = ConcurrentHashMap<Long, List<VisualFingerprint>>()
    private val requested = ConcurrentHashMap.newKeySet<Long>()
    private val _loadedCount = MutableStateFlow(0)

    /** Changes whenever new reference photos are ready, so the UI can re-score. */
    val loadedCount: StateFlow<Int> = _loadedCount.asStateFlow()

    fun prefetch(places: List<Place>) {
        places.take(MAX_PREFETCH).forEach { place ->
            if (place.imageUrls.isEmpty() || !requested.add(place.id)) return@forEach
            scope.launch {
                val fingerprints = place.imageUrls.take(MAX_PHOTOS_PER_PLACE).mapNotNull { url ->
                    runCatching { loadFingerprint(url) }.getOrNull()
                }
                if (fingerprints.isNotEmpty()) {
                    references[place.id] = fingerprints
                    _loadedCount.update { it + 1 }
                }
            }
        }
    }

    fun hasReference(placeId: Long): Boolean = references.containsKey(placeId)

    /** Best similarity (0..1) of [frame] with the place's reference photos, or null without photos. */
    fun score(placeId: Long, frame: VisualFingerprint): Float? =
        references[placeId]?.maxOfOrNull { it.similarity(frame) }

    fun close() {
        scope.cancel()
    }

    private suspend fun loadFingerprint(url: String): VisualFingerprint? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(REFERENCE_SIZE, REFERENCE_SIZE)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = result.drawable.toBitmap()
        // Photos usually frame the landmark in the middle; trim the edges.
        return fingerprintOf(bitmap, CropBox(0.08f, 0.08f, 0.92f, 0.92f))
    }

    /** Region of an image in relative coordinates (0..1). */
    data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        companion object {
            val CENTER = CropBox(0.2f, 0.15f, 0.8f, 0.75f)
        }
    }

    companion object {
        private const val MAX_PREFETCH = 24
        private const val MAX_PHOTOS_PER_PLACE = 2
        private const val REFERENCE_SIZE = 192
        private const val FRAME_WORKING_WIDTH = 192

        fun fingerprintOf(bitmap: Bitmap, crop: CropBox): VisualFingerprint {
            val left = (crop.left.coerceIn(0f, 1f) * bitmap.width).roundToInt()
            val top = (crop.top.coerceIn(0f, 1f) * bitmap.height).roundToInt()
            val right = max(left + 1, (crop.right.coerceIn(0f, 1f) * bitmap.width).roundToInt().coerceAtMost(bitmap.width))
            val bottom = max(top + 1, (crop.bottom.coerceIn(0f, 1f) * bitmap.height).roundToInt().coerceAtMost(bitmap.height))
            val width = min(right - left, bitmap.width - left)
            val height = min(bottom - top, bitmap.height - top)
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, left, top, width, height)
            return VisualFingerprint.fromArgb(pixels, width, height)
        }

        /** Downscales and rotates a camera frame to the upright view, ready for [fingerprintOf]. */
        fun uprightSmallFrame(frame: Bitmap, rotationDegrees: Int): Bitmap {
            val scale = FRAME_WORKING_WIDTH.toFloat() / max(frame.width, frame.height)
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotationDegrees.toFloat())
            }
            return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
        }

        /** ML Kit labels that fit a category get a small bonus when choosing the landmark. */
        fun labelBonus(category: PlaceCategory, labels: List<String>): Float {
            if (labels.isEmpty()) return 0f
            val hints = when (category) {
                PlaceCategory.CATHEDRAL, PlaceCategory.MONASTERY -> listOf("church", "temple", "cathedral", "dome", "building")
                PlaceCategory.ARCHITECTURE_MONUMENT, PlaceCategory.FAMOUS_PLACE -> listOf("building", "monument", "tower", "castle", "statue", "palace")
                PlaceCategory.MUSEUM, PlaceCategory.THEATER -> listOf("building", "museum", "theatre", "theater", "column")
                PlaceCategory.PARK, PlaceCategory.EMBANKMENT -> listOf("tree", "plant", "garden", "park", "flower", "lake", "river")
                PlaceCategory.SQUARE, PlaceCategory.STREET, PlaceCategory.DISTRICT -> listOf("building", "skyscraper", "city", "road", "crowd")
                PlaceCategory.STADIUM -> listOf("stadium", "sports", "building")
                else -> listOf("building")
            }
            val lower = labels.map { it.lowercase() }
            return if (lower.any { label -> hints.any { it in label } }) LABEL_BONUS else 0f
        }

        private const val LABEL_BONUS = 0.08f
    }
}
