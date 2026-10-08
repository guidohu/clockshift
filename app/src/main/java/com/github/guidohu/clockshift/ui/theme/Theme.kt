package com.clockshift.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF0F8),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF515E7D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E2FF),
    onTertiaryContainer = Color(0xFF0C1A37),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FAFB),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FAFB),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5F5),
    surfaceContainer = Color(0xFFE8EFEF),
    surfaceContainerHigh = Color(0xFFE3E9EA),
    surfaceContainerHighest = Color(0xFFDDE4E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D3DB),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFF9EF0F8),
    secondary = Color(0xFFB0CBCD),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFB9C6EA),
    onTertiary = Color(0xFF223048),
    tertiaryContainer = Color(0xFF394664),
    onTertiaryContainer = Color(0xFFD9E2FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
)

/** Colours that carry meaning for overtime and therefore must not change with the wallpaper. */
@Immutable
data class OvertimeColors(
    val positive: Color,
    val onPositiveContainer: Color,
    val positiveContainer: Color,
    val negative: Color,
    val onNegativeContainer: Color,
    val negativeContainer: Color,
)

private val LightOvertime = OvertimeColors(
    positive = Color(0xFF1E7A43),
    onPositiveContainer = Color(0xFF00210E),
    positiveContainer = Color(0xFFB4F1C4),
    negative = Color(0xFFA2450F),
    onNegativeContainer = Color(0xFF360F00),
    negativeContainer = Color(0xFFFFDBCA),
)

private val DarkOvertime = OvertimeColors(
    positive = Color(0xFF7DDA9A),
    onPositiveContainer = Color(0xFFB4F1C4),
    positiveContainer = Color(0xFF00522A),
    negative = Color(0xFFFFB693),
    onNegativeContainer = Color(0xFFFFDBCA),
    negativeContainer = Color(0xFF7B2F00),
)

private val LocalOvertimeColors = staticCompositionLocalOf { LightOvertime }

val MaterialTheme.overtime: OvertimeColors
    @Composable
    @ReadOnlyComposable
    get() = LocalOvertimeColors.current

@Composable
fun ClockShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalOvertimeColors provides if (darkTheme) DarkOvertime else LightOvertime) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
