package ua.kyiv.putivnyk.ui.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory
import kotlinx.coroutines.delay
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trCategory
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

private const val FOCUS_LOCK_DELAY_MS = 350L
private const val FOCUS_RELEASE_DELAY_MS = 900L
private const val MAX_LABELS = 6
private const val ASSUMED_LANDMARK_WIDTH_M = 30.0

private class Holder<T>(var value: T)

/** Object box detected by ML Kit, already mapped into preview-view pixels. */
private data class DetectedBox(val rect: Rect, val isPlace: Boolean)

private data class ArTarget(
    val place: Place,
    val distanceM: Double,
    val bearingDeg: Float,
    val x: Float,
    val y: Float,
    val inFront: Boolean
)

/**
 * Camera AR mode: point the phone at a landmark, the app isolates it from the rest of
 * the frame and slides in a card with information about it.
 *
 * Landmarks are found by projecting nearby places through GPS + compass, then the
 * highlight snaps onto the matching object box from on-device ML Kit object detection.
 */
@Composable
fun ArExploreScreen(
    places: List<Place>,
    hasUserLocation: Boolean,
    userLatitude: Double,
    userLongitude: Double,
    onClose: () -> Unit,
    onOpenDetails: (Place) -> Unit,
    onRouteTo: (Place) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionAsked = true
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var detections by remember { mutableStateOf<List<DetectedBox>>(emptyList()) }
    val tanHalfLong = remember(context) { backCameraTanHalfLongSide(context) }
    val sensorState = rememberCameraPose(context, view.display?.rotation ?: 0)
    val pose = sensorState.pose

    val declination = remember(userLatitude.roundToInt(), userLongitude.roundToInt()) {
        GeomagneticField(
            userLatitude.toFloat(),
            userLongitude.toFloat(),
            150f,
            System.currentTimeMillis()
        ).declination
    }

    val width = viewSize.width.toFloat()
    val height = viewSize.height.toFloat()
    val focalPx = if (width > 0f) ArProjection.focalLengthPx(width, height, tanHalfLong) else 0f

    val targets: List<ArTarget> = if (pose == null || !hasUserLocation || width <= 0f) {
        emptyList()
    } else {
        val results = FloatArray(2)
        places.map { place ->
            Location.distanceBetween(
                userLatitude, userLongitude,
                place.latitude, place.longitude,
                results
            )
            // Rotation vector is referenced to magnetic north; convert the true bearing.
            val magneticBearing = results[1] - declination
            val direction = ArProjection.targetDirection(results[0].toDouble(), magneticBearing.toDouble())
            val point = ArProjection.project(direction, pose, width, height, focalPx)
            ArTarget(
                place = place,
                distanceM = results[0].toDouble(),
                bearingDeg = magneticBearing,
                x = point.x,
                y = point.y,
                inFront = point.inFront
            )
        }
    }
    val onScreen = targets.filter { it.inFront && it.x in (-0.1f * width)..(1.1f * width) }

    // Candidate = the on-screen landmark closest to the crosshair, nearer places win ties.
    val candidate = onScreen
        .filter { abs(it.x - width / 2f) < width * 0.28f }
        .minByOrNull { abs(it.x - width / 2f) / width + it.distanceM / 3000.0 }

    var lockedId by remember { mutableStateOf<Long?>(null) }
    val candidateId = candidate?.place?.id
    LaunchedEffect(candidateId) {
        if (candidateId != null) {
            delay(FOCUS_LOCK_DELAY_MS)
            if (lockedId != candidateId) {
                lockedId = candidateId
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else {
            delay(FOCUS_RELEASE_DELAY_MS)
            lockedId = null
        }
    }
    val locked = lockedId?.let { id -> targets.firstOrNull { it.place.id == id } }
    // Keep the last locked place so the card can animate out with its content.
    // Plain holders: these are refreshed on every sensor frame and must not trigger recomposition.
    val lastLockedPlace = remember { Holder<Pair<Place, Double>?>(null) }
    if (locked != null) lastLockedPlace.value = locked.place to locked.distanceM

    val highlightTarget = locked?.takeIf { it.inFront }?.let { target ->
        highlightRectFor(target, detections, width, height, focalPx)
    }
    val lastHighlight = remember { Holder(Rect.Zero) }
    if (highlightTarget != null) lastHighlight.value = highlightTarget
    val animatedRect by animateRectAsState(
        targetValue = lastHighlight.value,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "arHighlightRect"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlightTarget != null) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "arHighlightAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
    ) {
        if (hasCameraPermission) {
            CameraPreviewWithDetection(
                modifier = Modifier.fillMaxSize(),
                viewSize = viewSize,
                onDetections = { detections = it }
            )
        } else {
            ArMessage(
                icon = Icons.Filled.NoPhotography,
                text = if (permissionAsked) {
                    tr("ar.no_camera_permission", "Немає доступу до камери. Дозвольте його в налаштуваннях.")
                } else {
                    tr("ar.camera_permission", "Потрібен доступ до камери")
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        HighlightOverlay(
            rect = animatedRect,
            alpha = highlightAlpha,
            modifier = Modifier.fillMaxSize()
        )

        // Floating name tags for other landmarks in view.
        onScreen
            .filter { it.place.id != lockedId && it.distanceM <= 1_500.0 }
            .take(MAX_LABELS)
            .forEach { target ->
                LandmarkTag(
                    target = target,
                    maxX = width,
                    maxY = height
                )
            }

        if (highlightTarget != null && locked != null) {
            HighlightTitle(
                title = trDynamic(locked.place.name),
                rect = animatedRect,
                alpha = highlightAlpha
            )
        }

        if (hasCameraPermission && lockedId == null) {
            Crosshair(modifier = Modifier.align(Alignment.Center))
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArStatusChip(
                icon = if (hasUserLocation) Icons.Filled.GpsFixed else Icons.Filled.LocationOff,
                text = when {
                    !hasUserLocation -> tr("ar.waiting_gps", "Очікування GPS…")
                    places.isEmpty() -> tr("ar.no_places_near", "Поруч немає пам'яток")
                    else -> "${tr("ar.places_near", "Пам'яток поруч")}: ${places.size}"
                }
            )
            if (sensorState.needsCalibration) {
                ArStatusChip(
                    icon = Icons.Filled.Explore,
                    text = tr("ar.calibrate", "Відкалібруйте компас: опишіть телефоном «вісімку»")
                )
            }
            if (sensorState.unavailable) {
                ArStatusChip(
                    icon = Icons.Filled.Explore,
                    text = tr("ar.no_compass", "На пристрої немає датчика орієнтації")
                )
            }
        }

        FilledTonalIconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp)
                .size(56.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = tr("ar.close", "Закрити"))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (lockedId == null && hasUserLocation && pose != null && places.isNotEmpty()) {
                val nearest = targets.minByOrNull { it.distanceM }
                val hint = if (onScreen.isEmpty() && nearest != null) nearest else null
                ArStatusChip(
                    icon = when {
                        hint == null -> Icons.Filled.Info
                        ArProjection.angleDelta(pose.headingDeg, hint.bearingDeg) < 0f -> Icons.AutoMirrored.Filled.ArrowBack
                        else -> Icons.AutoMirrored.Filled.ArrowForward
                    },
                    text = if (hint != null) {
                        "${trDynamic(hint.place.name)} · ${formatDistance(hint.distanceM)}"
                    } else {
                        tr("ar.point_camera", "Наведіть камеру на пам'ятку")
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            AnimatedVisibility(
                visible = locked != null,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                ) { it / 2 } + fadeIn(tween(300)) + scaleIn(initialScale = 0.92f),
                exit = slideOutVertically(tween(250)) { it / 2 } + fadeOut(tween(200))
            ) {
                val shown = lastLockedPlace.value
                if (shown != null) {
                    AnimatedContent(
                        targetState = shown,
                        contentKey = { it.first.id },
                        transitionSpec = {
                            (fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 6 })
                                .togetherWith(fadeOut(tween(150)))
                        },
                        label = "arPlaceCard"
                    ) { (place, distance) ->
                        ArPlaceCard(
                            place = place,
                            distanceM = distance,
                            onOpenDetails = { onOpenDetails(place) },
                            onRouteTo = { onRouteTo(place) }
                        )
                    }
                }
            }
        }
    }
}

/** Picks the screen rectangle that should be highlighted for [target]. */
private fun highlightRectFor(
    target: ArTarget,
    detections: List<DetectedBox>,
    width: Float,
    height: Float,
    focalPx: Float
): Rect {
    val margin = width * 0.12f
    val matching = detections
        .filter { box -> target.x in (box.rect.left - margin)..(box.rect.right + margin) }
        .filter { box -> box.rect.width > width * 0.12f && box.rect.height > height * 0.08f }
        .maxByOrNull { box ->
            val centerPenalty = abs(box.rect.center.x - target.x) / width
            val area = (box.rect.width * box.rect.height) / (width * height)
            area + (if (box.isPlace) 0.5f else 0f) - centerPenalty
        }
    if (matching != null) {
        return matching.rect.inflate(width * 0.02f)
    }
    // No object detected: estimate the landmark size from its distance.
    val apparentWidth = (focalPx * ASSUMED_LANDMARK_WIDTH_M / max(target.distanceM, 5.0)).toFloat()
        .coerceIn(width * 0.3f, width * 0.85f)
    val boxHeight = (apparentWidth * 0.9f).coerceAtMost(height * 0.5f)
    val cx = target.x.coerceIn(apparentWidth / 2f, width - apparentWidth / 2f)
    val cy = target.y.coerceIn(height * 0.2f + boxHeight / 2f, height * 0.62f - boxHeight / 2f)
    return Rect(
        left = cx - apparentWidth / 2f,
        top = cy - boxHeight / 2f,
        right = cx + apparentWidth / 2f,
        bottom = cy + boxHeight / 2f
    )
}

@Composable
private fun HighlightOverlay(rect: Rect, alpha: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "arScan")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "arScanLine"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arPulse"
    )
    val accent = MaterialTheme.colorScheme.primaryContainer

    Canvas(modifier = modifier) {
        if (alpha <= 0.01f || rect.width <= 0f || rect.height <= 0f) return@Canvas
        val corner = CornerRadius(28.dp.toPx())

        // Dim everything except the landmark so it stands out from the scene.
        val cutout = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(rect, corner))
        }
        drawPath(cutout, Color.Black.copy(alpha = 0.5f * alpha))

        // Soft glow + border.
        drawRoundRect(
            color = accent.copy(alpha = 0.25f * alpha * pulse),
            topLeft = rect.topLeft - Offset(6.dp.toPx(), 6.dp.toPx()),
            size = rect.inflate(6.dp.toPx()).size,
            cornerRadius = CornerRadius(corner.x + 6.dp.toPx()),
            style = Stroke(width = 10.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f * alpha),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = corner,
            style = Stroke(width = 2.dp.toPx())
        )

        // Corner brackets.
        val len = min(rect.width, rect.height) * 0.18f
        val stroke = 5.dp.toPx()
        val bracketColor = accent.copy(alpha = alpha)
        listOf(
            rect.topLeft to Offset(1f, 1f),
            rect.topRight to Offset(-1f, 1f),
            rect.bottomLeft to Offset(1f, -1f),
            rect.bottomRight to Offset(-1f, -1f)
        ).forEach { (p, dir) ->
            drawLine(bracketColor, p, p + Offset(len * dir.x, 0f), stroke, StrokeCap.Round)
            drawLine(bracketColor, p, p + Offset(0f, len * dir.y), stroke, StrokeCap.Round)
        }

        // Scanning sweep inside the box.
        val y = rect.top + rect.height * scan
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, accent.copy(alpha = 0.35f * alpha)),
                startY = y - rect.height * 0.25f,
                endY = y
            ),
            topLeft = Offset(rect.left, max(rect.top, y - rect.height * 0.25f)),
            size = androidx.compose.ui.geometry.Size(rect.width, max(0f, y - max(rect.top, y - rect.height * 0.25f)))
        )
    }
}

