package org.northwindinteractive.project.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.northwindinteractive.project.core.domain.model.ThemeMode

/*
 * Design tokens (spec: "Design tokens and accessibility").
 *
 * Token            Light                 Dark
 * Canvas           near-white #F7F8FC    midnight navy #0C1429
 * Primary action   indigo #5C59EE        electric indigo #6665FF
 * Primary content  navy #101A3A          off-white #F7F8FF
 * Cards            white #FFFFFF         elevated navy #17213A
 * Success          deep green + text     bright mint + text
 * Alert            muted danger / amber  accessible red / amber on dark surface
 * Shape / rhythm   16–20dp cards, 8dp spacing scale (same geometry in dark; no neon overload)
 */

@Immutable
data class AngelColors(
    val canvas: Color,
    val card: Color,
    val cardBorder: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val content: Color,
    val contentMuted: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val divider: Color,
    val isDark: Boolean,
)

private val LightAngel = AngelColors(
    canvas = Color(0xFFF7F8FC),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE6E8F2),
    primary = Color(0xFF5C59EE),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFFEDEDFE),
    content = Color(0xFF101A3A),
    contentMuted = Color(0xFF5B6380),
    success = Color(0xFF17803D),
    successSoft = Color(0xFFE3F5E9),
    warning = Color(0xFF9A5B00),
    warningSoft = Color(0xFFFFF3DC),
    danger = Color(0xFFC0303B),
    dangerSoft = Color(0xFFFDECEE),
    info = Color(0xFF4A47D6),
    infoSoft = Color(0xFFEEF0FF),
    divider = Color(0xFFE6E8F2),
    isDark = false,
)

private val DarkAngel = AngelColors(
    canvas = Color(0xFF0C1429),
    card = Color(0xFF17213A),
    cardBorder = Color(0xFF26314F),
    primary = Color(0xFF6665FF),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFF232B55),
    content = Color(0xFFF7F8FF),
    contentMuted = Color(0xFFB4BAD3),
    success = Color(0xFF5EE0A1),
    successSoft = Color(0xFF123A30),
    warning = Color(0xFFFFC56B),
    warningSoft = Color(0xFF3A2E14),
    danger = Color(0xFFFF7A82),
    dangerSoft = Color(0xFF3C1A24),
    info = Color(0xFFA9A8FF),
    infoSoft = Color(0xFF1E2750),
    divider = Color(0xFF26314F),
    isDark = true,
)

/** 8dp spacing scale. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Minimum interactive size (WCAG / spec: 48dp touch targets). */
    val minTouch = 48.dp
}

object Radii {
    val card = RoundedCornerShape(18.dp)
    val cardLarge = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(12.dp)
}

val LocalAngelColors = staticCompositionLocalOf { LightAngel }

/** When true, decorative motion (pulses, ring animations) is replaced by static states. */
val LocalReducedMotion = staticCompositionLocalOf { false }

object AngelTheme {
    val colors: AngelColors
        @Composable get() = LocalAngelColors.current
    val reducedMotion: Boolean
        @Composable get() = LocalReducedMotion.current
}

private fun AngelColors.toMaterial(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = content,
        background = canvas,
        onBackground = content,
        surface = card,
        onSurface = content,
        surfaceVariant = card,
        onSurfaceVariant = contentMuted,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        outline = cardBorder,
        outlineVariant = divider,
        error = danger,
        onError = Color(0xFF1A0A0D),
        errorContainer = dangerSoft,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = content,
        background = canvas,
        onBackground = content,
        surface = card,
        onSurface = content,
        surfaceVariant = card,
        onSurfaceVariant = contentMuted,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        outline = cardBorder,
        outlineVariant = divider,
        error = danger,
        onError = Color.White,
        errorContainer = dangerSoft,
    )
}

private val AngelTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    )
}

val CaptionStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)

@Composable
fun AngelTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkAngel else LightAngel
    CompositionLocalProvider(
        LocalAngelColors provides colors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = AngelTypography,
            shapes = Shapes(
                small = Radii.chip,
                medium = Radii.button,
                large = Radii.card,
            ),
            content = content,
        )
    }
}
