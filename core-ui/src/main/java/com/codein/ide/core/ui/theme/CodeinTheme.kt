package com.codein.ide.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.prefs.AppThemeMode
import com.codein.ide.core.ui.prefs.EditorPreferences

private val CodeinDarkColors = darkColorScheme(
    primary = CodeinPalette.Accent,
    onPrimary = CodeinPalette.AccentOn,
    primaryContainer = CodeinPalette.AccentContainer,
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = CodeinPalette.Teal,
    onSecondary = CodeinPalette.AccentOn,
    secondaryContainer = CodeinPalette.TealContainer,
    onSecondaryContainer = Color(0xFFB7F5EC),
    tertiary = CodeinPalette.Sky,
    onTertiary = CodeinPalette.AccentOn,
    background = CodeinPalette.Background,
    onBackground = CodeinPalette.TextPrimary,
    surface = CodeinPalette.Background,
    onSurface = CodeinPalette.TextPrimary,
    surfaceVariant = CodeinPalette.Card,
    onSurfaceVariant = CodeinPalette.TextSecondary,
    surfaceContainerLowest = Color(0xFF0B0D12),
    surfaceContainerLow = CodeinPalette.Panel,
    surfaceContainer = CodeinPalette.Panel,
    surfaceContainerHigh = CodeinPalette.Card,
    surfaceContainerHighest = CodeinPalette.CardHover,
    outline = CodeinPalette.HairlineStrong,
    outlineVariant = CodeinPalette.Hairline,
    error = CodeinPalette.Error,
    onError = CodeinPalette.AccentOn,
    errorContainer = CodeinPalette.ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = CodeinPalette.TextPrimary,
    inverseOnSurface = CodeinPalette.Background,
    scrim = Color(0xCC000000),
)

private val CodeinLightColors = lightColorScheme(
    primary = CodeinPalette.LightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF22145A),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF04332F),
    background = CodeinPalette.LightBackground,
    onBackground = CodeinPalette.LightTextPrimary,
    surface = CodeinPalette.LightBackground,
    onSurface = CodeinPalette.LightTextPrimary,
    surfaceVariant = CodeinPalette.LightCard,
    onSurfaceVariant = CodeinPalette.LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CodeinPalette.LightPanel,
    surfaceContainer = CodeinPalette.LightPanel,
    surfaceContainerHigh = CodeinPalette.LightCard,
    surfaceContainerHighest = Color(0xFFE4E8ED),
    outline = Color(0xFFAFB8C1),
    outlineVariant = CodeinPalette.LightHairline,
    error = Color(0xFFCF222E),
    onError = Color.White,
)

/** Extra semantic colors that Material 3 has no slot for. */
@Immutable
data class CodeinExtendedColors(
    val hairline: Color,
    val panel: Color,
    val card: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val textMuted: Color,
    val glass: Color,
)

private val DarkExtended = CodeinExtendedColors(
    hairline = CodeinPalette.Hairline,
    panel = CodeinPalette.Panel,
    card = CodeinPalette.Card,
    success = CodeinPalette.Success,
    warning = CodeinPalette.Warning,
    info = CodeinPalette.Info,
    textMuted = CodeinPalette.TextMuted,
    glass = Color(0xD9161922),
)

private val LightExtended = CodeinExtendedColors(
    hairline = CodeinPalette.LightHairline,
    panel = CodeinPalette.LightPanel,
    card = CodeinPalette.LightCard,
    success = Color(0xFF1A7F37),
    warning = Color(0xFF9A6700),
    info = Color(0xFF0969DA),
    textMuted = Color(0xFF8C959F),
    glass = Color(0xE6FFFFFF),
)

val LocalCodeinColors = staticCompositionLocalOf { DarkExtended }
val LocalEditorScheme = staticCompositionLocalOf { EditorColorSchemes.CatppuccinMocha }
val LocalEditorPreferences = staticCompositionLocalOf { EditorPreferences() }

val CodeinShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Accessors mirroring [MaterialTheme] for Codein-specific tokens. */
object CodeinTheme {
    val colors: CodeinExtendedColors
        @Composable get() = LocalCodeinColors.current
    val editorScheme: EditorColorScheme
        @Composable get() = LocalEditorScheme.current
    val editorPreferences: EditorPreferences
        @Composable get() = LocalEditorPreferences.current
}

@Composable
fun CodeinTheme(
    preferences: EditorPreferences = EditorPreferences(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (preferences.themeMode) {
        AppThemeMode.Dark -> true
        AppThemeMode.Light -> false
        AppThemeMode.System -> systemDark
    }
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) {
                dynamicDarkColorScheme(context).copy(
                    background = CodeinPalette.Background,
                    surface = CodeinPalette.Background,
                    surfaceContainerLow = CodeinPalette.Panel,
                    surfaceContainer = CodeinPalette.Panel,
                    surfaceContainerHigh = CodeinPalette.Card,
                    outlineVariant = CodeinPalette.Hairline,
                )
            } else {
                dynamicLightColorScheme(context)
            }
        dark -> CodeinDarkColors
        else -> CodeinLightColors
    }
    CompositionLocalProvider(
        LocalCodeinColors provides if (dark) DarkExtended else LightExtended,
        LocalEditorScheme provides preferences.editorScheme,
        LocalEditorPreferences provides preferences,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CodeinTypography,
            shapes = CodeinShapes,
            content = content,
        )
    }
}