@Composable
private fun HighlightTitle(title: String, rect: Rect, alpha: Float) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, placeable.height) {
                    placeable.place(
                        x = (rect.center.x - placeable.width / 2f).roundToInt(),
                        y = (rect.top - placeable.height - 8.dp.roundToPx()).roundToInt()
                    )
                }
            }
    ) {
        Text(
            text = title,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun LandmarkTag(target: ArTarget, maxX: Float, maxY: Float) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                val x = (target.x - placeable.width / 2f).coerceIn(0f, max(0f, maxX - placeable.width))
                val y = target.y.coerceIn(maxY * 0.15f, maxY * 0.6f) - placeable.height
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = target.place.category.icon, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = trDynamic(target.place.name),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = formatDistance(target.distanceM),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(56.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Color.White.copy(alpha = 0.8f), radius = r, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color.White, radius = 3.dp.toPx())
        val gap = r * 0.45f
        listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
            drawLine(
                Color.White.copy(alpha = 0.8f),
                c + Offset(d.x * gap, d.y * gap),
                c + Offset(d.x * r, d.y * r),
                1.5.dp.toPx()
            )
        }
    }
}

@Composable
private fun ArStatusChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        }
    }
}

@Composable
private fun ArMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(text = text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ArPlaceCard(
    place: Place,
    distanceM: Double,
    onOpenDetails: () -> Unit,
    onRouteTo: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val imageUrl = place.imageUrls.firstOrNull()
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(place.category.icon, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trDynamic(place.name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${place.category.icon} ${trCategory(place.category)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "· ${formatDistance(distanceM)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        place.rating?.let { rating ->
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", rating),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            place.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = trDynamic(description),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenDetails, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("ar.details", "Детальніше"))
                }
                FilledTonalButton(onClick = onRouteTo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.NearMe, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("ar.route", "Маршрут"))
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} м"
    else String.format(java.util.Locale.US, "%.1f км", meters / 1000.0)

/**
 * CameraX preview + ML Kit object detection on the analysis stream.
 * Detected boxes are reported in preview-view pixel coordinates.
 */
@Composable
private fun CameraPreviewWithDetection(
    modifier: Modifier,
    viewSize: IntSize,
    onDetections: (List<DetectedBox>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentViewSize by rememberUpdatedState(viewSize)
    val currentOnDetections by rememberUpdatedState(onDetections)

    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-backed preview composes correctly under Compose overlays and dialogs.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val detector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
            cameraProvider = provider

            val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspect)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(executor) { proxy ->
                analyzeFrame(proxy, detector) { boxes, imageWidth, imageHeight ->
                    val size = currentViewSize
                    if (size.width > 0 && imageWidth > 0) {
                        currentOnDetections(boxes.mapToView(imageWidth, imageHeight, size))
                    }
                }
            }
            analysis = imageAnalysis

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            }.onFailure {
                // Fall back to preview only if the device can't run both use cases.
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            analysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            executor.shutdown()
            detector.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    proxy: androidx.camera.core.ImageProxy,
    detector: com.google.mlkit.vision.objects.ObjectDetector,
    onResult: (List<DetectedBox>, Int, Int) -> Unit
) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val rotation = proxy.imageInfo.rotationDegrees
    val (imageWidth, imageHeight) = if (rotation % 180 == 0) {
        proxy.width to proxy.height
    } else {
        proxy.height to proxy.width
    }
    val input = InputImage.fromMediaImage(mediaImage, rotation)
    runCatching {
        detector.process(input)
            .addOnSuccessListener { objects ->
                val boxes = objects.map { obj ->
                    val b = obj.boundingBox
                    DetectedBox(
                        rect = Rect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()),
                        isPlace = obj.labels.any { it.text == PredefinedCategory.PLACE }
                    )
                }
                onResult(boxes, imageWidth, imageHeight)
            }
            .addOnCompleteListener { proxy.close() }
    }.onFailure { proxy.close() }
}

/** Maps boxes from (rotated) analysis image coordinates to a FILL_CENTER preview. */
private fun List<DetectedBox>.mapToView(imageWidth: Int, imageHeight: Int, view: IntSize): List<DetectedBox> {
    val scale = max(view.width / imageWidth.toFloat(), view.height / imageHeight.toFloat())
    val dx = (view.width - imageWidth * scale) / 2f
    val dy = (view.height - imageHeight * scale) / 2f
    return map { box ->
        box.copy(
            rect = Rect(
                left = box.rect.left * scale + dx,
                top = box.rect.top * scale + dy,
                right = box.rect.right * scale + dx,
                bottom = box.rect.bottom * scale + dy
            )
        )
    }
}

/** tan(fov/2) along the long side of the back camera sensor. */
private fun backCameraTanHalfLongSide(context: Context): Float {
    val fallback = tan(Math.toRadians(34.0)).toFloat()
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return fallback
    return runCatching {
        manager.cameraIdList.firstNotNullOfOrNull { id ->
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                return@firstNotNullOfOrNull null
            }
            val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return@firstNotNullOfOrNull null
            val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()
                ?.takeIf { it > 0f }
                ?: return@firstNotNullOfOrNull null
            max(sensor.width, sensor.height) / (2f * focal)
        }
    }.getOrNull() ?: fallback
}

