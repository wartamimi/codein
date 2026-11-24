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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.prefs.EditorPreferences

/** Hairlines are the hairline source color at 30% alpha: the only depth cue besides surface tiers. */
const val HairlineAlpha = 0.30f

/** Extra semantic colors that Material 3 has no slot for. */
@Immutable
data class CodeinExtendedColors(
    /** Tier 0: editor canvas / base. */
    val base: Color,
    /** Tier 1: sidebars, top bar, bottom dock. */
    val tier1: Color,
    /** Tier 2: cards, floating search, modals, active pills. */
    val tier2: Color,
    /** Tier 3: hover, pressed and selected highlight. */
    val tier3: Color,
    val hairline: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val textMuted: Color,
    /** Tier 2 at 95% alpha for floating, glass-like overlays (find bar, palette). */
    val glass: Color,
    val isDark: Boolean,
) {
    /** Legacy names for tier 1 / tier 2, kept for existing call sites. */
    val panel: Color get() = tier1
    val card: Color get() = tier2
}

val LocalCodeinColors = staticCompositionLocalOf { extendedColorsFor(EditorColorSchemes.Default) }
val LocalEditorScheme = staticCompositionLocalOf { EditorColorSchemes.Default }
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
    /** The resolved theme (honours the app theme mode), shared by the editor and the chrome. */
    val editorScheme: EditorColorScheme
        @Composable get() = LocalEditorScheme.current
    val editorPreferences: EditorPreferences
        @Composable get() = LocalEditorPreferences.current
}

/** WCAG contrast ratio between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * Nudges [fg] toward [strong] until it reaches [minRatio] contrast against [bg], so secondary and
 * muted text stays readable on every surface tier of every theme (Tokyo Night's and Dracula's
 * comment greys are far below 4.5:1 on their card surfaces as shipped).
 */
internal fun legible(fg: Color, bg: Color, strong: Color, minRatio: Float = 4.5f): Color {
    if (contrastRatio(fg, bg) >= minRatio) return fg
    var t = 0f
    var out = fg
    while (t < 1f && contrastRatio(out, bg) < minRatio) {
        t += 0.05f
        out = lerp(fg, strong, t.coerceAtMost(1f))
    }
    return out
}

/** Theme chrome with secondary/muted text lifted to readable contrast against the brightest tier. */
private fun readableChrome(scheme: EditorColorScheme): ChromePalette {
    val c = scheme.chrome
    val worst = c.tier3
    return c.copy(
        textSecondary = legible(c.textSecondary, worst, c.textPrimary, 4.5f),
        textMuted = legible(c.textMuted, worst, c.textPrimary, 4.5f),
    )
}

private fun extendedColorsFor(scheme: EditorColorScheme): CodeinExtendedColors {
    val c = readableChrome(scheme)
    return CodeinExtendedColors(
        base = c.base,
        tier1 = c.tier1,
        tier2 = c.tier2,
        tier3 = c.tier3,
        hairline = c.hairlineSource.copy(alpha = HairlineAlpha),
        success = c.success,
        warning = c.warning,
        info = c.info,
        textMuted = c.textMuted,
        glass = c.tier2.copy(alpha = 0.95f),
        isDark = scheme.isDark,
    )
}

/** Maps a theme's [ChromePalette] onto the Material 3 color roles used across the app. */
private fun materialColorsFor(scheme: EditorColorScheme): ColorScheme {
    val c = readableChrome(scheme)
    val hairline = c.hairlineSource.copy(alpha = HairlineAlpha)
    val outline = c.hairlineSource.copy(alpha = 0.55f)
    val primaryContainer = lerp(c.tier2, c.accent, 0.28f)
    val secondaryContainer = lerp(c.tier2, c.secondary, 0.24f)
    val errorContainer = lerp(c.tier2, c.error, 0.22f)
    return if (scheme.isDark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = c.textPrimary,
            secondary = c.secondary,
            onSecondary = c.onAccent,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = c.textPrimary,
            tertiary = c.info,
            onTertiary = c.onAccent,
            background = c.base,
            onBackground = c.textPrimary,
            surface = c.base,
            onSurface = c.textPrimary,
            surfaceVariant = c.tier2,
            onSurfaceVariant = c.textSecondary,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = c.base,
            surfaceContainerLow = c.tier1,
            surfaceContainer = c.tier1,
            surfaceContainerHigh = c.tier2,
            surfaceContainerHighest = c.tier3,
            surfaceBright = c.tier3,
            surfaceDim = c.base,
            outline = outline,
            outlineVariant = hairline,
            error = c.error,
            onError = c.onAccent,
            errorContainer = errorContainer,
            onErrorContainer = c.textPrimary,
            inverseSurface = c.textPrimary,
            inverseOnSurface = c.base,
            inversePrimary = c.accent,
            scrim = Color(0xCC000000),
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = lerp(Color.White, c.accent, 0.14f),
            onPrimaryContainer = c.textPrimary,
            secondary = c.secondary,
            onSecondary = c.onAccent,
            secondaryContainer = lerp(Color.White, c.secondary, 0.14f),
            onSecondaryContainer = c.textPrimary,
            tertiary = c.info,
            onTertiary = c.onAccent,
            background = c.base,
            onBackground = c.textPrimary,
            surface = c.base,
            onSurface = c.textPrimary,
            surfaceVariant = c.tier2,
            onSurfaceVariant = c.textSecondary,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = c.base,
            surfaceContainerLow = c.tier1,
            surfaceContainer = c.tier1,
            surfaceContainerHigh = c.tier2,
            surfaceContainerHighest = c.tier3,
            surfaceBright = c.base,
            surfaceDim = c.tier3,
            outline = outline,
            outlineVariant = hairline,
            error = c.error,
            onError = Color.White,
            errorContainer = lerp(Color.White, c.error, 0.14f),
            onErrorContainer = c.textPrimary,
            inverseSurface = c.textPrimary,
            inverseOnSurface = c.base,
            inversePrimary = c.accent,
            scrim = Color(0x99000000),
        )
    }
}

/**
 * Codein's root theme. The selected [EditorColorScheme] drives both the code editor and the app
 * chrome (surface tiers, accent, hairlines), so switching between Codein Obsidian, Catppuccin
 * Mocha, One Dark Pro, Tokyo Night and Clean Daylight recolors the whole IDE at once.
 */
@Composable
fun CodeinTheme(
    preferences: EditorPreferences = EditorPreferences(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val scheme = preferences.resolveScheme(systemDark)
    val context = LocalContext.current
    val base = remember(scheme) { materialColorsFor(scheme) }
    val colorScheme: ColorScheme = if (preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Material You only tints the accents; Codein keeps its own calibrated surface tiers.
        val dynamic = if (scheme.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(
            primary = dynamic.primary,
            onPrimary = dynamic.onPrimary,
            primaryContainer = dynamic.primaryContainer,
            onPrimaryContainer = dynamic.onPrimaryContainer,
            secondary = dynamic.secondary,
            onSecondary = dynamic.onSecondary,
            tertiary = dynamic.tertiary,
        )
    } else {
        base
    }
    val extended = remember(scheme) { extendedColorsFor(scheme) }
    CompositionLocalProvider(
        LocalCodeinColors provides extended,
        LocalEditorScheme provides scheme,
        LocalEditorPreferences provides preferences,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CodeinTypography,
            shapes = CodeinShapes,
        ) {
            // No root Surface wraps the app, so seed the content color here. Without it every Text
            // and Icon that doesn't set a color falls back to black, which vanishes on dark themes.
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onBackground,
                content = content,
            )
        }
    }
}
