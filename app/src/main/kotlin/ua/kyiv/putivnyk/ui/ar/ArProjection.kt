package ua.kyiv.putivnyk.ui.ar

import android.view.Surface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure math for projecting geographic targets onto the camera preview.
 *
 * World frame is ENU (x = east, y = north, z = up), the same frame that
 * [android.hardware.SensorManager.getRotationMatrixFromVector] maps device vectors into.
 */
object ArProjection {

    /** Assumed height of the eye/phone above ground, metres. */
    private const val EYE_HEIGHT_M = 1.5

    /** Point of a landmark we aim at (roughly the middle of a facade), metres. */
    private const val TARGET_HEIGHT_M = 8.0

    data class CameraPose(
        val right: FloatArray,
        val up: FloatArray,
        val look: FloatArray
    ) {
        /** Heading of the camera axis, degrees from (magnetic) north, 0..360. */
        val headingDeg: Float
            get() {
                val deg = Math.toDegrees(atan2(look[0].toDouble(), look[1].toDouble())).toFloat()
                return if (deg < 0f) deg + 360f else deg
            }
    }

    data class ScreenPoint(val x: Float, val y: Float, val inFront: Boolean)

    /**
     * Builds the camera pose from a row-major 3x3 rotation matrix (device -> world).
     * The screen right/up axes depend on how the display is rotated relative to the device.
     */
    fun poseFromRotationMatrix(r: FloatArray, displayRotation: Int): CameraPose {
        fun world(dx: Float, dy: Float, dz: Float) = floatArrayOf(
            r[0] * dx + r[1] * dy + r[2] * dz,
            r[3] * dx + r[4] * dy + r[5] * dz,
            r[6] * dx + r[7] * dy + r[8] * dz
        )

        val (right, up) = when (displayRotation) {
            Surface.ROTATION_90 -> world(0f, 1f, 0f) to world(-1f, 0f, 0f)
            Surface.ROTATION_180 -> world(-1f, 0f, 0f) to world(0f, -1f, 0f)
            Surface.ROTATION_270 -> world(0f, -1f, 0f) to world(1f, 0f, 0f)
            else -> world(1f, 0f, 0f) to world(0f, 1f, 0f)
        }
        // The back camera looks along device -Z.
        return CameraPose(right = right, up = up, look = world(0f, 0f, -1f))
    }

    /** Unit ENU direction from the user to a target at [distanceM] / [bearingDeg]. */
    fun targetDirection(distanceM: Double, bearingDeg: Double): FloatArray {
        val elevation = atan2(TARGET_HEIGHT_M - EYE_HEIGHT_M, max(distanceM, 1.0))
        val bearing = Math.toRadians(bearingDeg)
        val horizontal = cos(elevation)
        return floatArrayOf(
            (sin(bearing) * horizontal).toFloat(),
            (cos(bearing) * horizontal).toFloat(),
            sin(elevation).toFloat()
        )
    }

    /**
     * Pinhole projection of an ENU direction onto a view of [viewWidth] x [viewHeight] px
     * with focal length [focalPx] (px).
     */
    fun project(
        direction: FloatArray,
        pose: CameraPose,
        viewWidth: Float,
        viewHeight: Float,
        focalPx: Float
    ): ScreenPoint {
        val xc = dot(direction, pose.right)
        val yc = dot(direction, pose.up)
        val zc = dot(direction, pose.look)
        if (zc <= 0.05f) {
            return ScreenPoint(
                x = if (xc >= 0f) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY,
                y = viewHeight / 2f,
                inFront = false
            )
        }
        return ScreenPoint(
            x = viewWidth / 2f + focalPx * xc / zc,
            y = viewHeight / 2f - focalPx * yc / zc,
            inFront = true
        )
    }

    /**
     * Focal length in view pixels for a 4:3 camera stream shown with FILL_CENTER.
     *
     * @param tanHalfLong tan(fov/2) along the long side of the sensor.
     */
    fun focalLengthPx(viewWidth: Float, viewHeight: Float, tanHalfLong: Float): Float {
        val longView = max(viewWidth, viewHeight)
        val shortView = minOf(viewWidth, viewHeight)
        // FILL_CENTER: scale so that the 4:3 frame covers the whole view.
        val displayedLong = max(longView, shortView * 4f / 3f)
        return displayedLong / (2f * tanHalfLong)
    }

    /** Signed angle from [from] to [to] in degrees, range -180..180. */
    fun angleDelta(from: Float, to: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        val n = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]).takeIf { it > 1e-6f } ?: 1f
        return (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / n
    }
}
