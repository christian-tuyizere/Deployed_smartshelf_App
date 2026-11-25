package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class SmartShelfExtendedColors(
    val canvas: Color,
    val card: Color,
    val elevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentBright: Color,
    val accentMuted: Color,
    val profit: Color,
    val warning: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val critical: Color,
    val criticalBg: Color,
    val criticalBorder: Color,
    val isDark: Boolean
)

val LocalSmartShelfColors = staticCompositionLocalOf {
    SmartShelfExtendedColors(
        canvas = ObsidianCanvas,
        card = ObsidianSurface,
        elevated = ObsidianElevated,
        border = ObsidianBorder,
        textPrimary = ObsidianTextPrimary,
        textSecondary = ObsidianTextSecondary,
        accent = SpotifyGreen,
        accentBright = SpotifyGreenBright,
        accentMuted = SpotifyGreenMutedDark,
        profit = ProfitGreenDark,
        warning = WarningAmberDark,
        warningBg = WarningAmberBgDark,
        warningBorder = WarningAmberBorderDark,
        critical = CriticalRedDark,
        criticalBg = CriticalRedBgDark,
        criticalBorder = CriticalRedBorderDark,
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color(0xFF06130A),
    primaryContainer = SpotifyGreenMutedDark,
    onPrimaryContainer = SpotifyGreenBright,
    secondary = SpotifyGreenBright,
    onSecondary = Color(0xFF06130A),
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = ObsidianTextPrimary,
    background = ObsidianCanvas,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianBorder,
    error = CriticalRedDark,
    onError = Color(0xFF280505),
    errorContainer = CriticalRedBgDark,
    onErrorContainer = CriticalRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = SpotifyGreenDark,
    onPrimary = Color.White,
    primaryContainer = SpotifyGreenMutedLight,
    onPrimaryContainer = Color(0xFF0A4D22),
    secondary = SpotifyGreen,
    onSecondary = Color.White,
    secondaryContainer = DaylightElevated,
    onSecondaryContainer = DaylightTextPrimary,
    background = DaylightCanvas,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightElevated,
    onSurfaceVariant = DaylightTextSecondary,
    outline = DaylightBorder,
    error = CriticalRedLight,
    onError = Color.White,
    errorContainer = CriticalRedBgLight,
    onErrorContainer = CriticalRedLight
)

val SmartShelfShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartShelfTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        SmartShelfExtendedColors(
            canvas = ObsidianCanvas,
            card = ObsidianSurface,
            elevated = ObsidianElevated,
            border = ObsidianBorder,
            textPrimary = ObsidianTextPrimary,
            textSecondary = ObsidianTextSecondary,
            accent = SpotifyGreen,
            accentBright = SpotifyGreenBright,
            accentMuted = SpotifyGreenMutedDark,
            profit = ProfitGreenDark,
            warning = WarningAmberDark,
            warningBg = WarningAmberBgDark,
            warningBorder = WarningAmberBorderDark,
            critical = CriticalRedDark,
            criticalBg = CriticalRedBgDark,
            criticalBorder = CriticalRedBorderDark,
            isDark = true
        )
    } else {
        SmartShelfExtendedColors(
            canvas = DaylightCanvas,
            card = DaylightSurface,
            elevated = DaylightElevated,
            border = DaylightBorder,
            textPrimary = DaylightTextPrimary,
            textSecondary = DaylightTextSecondary,
            accent = SpotifyGreenDark,
            accentBright = SpotifyGreen,
            accentMuted = SpotifyGreenMutedLight,
            profit = ProfitGreenLight,
            warning = WarningAmberLight,
            warningBg = WarningAmberBgLight,
            warningBorder = WarningAmberBorderLight,
            critical = CriticalRedLight,
            criticalBg = CriticalRedBgLight,
            criticalBorder = CriticalRedBorderLight,
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSmartShelfColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmartShelfTypography,
            shapes = SmartShelfShapes,
            content = content
        )
    }
}
