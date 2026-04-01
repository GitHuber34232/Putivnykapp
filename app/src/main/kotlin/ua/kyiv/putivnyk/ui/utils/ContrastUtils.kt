package ua.kyiv.putivnyk.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

fun calculateContrastRatio(color1: Color, color2: Color): Float {
    val l1 = color1.luminance()
    val l2 = color2.luminance()

    val lighter = max(l1, l2)
    val darker = min(l1, l2)

    return (lighter + 0.05f) / (darker + 0.05f)
}

fun getAccessibleTextColor(backgroundColor: Color): Color {
    val crWithBlack = calculateContrastRatio(backgroundColor, Color.Black)
    return if (crWithBlack >= 4.5f) Color.Black else Color.White
}
