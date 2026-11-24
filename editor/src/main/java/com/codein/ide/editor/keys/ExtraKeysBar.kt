package com.codein.ide.editor.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.VerticalHairline
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.codeTextStyle

/** Actions emitted by the Developer Extra Keys Bar. */
sealed interface ExtraKey {
    data object Tab : ExtraKey
    data class Symbol(val text: String, val caretBack: Int = 0) : ExtraKey
    data object Undo : ExtraKey
    data object Redo : ExtraKey
    data object ToggleComment : ExtraKey
    data object AiFix : ExtraKey
    data object Find : ExtraKey
}

private val symbolKeys = listOf(
    "{" to ExtraKey.Symbol("{}", 1),
    "}" to ExtraKey.Symbol("}"),
    "(" to ExtraKey.Symbol("()", 1),
    ")" to ExtraKey.Symbol(")"),
    "[" to ExtraKey.Symbol("[]", 1),
    "]" to ExtraKey.Symbol("]"),
    ";" to ExtraKey.Symbol(";"),
    "=" to ExtraKey.Symbol("="),
    "<" to ExtraKey.Symbol("<"),
    ">" to ExtraKey.Symbol(">"),
    "\"" to ExtraKey.Symbol("\"\"", 1),
    "/" to ExtraKey.Symbol("/"),
    "->" to ExtraKey.Symbol("->"),
    ":" to ExtraKey.Symbol(":"),
    "." to ExtraKey.Symbol("."),
    "," to ExtraKey.Symbol(","),
    "!" to ExtraKey.Symbol("!"),
    "?" to ExtraKey.Symbol("?"),
    "&" to ExtraKey.Symbol("&"),
    "|" to ExtraKey.Symbol("|"),
    "$" to ExtraKey.Symbol("$"),
    "@" to ExtraKey.Symbol("@"),
)

/**
 * Horizontal accessory bar anchored above the soft keyboard. The caller places it inside an
 * `imePadding()` container so it rides on top of the IME.
 */
@Composable
fun ExtraKeysBar(
    onKey: (ExtraKey) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    hasBuildError: Boolean,
    haptics: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    fun press(key: ExtraKey) {
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKey(key)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .heightIn(min = 44.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // AI Fix stays pinned on the left; everything else scrolls.
        AiFixChip(highlighted = hasBuildError, onClick = { press(ExtraKey.AiFix) }, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
        VerticalHairline(Modifier.height(24.dp))
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconKey(CodeinIcons.Tab, "Tab") { press(ExtraKey.Tab) }
            symbolKeys.forEach { (label, key) -> TextKey(label) { press(key) } }
            VerticalHairline(Modifier.height(20.dp))
            IconKey(CodeinIcons.Undo, "Undo", enabled = canUndo) { press(ExtraKey.Undo) }
            IconKey(CodeinIcons.Redo, "Redo", enabled = canRedo) { press(ExtraKey.Redo) }
            IconKey(CodeinIcons.Comment, "Toggle comment") { press(ExtraKey.ToggleComment) }
            IconKey(CodeinIcons.Search, "Find") { press(ExtraKey.Find) }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun TextKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .widthIn(min = 36.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 15f, ligatures = true), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IconKey(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 40.dp, height = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AiFixChip(highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .then(
                if (highlighted) Modifier.background(brush)
                else Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fg = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
        Icon(CodeinIcons.Sparkle, null, tint = fg, modifier = Modifier.size(16.dp))
        Text("AI Fix", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
