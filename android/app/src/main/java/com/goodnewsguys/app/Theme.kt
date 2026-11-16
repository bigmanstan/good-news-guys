package com.goodnewsguys.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

/** Same colours as the website (src/styles/global.css). */
@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    private val cats: Map<String, Color>,
) {
    fun cat(slug: String?): Color = cats[slug] ?: cats.getValue("general")

    /** A soft wash of a colour over the card surface: the bento tile fill. */
    fun tint(color: Color, amount: Float = if (dark) 0.20f else 0.13f): Color = lerp(surface, color, amount)
}

val OnAccent = Color(0xFF1F1300)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFBF8F1), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF4EFE4),
    ink = Color(0xFF1F1C16), muted = Color(0xFF6C6456), line = Color(0xFFE7DFCF),
    accent = Color(0xFFF0A01F), accentInk = Color(0xFF9A5B00), accentSoft = Color(0xFFFDF0D6),
    cats = mapOf(
        "science" to Color(0xFF2F6FB3), "space" to Color(0xFF6D4FC2), "health" to Color(0xFFC2415D),
        "environment" to Color(0xFF2F8A4E), "animals" to Color(0xFFB8661E), "community" to Color(0xFFC0861A),
        "tech" to Color(0xFF0F8A8A), "culture" to Color(0xFFA2458F), "general" to Color(0xFFD07A0A),
        "local" to Color(0xFF3A7F6B),
    ),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF141310), surface = Color(0xFF1C1A16), surface2 = Color(0xFF24211C),
    ink = Color(0xFFF2EEE5), muted = Color(0xFFA69F90), line = Color(0xFF322E27),
    accent = Color(0xFFF4B23E), accentInk = Color(0xFFF7C76C), accentSoft = Color(0xFF3A2C12),
    cats = mapOf(
        "science" to Color(0xFF7FB0E8), "space" to Color(0xFFB29CF0), "health" to Color(0xFFF08AA0),
        "environment" to Color(0xFF78C993), "animals" to Color(0xFFE8A86A), "community" to Color(0xFFE9C068),
        "tech" to Color(0xFF5FCACA), "culture" to Color(0xFFDC8FCD), "general" to Color(0xFFF4B23E),
        "local" to Color(0xFF7CC4AE),
    ),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val Serif = FontFamily.Serif

@Composable
fun GngTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = OnAccent, background = p.bg, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
            outline = p.line,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = OnAccent, background = p.bg, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
            outline = p.line,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
