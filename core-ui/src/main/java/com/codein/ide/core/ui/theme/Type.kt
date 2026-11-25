package com.codein.ide.core.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.codein.ide.core.ui.R

private fun variableFont(@FontRes res: Int, weight: FontWeight): Font =
    Font(
        resId = res,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

private fun variableFamily(@FontRes res: Int): FontFamily = FontFamily(
    variableFont(res, FontWeight.Light),
    variableFont(res, FontWeight.Normal),
    variableFont(res, FontWeight.Medium),
    variableFont(res, FontWeight.SemiBold),
    variableFont(res, FontWeight.Bold),
)

/**
 * UI typeface: Host Grotesk (variable weight axis, 300 to 800). One family carries both display
 * and reading roles; hierarchy comes from size and a single step from Normal to Medium.
 */
val HostGrotesk: FontFamily = variableFamily(R.font.host_grotesk)

/** The four bundled coding fonts. Each maps to a variable TTF inside `res/font/`. */
enum class CodingFont(val id: String, val displayName: String, @FontRes val fontRes: Int, val supportsLigatures: Boolean) {
    JetBrainsMono("jetbrains_mono", "JetBrains Mono", R.font.jetbrains_mono, true),
    FiraCode("fira_code", "Fira Code", R.font.fira_code, true),
    CascadiaCode("cascadia_code", "Cascadia Code", R.font.cascadia_code, true),
    SourceCodePro("source_code_pro", "Source Code Pro", R.font.source_code_pro, false);

    val family: FontFamily by lazy { variableFamily(fontRes) }

    companion object {
        fun byId(id: String): CodingFont = entries.firstOrNull { it.id == id } ?: JetBrainsMono
    }
}

/** Tabular figures for line numbers, build timers and file sizes. */
const val TabularNumbers = "tnum"

private val tight = (-0.01).em

val CodeinTypography = Typography(
    displaySmall = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.015).em),
    headlineSmall = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = tight),
    titleLarge = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = tight),
    titleMedium = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = tight),
    titleSmall = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = tight),
    bodyLarge = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = tight),
    bodyMedium = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = tight),
    bodySmall = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = tight),
    labelLarge = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = tight, fontFeatureSettings = TabularNumbers),
    labelMedium = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.em, fontFeatureSettings = TabularNumbers),
    labelSmall = TextStyle(fontFamily = HostGrotesk, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.01.em, fontFeatureSettings = TabularNumbers),
)

/** Monospace text style for code shown outside the editor (diffs, logs, search previews). */
fun codeTextStyle(font: CodingFont, sizeSp: Float, ligatures: Boolean): TextStyle = TextStyle(
    fontFamily = font.family,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 1.45f).sp,
    fontFeatureSettings = if (ligatures && font.supportsLigatures) "liga 1, calt 1, tnum" else "liga 0, calt 0, tnum",
)

/**
 * Monospace style for live numbers outside the editor (Ln:Col, build timers, file sizes, Logcat
 * timestamps). Always tabular and ligature-free so values never jitter while they update.
 */
fun metricsTextStyle(font: CodingFont, sizeSp: Float = 11f): TextStyle = TextStyle(
    fontFamily = font.family,
    fontWeight = FontWeight.Medium,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 1.4f).sp,
    fontFeatureSettings = "tnum, liga 0, calt 0",
)
