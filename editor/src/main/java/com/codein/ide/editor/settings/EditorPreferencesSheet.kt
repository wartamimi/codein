package com.codein.ide.editor.settings

import com.codein.ide.core.ui.theme.CodeinShape
import com.codein.ide.core.ui.i18n.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codein.ide.core.ui.components.DragHandle
import com.codein.ide.core.ui.components.HairlineDivider
import com.codein.ide.core.ui.components.SegmentedTabs
import com.codein.ide.core.ui.components.StudioSectionCard
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.prefs.AppThemeMode
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.CodingFont
import com.codein.ide.core.ui.theme.EditorColorScheme
import com.codein.ide.core.ui.theme.EditorColorSchemes
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.core.ui.theme.codeTextStyle
import com.codein.ide.core.ui.theme.metricsTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Every change the preferences sheet can make; the host persists them via DataStore. */
sealed interface PreferenceChange {
    data class Font(val font: CodingFont) : PreferenceChange
    data class FontSize(val sp: Float) : PreferenceChange
    data class LineHeight(val multiplier: Float) : PreferenceChange
    data class Ligatures(val enabled: Boolean) : PreferenceChange
    data class TabSize(val size: Int) : PreferenceChange
    data class SoftWrap(val enabled: Boolean) : PreferenceChange
    data class Scheme(val id: String) : PreferenceChange
    data class DynamicColor(val enabled: Boolean) : PreferenceChange
    data class ThemeMode(val mode: AppThemeMode) : PreferenceChange
    data class AutoSave(val enabled: Boolean) : PreferenceChange
    data class IndentGuides(val enabled: Boolean) : PreferenceChange
    data class Haptics(val enabled: Boolean) : PreferenceChange
}

