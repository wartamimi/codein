package com.codein.ide.editor.keys

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.HairlineDivider
import com.codein.ide.core.ui.components.VerticalHairline
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
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
 * The 42dp Developer Extra Keys Bar. The caller places it inside an `imePadding()` container so it
 * rides directly on top of the soft keyboard. Every key is an evenly proportioned pill
 * (min 36dp x 32dp, 6dp corners) and fires a subtle `TextHandleMove` haptic.
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
    Column(modifier.fillMaxWidth().background(CodeinTheme.colors.tier1)) {
        HairlineDivider()
        Row(
            modifier = Modifier.fillMaxWidth().height(Sizes.ExtraKeysBar - 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // AI Fix stays pinned on the left; everything else scrolls.
            AiFixChip(highlighted = hasBuildError, onClick = { press(ExtraKey.AiFix) }, modifier = Modifier.padding(start = Spacing.sm, end = Spacing.xs))
            VerticalHairline(Modifier.height(Spacing.xl))
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconKey(CodeinIcons.Tab, "Tab") { press(ExtraKey.Tab) }
                symbolKeys.forEach { (label, key) -> TextKey(label) { press(key) } }
                VerticalHairline(Modifier.height(Spacing.lg + Spacing.xs))
                IconKey(CodeinIcons.Undo, "Undo", enabled = canUndo) { press(ExtraKey.Undo) }
                IconKey(CodeinIcons.Redo, "Redo", enabled = canRedo) { press(ExtraKey.Redo) }
                IconKey(CodeinIcons.Comment, "Toggle comment") { press(ExtraKey.ToggleComment) }
                IconKey(CodeinIcons.Search, "Find") { press(ExtraKey.Find) }
                Spacer(Modifier.width(Spacing.xs))
            }
        }
    }
}

private val KeyShape = RoundedCornerShape(Sizes.KeyPillCorner)

/** What TalkBack reads for a symbol key, since many screen readers skip punctuation. */
private fun spokenSymbol(label: String): String = when (label) {
    "{" -> "open brace"
    "}" -> "close brace"
    "(" -> "open parenthesis"
    ")" -> "close parenthesis"
    "[" -> "open bracket"
    "]" -> "close bracket"
    "<" -> "less than"
    ">" -> "greater than"
    ";" -> "semicolon"
    ":" -> "colon"
    "." -> "dot"
    "," -> "comma"
    "=" -> "equals"
    "\"" -> "double quote"
    "'" -> "single quote"
    "/" -> "slash"
    "\\" -> "backslash"
    "|" -> "pipe"
    "&" -> "ampersand"
    "!" -> "exclamation mark"
    "?" -> "question mark"
    "-" -> "minus"
    "+" -> "plus"
    "*" -> "asterisk"
    "_" -> "underscore"
    "$" -> "dollar"
    "#" -> "hash"
    "@" -> "at"
    "%" -> "percent"
    else -> label
}

@Composable
private fun TextKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .widthIn(min = Sizes.KeyPillMinWidth)
            .height(Sizes.KeyPillHeight)
            .clip(KeyShape)
            .background(CodeinTheme.colors.tier2)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, KeyShape)
            .clickable(onClickLabel = "Type ${spokenSymbol(label)}", onClick = onClick)
            .semantics { contentDescription = spokenSymbol(label) }
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 14f, ligatures = true), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IconKey(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .widthIn(min = Sizes.KeyPillMinWidth + Spacing.xs)
            .height(Sizes.KeyPillHeight)
            .clip(KeyShape)
            .background(CodeinTheme.colors.tier2)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, KeyShape)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(Sizes.Icon))
    }
}

@Composable
private fun AiFixChip(highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (highlighted) accent else accent.copy(alpha = 0.14f), Motion.switch(), label = "ai-fix-bg")
    val fg by animateColorAsState(if (highlighted) MaterialTheme.colorScheme.onPrimary else accent, Motion.switch(), label = "ai-fix-fg")
    Row(
        modifier = modifier
            .height(Sizes.KeyPillHeight)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, accent.copy(alpha = if (highlighted) 0f else 0.45f), CircleShape)
            .clickable(onClickLabel = "Fix with AI", onClick = onClick)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(CodeinIcons.Sparkle, null, tint = fg, modifier = Modifier.size(Sizes.IconSm))
        Text(tr("AI Fix"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
