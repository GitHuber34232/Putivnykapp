package ua.kyiv.putivnyk.ui.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.utils.RouteUiFormatter
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Route data the AR camera needs to draw navigation on top of the street. */
data class ArNavigationInfo(
    /** Line from the user's position to the destination. */
    val path: List<RoutePoint>,
    val nextStop: RoutePoint?,
    val nextStopName: String?,
    val distanceToNextMeters: Double,
    val remainingMeters: Double,
    val remainingMinutes: Int,
    val completed: Boolean
)

private const val PATH_AHEAD_METERS = 160.0
private const val SAMPLE_STEP_METERS = 4.0
private const val CHEVRON_EVERY_SAMPLES = 3
private const val LOOK_AHEAD_METERS = 25.0

/** A sampled point of the path in the magnetic ENU frame, metres from the user. */
private data class GroundPoint(val east: Double, val north: Double, val along: Double)

/**
 * Draws the route on the camera image: a ribbon with chevrons along the street ahead and a pin
 * over the next stop. Points come from the walking route and are projected with the same
 * pinhole model as the landmark labels.
 */
@Composable
fun ArRouteOverlay(
    info: ArNavigationInfo,
    pose: ArProjection.CameraPose,
    userLatitude: Double,
    userLongitude: Double,
    declinationDeg: Float,
    width: Float,
    height: Float,
    focalPx: Float,
    modifier: Modifier = Modifier
) {
    val samples = sampleAhead(info.path, userLatitude, userLongitude, declinationDeg)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (samples.size < 2 || width <= 0f) return@Canvas
        val projected = samples.map { p ->
            val dir = ArProjection.groundDirection(p.east, p.north)
            val point = ArProjection.project(dir, pose, width, height, focalPx)
            Triple(p, point, hypot(p.east, p.north))
        }
        // Ribbon: wide close to the user, narrow in the distance.
        for (i in 0 until projected.lastIndex) {
            val (_, a, distA) = projected[i]
            val (_, b, _) = projected[i + 1]
            if (!a.inFront || !b.inFront) continue
            if (!a.x.isFinite() || !b.x.isFinite()) continue
            val strokeWidth = (48.dp.toPx() / max(1.0, distA / 6.0)).toFloat().coerceIn(4.dp.toPx(), 40.dp.toPx())
            drawLine(
                color = accent.copy(alpha = 0.38f),
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        // Chevrons pointing along the route.
        projected.forEachIndexed { index, (_, point, dist) ->
            if (index == 0 || index % CHEVRON_EVERY_SAMPLES != 0 || index >= projected.lastIndex) return@forEachIndexed
            val next = projected[index + 1].second
            if (!point.inFront || !next.inFront || !point.x.isFinite() || !next.x.isFinite()) return@forEachIndexed
            val angle = atan2(next.y - point.y, next.x - point.x)
            val size = (34.dp.toPx() / max(1.0, dist / 6.0)).toFloat().coerceIn(6.dp.toPx(), 28.dp.toPx())
            val cos = kotlin.math.cos(angle)
            val sin = kotlin.math.sin(angle)
            val rotated = Path().apply {
                fun map(x: Float, y: Float) = Offset(point.x + x * cos - y * sin, point.y + x * sin + y * cos)
                val p0 = map(-size * 0.55f, -size * 0.6f)
                val p1 = map(size * 0.45f, 0f)
                val p2 = map(-size * 0.55f, size * 0.6f)
                moveTo(p0.x, p0.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
            }
            drawPath(
                rotated,
                color = Color.White.copy(alpha = 0.92f),
                style = Stroke(width = (size * 0.28f).coerceAtLeast(2.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/** Pin over the next stop, placed by projecting its position. */
@Composable
fun ArNextStopPin(
    info: ArNavigationInfo,
    pose: ArProjection.CameraPose,
    userLatitude: Double,
    userLongitude: Double,
    declinationDeg: Float,
    width: Float,
    height: Float,
    focalPx: Float
) {
    val stop = info.nextStop ?: return
    val (trueEast, trueNorth) = ArProjection.offsetMeters(userLatitude, userLongitude, stop.latitude, stop.longitude)
    val (east, north) = ArProjection.toMagnetic(trueEast, trueNorth, declinationDeg)
    val point = ArProjection.project(ArProjection.groundDirection(east, north, heightM = 3.0), pose, width, height, focalPx)
    if (!point.inFront || !point.x.isFinite() || point.x < -width * 0.1f || point.x > width * 1.1f) return
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = 4.dp,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                val x = (point.x - placeable.width / 2f).coerceIn(0f, max(0f, width - placeable.width))
                val y = (point.y - placeable.height).coerceIn(height * 0.12f, height * 0.7f)
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = info.nextStopName?.let { trDynamic(it) } ?: tr("ar.next_stop", "Наступна точка"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = RouteUiFormatter.formatDistance(info.distanceToNextMeters),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** Big turn arrow + instruction that always points along the route, even off screen. */
@Composable
fun ArDirectionPanel(
    info: ArNavigationInfo,
    pose: ArProjection.CameraPose,
    userLatitude: Double,
    userLongitude: Double,
    declinationDeg: Float,
    modifier: Modifier = Modifier
) {
    val target = lookAheadTarget(info, userLatitude, userLongitude, declinationDeg)
    val relative = if (target == null) 0f else {
        val bearing = Math.toDegrees(atan2(target.first, target.second)).toFloat()
        ArProjection.angleDelta(pose.headingDeg, bearing)
    }
    val animated by animateFloatAsState(targetValue = relative, animationSpec = tween(250), label = "arTurnArrow")
    val instruction = when {
        info.completed -> tr("ar.arrived", "Ви на місці!")
        abs(relative) < 20f -> tr("ar.go_straight", "Прямо")
        abs(relative) < 60f -> if (relative < 0f) tr("ar.bear_left", "Тримайтеся лівіше") else tr("ar.bear_right", "Тримайтеся правіше")
        abs(relative) < 150f -> if (relative < 0f) tr("ar.turn_left", "Поверніть ліворуч") else tr("ar.turn_right", "Поверніть праворуч")
        else -> tr("ar.turn_around", "Розверніться")
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { rotationZ = if (info.completed) 0f else animated },
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (info.completed) Icons.Filled.Flag else Icons.Filled.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = instruction, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!info.completed) {
                    Text(
                        text = buildString {
                            info.nextStopName?.let { append(trDynamic(it)); append(" · ") }
                            append(RouteUiFormatter.formatDistance(info.distanceToNextMeters))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tr("ar.remaining", "До фінішу")}: ${RouteUiFormatter.formatDistance(info.remainingMeters)} · " +
                            RouteUiFormatter.formatDuration(info.remainingMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

/** Samples the path ahead every few metres, in the magnetic frame. */
private fun sampleAhead(path: List<RoutePoint>, userLat: Double, userLon: Double, declination: Float): List<GroundPoint> {
    if (path.size < 2) return emptyList()
    val local = path.map { point ->
        val (e, n) = ArProjection.offsetMeters(userLat, userLon, point.latitude, point.longitude)
        ArProjection.toMagnetic(e, n, declination)
    }
    val samples = mutableListOf<GroundPoint>()
    var travelled = 0.0
    var carry = 0.0
    for (i in 0 until local.lastIndex) {
        val (e0, n0) = local[i]
        val (e1, n1) = local[i + 1]
        val segment = hypot(e1 - e0, n1 - n0)
        if (segment < 0.01) continue
        var t = carry
        while (t <= segment) {
            val f = t / segment
            samples += GroundPoint(e0 + (e1 - e0) * f, n0 + (n1 - n0) * f, travelled + t)
            t += SAMPLE_STEP_METERS
        }
        carry = t - segment
        travelled += segment
        if (travelled > PATH_AHEAD_METERS) break
    }
    return samples.filter { it.along <= PATH_AHEAD_METERS }
}

/** East/north (magnetic) of the point the user should walk towards now. */
private fun lookAheadTarget(info: ArNavigationInfo, userLat: Double, userLon: Double, declination: Float): Pair<Double, Double>? {
    val samples = sampleAhead(info.path, userLat, userLon, declination)
    val ahead = samples.firstOrNull { it.along >= LOOK_AHEAD_METERS && hypot(it.east, it.north) > 8.0 }
        ?: samples.lastOrNull { hypot(it.east, it.north) > 3.0 }
    if (ahead != null) return ahead.east to ahead.north
    val stop = info.nextStop ?: return null
    val (e, n) = ArProjection.offsetMeters(userLat, userLon, stop.latitude, stop.longitude)
    return ArProjection.toMagnetic(e, n, declination)
}