/**
 * Live Font, Theme & Editor Preferences sheet. A code preview is pinned at the top and redraws
 * instantly as the font, size, line height, ligatures, tab width, wrap or theme change; the controls
 * scroll underneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPreferencesSheet(
    preferences: EditorPreferences,
    onChange: (PreferenceChange) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CodeinTheme.colors.tier1,
        dragHandle = { DragHandle(Modifier.padding(vertical = Spacing.md)) },
        shape = CodeinShape.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Editor appearance"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${preferences.codingFont.displayName} · ${preferences.fontSizeSp.roundToInt()}sp",
                    style = metricsTextStyle(preferences.codingFont, 11f),
                    color = CodeinTheme.colors.textMuted,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            // Pinned live preview: never scrolls away while tuning.
            PreviewCard(preferences, Modifier.padding(horizontal = Spacing.lg))
            Spacer(Modifier.height(Spacing.md))
            HairlineDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                StudioSectionCard("Coding font", icon = CodeinIcons.Type) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CodingFont.entries.forEach { font ->
                            FontChoice(font, preferences.codingFont == font) { onChange(PreferenceChange.Font(font)) }
                        }
                    }
                    SliderRow(
                        label = "Font size",
                        value = "${preferences.fontSizeSp.roundToInt()} sp",
                        font = preferences.codingFont,
                    ) {
                        Slider(
                            value = preferences.fontSizeSp,
                            onValueChange = { onChange(PreferenceChange.FontSize(it.roundToInt().toFloat())) },
                            valueRange = EditorPreferences.MIN_FONT_SIZE..EditorPreferences.MAX_FONT_SIZE,
                            steps = (EditorPreferences.MAX_FONT_SIZE - EditorPreferences.MIN_FONT_SIZE).toInt() - 1,
                        )
                    }
                    SliderRow(
                        label = "Line height",
                        value = String.format(Locale.US, "%.2fx", preferences.lineHeightMultiplier),
                        font = preferences.codingFont,
                    ) {
                        Slider(
                            value = preferences.lineHeightMultiplier,
                            onValueChange = { onChange(PreferenceChange.LineHeight((it * 20).roundToInt() / 20f)) },
                            valueRange = EditorPreferences.MIN_LINE_HEIGHT..EditorPreferences.MAX_LINE_HEIGHT,
                            steps = ((EditorPreferences.MAX_LINE_HEIGHT - EditorPreferences.MIN_LINE_HEIGHT) * 20).roundToInt() - 1,
                        )
                    }
                    ToggleRow("Font ligatures", "=>  !=  ===  ->", preferences.ligatures, enabled = preferences.codingFont.supportsLigatures) { onChange(PreferenceChange.Ligatures(it)) }
                }

                StudioSectionCard("Layout", icon = CodeinIcons.Wrap) {
                    Text(tr("Tab width"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SegmentedTabs(
                        items = EditorPreferences.TAB_SIZES,
                        selected = preferences.tabSize,
                        onSelect = { onChange(PreferenceChange.TabSize(it)) },
                        label = { "$it spaces" },
                    )
                    ToggleRow("Word wrap", "Wrap long lines to the viewport", preferences.softWrap) { onChange(PreferenceChange.SoftWrap(it)) }
                    ToggleRow("Indent guides", null, preferences.showIndentGuides) { onChange(PreferenceChange.IndentGuides(it)) }
                }

                StudioSectionCard("Color theme", icon = CodeinIcons.Palette, subtitle = tr("Recolors the editor and the whole IDE")) {
                    EditorColorSchemes.all.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            pair.forEach { scheme ->
                                SchemeCard(scheme, scheme.id == preferences.editorSchemeId, Modifier.weight(1f)) { onChange(PreferenceChange.Scheme(scheme.id)) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(tr("App theme mode"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SegmentedTabs(
                        items = AppThemeMode.entries.toList(),
                        selected = preferences.themeMode,
                        onSelect = { onChange(PreferenceChange.ThemeMode(it)) },
                        label = { it.name },
                    )
                    ToggleRow("Material You accents", "Tint the accent from your wallpaper (Android 12+)", preferences.dynamicColor) { onChange(PreferenceChange.DynamicColor(it)) }
                }

                StudioSectionCard("Behavior", icon = CodeinIcons.Settings) {
                    ToggleRow("Auto-save on edit", null, preferences.autoSave) { onChange(PreferenceChange.AutoSave(it)) }
                    ToggleRow("Haptic extra keys", null, preferences.hapticExtraKeys) { onChange(PreferenceChange.Haptics(it)) }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: String, font: CodingFont, slider: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = metricsTextStyle(font, 12f), color = MaterialTheme.colorScheme.primary)
        }
        slider()
    }
}

@Composable
private fun ToggleRow(label: String, supporting: String?, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.MinTouch).clickable(enabled = enabled) { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(checked = checked && enabled, onCheckedChange = onToggle, enabled = enabled)
    }
}

@Composable
private fun FontChoice(font: CodingFont, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, Motion.switch(), label = "font-border")
    val shape = CodeinShape.Surface
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) CodeinTheme.colors.tier3 else CodeinTheme.colors.base)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text("=> != {}", style = codeTextStyle(font, 16f, ligatures = true))
        Spacer(Modifier.height(Spacing.xs))
        Text(font.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SchemeCard(scheme: EditorColorScheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, Motion.switch(), label = "scheme-border")
    val shape = CodeinShape.Surface
    val chrome = scheme.chrome
    Column(
        modifier
            .clip(shape)
            .background(chrome.tier1)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(onClick = onClick),
    ) {
        // Miniature IDE: tier strip on top, code canvas below.
        Row(Modifier.fillMaxWidth().height(Spacing.md).background(chrome.tier1).padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Spacing.xs + 2.dp).clip(CircleShape).background(chrome.accent))
            Spacer(Modifier.width(Spacing.xs))
            Box(Modifier.width(Spacing.xl).height(Spacing.xs).clip(CircleShape).background(chrome.tier3))
        }
        Column(Modifier.fillMaxWidth().background(scheme.background).padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Bar(scheme.keyword, 14); Bar(scheme.function, 22); Bar(scheme.punctuation, 6)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Spacer(Modifier.width(Spacing.sm)); Bar(scheme.type, 16); Bar(scheme.string, 26)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Spacer(Modifier.width(Spacing.sm)); Bar(scheme.comment, 30)
            }
        }
        Row(Modifier.fillMaxWidth().background(chrome.tier2).padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(scheme.displayName, style = MaterialTheme.typography.labelMedium, color = chrome.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) androidx.compose.material3.Icon(CodeinIcons.Check, null, tint = chrome.accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Bar(color: Color, widthDp: Int) {
    Box(Modifier.width(widthDp.dp).height(Spacing.xs + 1.dp).clip(CircleShape).background(color))
}

@Composable
private fun PreviewCard(prefs: EditorPreferences, modifier: Modifier = Modifier) {
    val s = CodeinTheme.editorScheme
    val style = codeTextStyle(prefs.codingFont, prefs.fontSizeSp, prefs.ligatures).copy(lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).sp)
    val gutterStyle = metricsTextStyle(prefs.codingFont, prefs.fontSizeSp).copy(lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).sp)
    val tab = " ".repeat(prefs.tabSize)
    val code: AnnotatedString = buildAnnotatedString {
        fun span(text: String, color: Color) {
            val i = pushStyle(SpanStyle(color = color))
            append(text)
            pop(i)
        }
        span("fun ", s.keyword); span("greet", s.function); span("(name: ", s.foreground); span("String", s.type); span(") {\n", s.foreground)
        span("${tab}if ", s.keyword); span("(name != ", s.foreground); span("\"\"", s.string); span(") ", s.foreground); span("println", s.function); span("(", s.foreground); span("\"Hi \$name\"", s.string); span(")\n", s.foreground)
        span("$tab// => ligatures, 0123456789\n", s.comment)
        span("}", s.foreground)
    }
    val shape = CodeinShape.Surface
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(s.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .animateContentSize(Motion.spatial()),
    ) {
        Column(Modifier.background(s.gutterBackground).padding(horizontal = Spacing.sm, vertical = Spacing.md), horizontalAlignment = Alignment.End) {
            (1..4).forEach { n -> Text("$n", style = gutterStyle, color = if (n == 2) s.lineNumberActive else s.lineNumber) }
        }
        Text(
            code,
            style = style,
            softWrap = prefs.softWrap,
            maxLines = 8,
            modifier = Modifier
                .weight(1f)
                .then(if (prefs.softWrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
    }
}
