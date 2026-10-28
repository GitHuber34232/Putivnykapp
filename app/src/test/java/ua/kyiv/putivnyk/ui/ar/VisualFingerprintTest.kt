package ua.kyiv.putivnyk.ui.ar

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class VisualFingerprintTest {

    private val size = 96

    /** A "building": light facade with dark windows in a grid under a blue sky. */
    private fun facade(brightness: Float = 1f, windowColumns: Int = 6): IntArray = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        val (r, g, b) = when {
            y < size / 4 -> Triple(110, 160, 230)
            (x / (size / windowColumns)) % 2 == 0 && (y / 12) % 2 == 1 -> Triple(40, 40, 60)
            else -> Triple(225, 210, 180)
        }
        argb((r * brightness).roundToInt(), (g * brightness).roundToInt(), (b * brightness).roundToInt())
    }

    /** Something else entirely: green foliage with irregular texture. */
    private fun trees(): IntArray = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        val shade = ((x * 7 + y * 13) % 23) * 4
        argb(30 + shade / 3, 110 + shade, 40)
    }

    private fun argb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    @Test
    fun anImageMatchesItselfBest() {
        val a = VisualFingerprint.fromArgb(facade(), size, size)
        assertTrue(a.similarity(a) > 0.95f)
    }

    @Test
    fun theSameFacadeInOtherLightIsCloserThanTrees() {
        val reference = VisualFingerprint.fromArgb(facade(), size, size)
        val evening = VisualFingerprint.fromArgb(facade(brightness = 0.75f), size, size)
        val park = VisualFingerprint.fromArgb(trees(), size, size)
        val same = reference.similarity(evening)
        val different = reference.similarity(park)
        assertTrue("same=$same different=$different", same > different + 0.1f)
    }

    @Test
    fun similarityIsSymmetricAndBounded() {
        val a = VisualFingerprint.fromArgb(facade(), size, size)
        val b = VisualFingerprint.fromArgb(trees(), size, size)
        val ab = a.similarity(b)
        assertTrue(ab in 0f..1f)
        assertTrue(kotlin.math.abs(ab - b.similarity(a)) < 1e-4f)
    }
}
