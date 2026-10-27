package ua.kyiv.putivnyk.ui.ar

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArProjectionTest {

    // Portrait phone held upright, back camera facing north:
    // device X = east, device Y = up, device Z (towards the user) = south.
    private val uprightFacingNorth = floatArrayOf(
        1f, 0f, 0f,
        0f, 0f, -1f,
        0f, 1f, 0f
    )

    private val pose = ArProjection.poseFromRotationMatrix(uprightFacingNorth, Surface.ROTATION_0)

    @Test
    fun headingOfUprightPhoneFacingNorthIsZero() {
        assertEquals(0f, pose.headingDeg, 0.01f)
    }

    @Test
    fun targetStraightAheadProjectsToHorizontalCenter() {
        val dir = ArProjection.targetDirection(distanceM = 200.0, bearingDeg = 0.0)
        val point = ArProjection.project(dir, pose, 1080f, 2400f, focalPx = 1500f)
        assertTrue(point.inFront)
        assertEquals(540f, point.x, 0.5f)
        // Target is slightly above eye level.
        assertTrue(point.y < 1200f)
    }

    @Test
    fun targetToTheEastAppearsOnTheRight() {
        val dir = ArProjection.targetDirection(distanceM = 200.0, bearingDeg = 20.0)
        val point = ArProjection.project(dir, pose, 1080f, 2400f, focalPx = 1500f)
        assertTrue(point.inFront)
        assertTrue(point.x > 540f)
    }

    @Test
    fun targetBehindIsNotInFront() {
        val dir = ArProjection.targetDirection(distanceM = 200.0, bearingDeg = 180.0)
        val point = ArProjection.project(dir, pose, 1080f, 2400f, focalPx = 1500f)
        assertFalse(point.inFront)
    }

    @Test
    fun angleDeltaWrapsAround() {
        assertEquals(20f, ArProjection.angleDelta(350f, 10f), 0.001f)
        assertEquals(-20f, ArProjection.angleDelta(10f, 350f), 0.001f)
    }

    @Test
    fun focalLengthCoversPortraitViewWithFourThreeFrame() {
        // 1080x2400 portrait: the 4:3 frame is scaled so its long side spans 2400 px.
        val f = ArProjection.focalLengthPx(1080f, 2400f, tanHalfLong = 0.6f)
        assertEquals(2000f, f, 0.5f)
    }
}
