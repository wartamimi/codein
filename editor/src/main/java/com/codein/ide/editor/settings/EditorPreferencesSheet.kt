package com.codein.ide.editor.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.prefs.AppThemeMode
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.theme.CodingFont
import com.codein.ide.core.ui.theme.EditorColorScheme
import com.codein.ide.core.ui.theme.EditorColorSchemes
import com.codein.ide.core.ui.theme.codeTextStyle
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

/** Live Font & Editor Preferences modal bottom sheet. Changes apply to the editor in real time. */
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Editor appearance", style = MaterialTheme.typography.titleLarge)

            PreviewCard(preferences)

            Section("Coding font") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CodingFont.entries.forEach { font ->
                        FontChoice(font, preferences.codingFont == font) { onChange(PreferenceChange.Font(font)) }
                    }
                }
            }

            Section("Font size · ${preferences.fontSizeSp.roundToInt()} sp") {
                Slider(
                    value = preferences.fontSizeSp,
                    onValueChange = { onChange(PreferenceChange.FontSize(it.roundToInt().toFloat())) },
                    valueRange = EditorPreferences.MIN_FONT_SIZE..EditorPreferences.MAX_FONT_SIZE,
                    steps = (EditorPreferences.MAX_FONT_SIZE - EditorPreferences.MIN_FONT_SIZE).toInt() - 1,
                )
            }

            Section("Line height · ${String.format(Locale.US, "%.2f", preferences.lineHeightMultiplier)}x") {
                Slider(
                    value = preferences.lineHeightMultiplier,
                    onValueChange = { onChange(PreferenceChange.LineHeight((it * 20).roundToInt() / 20f)) },
                    valueRange = EditorPreferences.MIN_LINE_HEIGHT..EditorPreferences.MAX_LINE_HEIGHT,
                    steps = 9,
                )
            }

            ToggleRow("Font ligatures (=>, !=, ===)", preferences.ligatures, enabled = preferences.codingFont.supportsLigatures) { onChange(PreferenceChange.Ligatures(it)) }
            ToggleRow("Soft word wrap", preferences.softWrap) { onChange(PreferenceChange.SoftWrap(it)) }
            ToggleRow("Indent guides", preferences.showIndentGuides) { onChange(PreferenceChange.IndentGuides(it)) }
            ToggleRow("Auto-save on edit", preferences.autoSave) { onChange(PreferenceChange.AutoSave(it)) }
            ToggleRow("Haptic extra keys", preferences.hapticExtraKeys) { onChange(PreferenceChange.Haptics(it)) }

            Section("Tab size") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorPreferences.TAB_SIZES.forEach { size ->
                        PillChip("$size spaces", selected = preferences.tabSize == size, onClick = { onChange(PreferenceChange.TabSize(size)) })
                    }
                }
            }

            Section("Editor theme") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorColorSchemes.all.forEach { scheme ->
                        SchemeRow(scheme, scheme.id == preferences.editorSchemeId) { onChange(PreferenceChange.Scheme(scheme.id)) }
                    }
                }
            }

            Section("App theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        PillChip(mode.name, selected = preferences.themeMode == mode, onClick = { onChange(PreferenceChange.ThemeMode(mode)) })
                    }
                }
            }
            ToggleRow("Material You dynamic accents", preferences.dynamicColor) { onChange(PreferenceChange.DynamicColor(it)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
        Switch(checked = checked && enabled, onCheckedChange = onToggle, enabled = enabled)
    }
}

@Composable
private fun FontChoice(font: CodingFont, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("=> != {}", style = codeTextStyle(font, 16f, ligatures = true))
        Spacer(Modifier.height(2.dp))
        Text(font.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SchemeRow(scheme: EditorColorScheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.background)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(scheme.displayName, color = scheme.foreground, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        listOf(scheme.keyword, scheme.string, scheme.function, scheme.type, scheme.number).forEach { c ->
            Box(Modifier.padding(start = 4.dp).size(12.dp).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun PreviewCard(prefs: EditorPreferences) {
    val s = prefs.editorScheme
    val style = codeTextStyle(prefs.codingFont, prefs.fontSizeSp, prefs.ligatures).copy(lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) })
    val tab = " ".repeat(prefs.tabSize)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(s.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                fun span(text: String, color: androidx.compose.ui.graphics.Color) =
                    withStyleCompat(this, color, text)
                span("fun ", s.keyword); span("greet", s.function); span("(name: ", s.foreground); span("String", s.type); span(") {\n", s.foreground)
                span("${tab}if ", s.keyword); span("(name != ", s.foreground); span("\"\"", s.string); span(") ", s.foreground); span("println", s.function); span("(", s.foreground); span("\"Hi \$name\"", s.string); span(")\n", s.foreground)
                span("$tab// => ligatures, 42 numbers\n", s.comment)
                span("}", s.foreground)
            },
            style = style,
        )
    }
}

private fun withStyleCompat(builder: androidx.compose.ui.text.AnnotatedString.Builder, color: androidx.compose.ui.graphics.Color, text: String) {
    val index = builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
    builder.append(text)
    builder.pop(index)
}
