package ua.kyiv.putivnyk.ui.ar

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Compact appearance descriptor of an image region, used to match the camera view against
 * reference photos of nearby landmarks. It combines a hue/saturation histogram (colour of
 * the facade, domes, greenery), a HOG-like edge-orientation grid (shape of the building) and
 * a 64-bit difference hash (overall layout). All maths is plain Kotlin on ARGB pixels.
 */
class VisualFingerprint private constructor(
    private val colorHistogram: FloatArray,
    private val edgeHistogram: FloatArray,
    private val layoutHash: Long
) {
    /** 0..1, higher means more alike. */
    fun similarity(other: VisualFingerprint): Float {
        val color = histogramIntersection(colorHistogram, other.colorHistogram)
        val edges = cosine(edgeHistogram, other.edgeHistogram)
        val layout = 1f - java.lang.Long.bitCount(layoutHash xor other.layoutHash) / 64f
        return (EDGE_WEIGHT * edges + COLOR_WEIGHT * color + LAYOUT_WEIGHT * layout).coerceIn(0f, 1f)
    }

    companion object {
        const val SIZE = 64
        private const val CELLS = 4
        private const val ORIENTATIONS = 8
        private const val HUE_BINS = 12
        private const val SAT_BINS = 2
        private const val GRAY_BINS = 4
        private const val EDGE_WEIGHT = 0.45f
        private const val COLOR_WEIGHT = 0.35f
        private const val LAYOUT_WEIGHT = 0.20f

        /** Builds a fingerprint from ARGB pixels of any size; the image is resampled to [SIZE]². */
        fun fromArgb(pixels: IntArray, width: Int, height: Int): VisualFingerprint {
            require(width > 0 && height > 0 && pixels.size >= width * height) { "Bad image" }
            val r = FloatArray(SIZE * SIZE)
            val g = FloatArray(SIZE * SIZE)
            val b = FloatArray(SIZE * SIZE)
            // Box-filter resampling: every target pixel averages its source block.
            for (ty in 0 until SIZE) {
                val y0 = ty * height / SIZE
                val y1 = max(y0 + 1, (ty + 1) * height / SIZE)
                for (tx in 0 until SIZE) {
                    val x0 = tx * width / SIZE
                    val x1 = max(x0 + 1, (tx + 1) * width / SIZE)
                    var sr = 0f
                    var sg = 0f
                    var sb = 0f
                    var n = 0
                    for (y in y0 until min(y1, height)) {
                        val row = y * width
                        for (x in x0 until min(x1, width)) {
                            val c = pixels[row + x]
                            sr += (c shr 16 and 0xFF)
                            sg += (c shr 8 and 0xFF)
                            sb += (c and 0xFF)
                            n++
                        }
                    }
                    val i = ty * SIZE + tx
                    val count = max(n, 1)
                    r[i] = sr / count
                    g[i] = sg / count
                    b[i] = sb / count
                }
            }
            val gray = FloatArray(SIZE * SIZE) { i -> 0.299f * r[i] + 0.587f * g[i] + 0.114f * b[i] }
            return VisualFingerprint(
                colorHistogram = colorHistogram(r, g, b),
                edgeHistogram = edgeHistogram(gray),
                layoutHash = differenceHash(gray)
            )
        }

        private fun colorHistogram(r: FloatArray, g: FloatArray, b: FloatArray): FloatArray {
            val histogram = FloatArray(HUE_BINS * SAT_BINS + GRAY_BINS)
            for (i in r.indices) {
                val rf = r[i] / 255f
                val gf = g[i] / 255f
                val bf = b[i] / 255f
                val maxC = max(rf, max(gf, bf))
                val minC = min(rf, min(gf, bf))
                val delta = maxC - minC
                val saturation = if (maxC <= 0f) 0f else delta / maxC
                if (saturation < 0.18f || maxC < 0.12f) {
                    // Greys (sky, stone, asphalt) are binned by brightness instead of hue.
                    val bin = min(GRAY_BINS - 1, (maxC * GRAY_BINS).toInt())
                    histogram[HUE_BINS * SAT_BINS + bin] += 1f
                    continue
                }
                var hue = when (maxC) {
                    rf -> 60f * (((gf - bf) / delta) % 6f)
                    gf -> 60f * (((bf - rf) / delta) + 2f)
                    else -> 60f * (((rf - gf) / delta) + 4f)
                }
                if (hue < 0f) hue += 360f
                val hueBin = min(HUE_BINS - 1, (hue / 360f * HUE_BINS).toInt())
                val satBin = if (saturation < 0.5f) 0 else 1
                histogram[hueBin * SAT_BINS + satBin] += 1f
            }
            val total = histogram.sum().takeIf { it > 0f } ?: 1f
            for (i in histogram.indices) histogram[i] /= total
            return histogram
        }

        private fun edgeHistogram(gray: FloatArray): FloatArray {
            val cellSize = SIZE / CELLS
            val histogram = FloatArray(CELLS * CELLS * ORIENTATIONS)
            for (y in 1 until SIZE - 1) {
                for (x in 1 until SIZE - 1) {
                    fun p(dx: Int, dy: Int) = gray[(y + dy) * SIZE + (x + dx)]
                    val gx = (p(1, -1) + 2 * p(1, 0) + p(1, 1)) - (p(-1, -1) + 2 * p(-1, 0) + p(-1, 1))
                    val gy = (p(-1, 1) + 2 * p(0, 1) + p(1, 1)) - (p(-1, -1) + 2 * p(0, -1) + p(1, -1))
                    val magnitude = sqrt(gx * gx + gy * gy)
                    if (magnitude < 8f) continue
                    // Unsigned orientation: a facade edge looks the same lit from either side.
                    var angle = atan2(gy, gx)
                    if (angle < 0f) angle += Math.PI.toFloat()
                    val bin = min(ORIENTATIONS - 1, (angle / Math.PI.toFloat() * ORIENTATIONS).toInt())
                    val cell = (y / cellSize).coerceAtMost(CELLS - 1) * CELLS + (x / cellSize).coerceAtMost(CELLS - 1)
                    histogram[cell * ORIENTATIONS + bin] += magnitude
                }
            }
            // Per-cell normalisation keeps the descriptor robust to contrast changes.
            for (cell in 0 until CELLS * CELLS) {
                var norm = 0f
                for (o in 0 until ORIENTATIONS) norm += histogram[cell * ORIENTATIONS + o].let { it * it }
                val scale = 1f / (sqrt(norm) + 1e-3f)
                for (o in 0 until ORIENTATIONS) histogram[cell * ORIENTATIONS + o] *= scale
            }
            return histogram
        }

        private fun differenceHash(gray: FloatArray): Long {
            // 9x8 thumbnail: each bit says whether a pixel is brighter than its right neighbour.
            val w = 9
            val h = 8
            val small = FloatArray(w * h)
            for (ty in 0 until h) {
                for (tx in 0 until w) {
                    val x0 = tx * SIZE / w
                    val x1 = max(x0 + 1, (tx + 1) * SIZE / w)
                    val y0 = ty * SIZE / h
                    val y1 = max(y0 + 1, (ty + 1) * SIZE / h)
                    var sum = 0f
                    var n = 0
                    for (y in y0 until y1) for (x in x0 until x1) {
                        sum += gray[y * SIZE + x]
                        n++
                    }
                    small[ty * w + tx] = sum / max(n, 1)
                }
            }
            var hash = 0L
            var bit = 0
            for (y in 0 until h) {
                for (x in 0 until w - 1) {
                    if (small[y * w + x] > small[y * w + x + 1]) hash = hash or (1L shl bit)
                    bit++
                }
            }
            return hash
        }

        private fun histogramIntersection(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            for (i in a.indices) sum += min(a[i], b[i])
            return sum.coerceIn(0f, 1f)
        }

        private fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var na = 0f
            var nb = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                na += a[i] * a[i]
                nb += b[i] * b[i]
            }
            if (na <= 0f || nb <= 0f) return if (abs(na - nb) < 1e-6f) 1f else 0f
            return (dot / (sqrt(na) * sqrt(nb))).coerceIn(0f, 1f)
        }
    }
}
