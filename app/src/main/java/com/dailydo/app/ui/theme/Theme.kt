package com.dailydo.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class ThemeOption(
    val id: Int,
    val name: String,
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val primary: Color,
    val onBg: Color,
    val outline: Color
)

val LightThemes = listOf(
    ThemeOption(1, "Classic Blue", false, Color(0xFFF5F7FA), Color(0xFFFFFFFF), Color(0xFF007AFF), Color(0xFF1C1C1E), Color(0xFFE0E0E0)),
    ThemeOption(2, "Warm Sand", false, Color(0xFFFAF9F6), Color(0xFFF2EBE1), Color(0xFFD4A373), Color(0xFF3E3A37), Color(0xFFE3D5C1)),
    ThemeOption(3, "Minty Fresh", false, Color(0xFFF4FDF9), Color(0xFFE0F2EB), Color(0xFF26A69A), Color(0xFF1A3B32), Color(0xFFB2DFCD)),
    ThemeOption(4, "Blush Pink", false, Color(0xFFFFF5F8), Color(0xFFFFE4EC), Color(0xFFEC407A), Color(0xFF4A2C39), Color(0xFFF8BBD0)),
    ThemeOption(5, "Cyber Light", false, Color(0xFFF8F9FA), Color(0xFFFFFFFF), Color(0xFF8B5CF6), Color(0xFF212529), Color(0xFFD1D5DB)),
    ThemeOption(6, "Ocean Breeze", false, Color(0xFFF0F8FF), Color(0xFFE0F7FA), Color(0xFF00ACC1), Color(0xFF0D2B45), Color(0xFFB2EBF2)),
    ThemeOption(7, "Lemonade", false, Color(0xFFFFFDF5), Color(0xFFFFF9C4), Color(0xFFFBC02D), Color(0xFF4A3B00), Color(0xFFFFF59D)),
    ThemeOption(8, "Clean Slate", false, Color(0xFFF3F4F6), Color(0xFFFFFFFF), Color(0xFF111827), Color(0xFF374151), Color(0xFFD1D5DB)),
    ThemeOption(9, "Lavender Fields", false, Color(0xFFF8F5FF), Color(0xFFEDE7F6), Color(0xFF673AB7), Color(0xFF3B2A5C), Color(0xFFD1C4E9)),
    ThemeOption(10, "Retro Peach", false, Color(0xFFFFF8F5), Color(0xFFFBE9E7), Color(0xFFFF7043), Color(0xFF5D4037), Color(0xFFFFCCBC))
)

val DarkThemes = listOf(
    ThemeOption(1, "Obsidian", true, Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF5B8DEF), Color(0xFFFFFFFF), Color(0xFF333333)),
    ThemeOption(2, "Midnight Ocean", true, Color(0xFF0A192F), Color(0xFF112240), Color(0xFF64FFDA), Color(0xFFE6F1FF), Color(0xFF233554)),
    ThemeOption(3, "Cyberpunk", true, Color(0xFF090909), Color(0xFF1A1A1A), Color(0xFFD500F9), Color(0xFFFFFFFF), Color(0xFF333333)),
    ThemeOption(4, "Nordic Forest", true, Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFFA3BE8C), Color(0xFFECEFF4), Color(0xFF4C566A)),
    ThemeOption(5, "Sunset Blvd", true, Color(0xFF1C1B1F), Color(0xFF2B2930), Color(0xFFFFB4A2), Color(0xFFE6E1E5), Color(0xFF49454F)),
    ThemeOption(6, "Matrix", true, Color(0xFF000000), Color(0xFF0D0D0D), Color(0xFF00FF41), Color(0xFF00FF41), Color(0xFF003B00)),
    ThemeOption(7, "Deep Purple", true, Color(0xFF120C1C), Color(0xFF1E142E), Color(0xFFAB47BC), Color(0xFFF3E5F5), Color(0xFF311B4D)),
    ThemeOption(8, "Coffee House", true, Color(0xFF1E1815), Color(0xFF2D2420), Color(0xFF8D6E63), Color(0xFFEFEBE9), Color(0xFF4E342E)),
    ThemeOption(9, "Crimson Dark", true, Color(0xFF1A0A0A), Color(0xFF2B1010), Color(0xFFF44336), Color(0xFFFFEBEE), Color(0xFF4A1C1C)),
    ThemeOption(10, "E-Ink Minimal", true, Color(0xFF000000), Color(0xFF121212), Color(0xFFE0E0E0), Color(0xFFFFFFFF), Color(0xFF2A2A2A))
)

fun ThemeOption.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = Color.White,
        background = bg,
        onBackground = onBg,
        surface = surface,
        onSurface = onBg,
        surfaceVariant = surface,
        onSurfaceVariant = onBg.copy(alpha = 0.7f),
        outline = outline
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        background = bg,
        onBackground = onBg,
        surface = surface,
        onSurface = onBg,
        surfaceVariant = surface,
        onSurfaceVariant = onBg.copy(alpha = 0.7f),
        outline = outline
    )
}

@Composable
fun TodoAppTheme(
    displayMode: String = "Dark",
    themeId: Int = 1,
    content: @Composable () -> Unit
) {
    val isDark = displayMode == "Dark"
    val themeList = if (isDark) DarkThemes else LightThemes
    val selectedTheme = themeList.find { it.id == themeId } ?: themeList.first()

    MaterialTheme(
        colorScheme = selectedTheme.toColorScheme(),
        typography = MaterialTheme.typography,
        content = content
    )
}