private class CameraPoseState(
    val pose: ArProjection.CameraPose?,
    val needsCalibration: Boolean,
    val unavailable: Boolean
)

/** Smoothed camera orientation from the rotation-vector sensor. */
@Composable
private fun rememberCameraPose(context: Context, displayRotation: Int): CameraPoseState {
    val lifecycleOwner = LocalLifecycleOwner.current
    var pose by remember { mutableStateOf<ArProjection.CameraPose?>(null) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var unavailable by remember { mutableStateOf(false) }

    DisposableEffect(context, lifecycleOwner, displayRotation) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || sensor == null) {
            unavailable = true
            return@DisposableEffect onDispose { }
        }

        val raw = FloatArray(9)
        val smoothed = FloatArray(9)
        var initialized = false
        var lastEmit = 0L
        val alpha = 0.18f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(raw, event.values)
                if (!initialized) {
                    raw.copyInto(smoothed)
                    initialized = true
                } else {
                    for (i in 0 until 9) smoothed[i] += alpha * (raw[i] - smoothed[i])
                }
                val now = System.currentTimeMillis()
                if (now - lastEmit < 33L) return
                lastEmit = now
                pose = ArProjection.poseFromRotationMatrix(smoothed.copyOf(), displayRotation)
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(listener)
                else -> Unit
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }

    return CameraPoseState(
        pose = pose,
        needsCalibration = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW,
        unavailable = unavailable
    )
}
