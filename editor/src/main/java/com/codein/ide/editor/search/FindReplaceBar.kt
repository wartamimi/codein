package com.codein.ide.editor.search

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.core.ui.theme.codeTextStyle
import com.codein.ide.core.ui.theme.metricsTextStyle

/** State for the floating in-editor find & replace bar. */
data class FindBarState(
    val visible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val options: FindOptions = FindOptions(),
    val showReplace: Boolean = false,
    val matches: List<FindMatch> = emptyList(),
    val currentIndex: Int = -1,
) {
    val validation: QueryValidation get() = FindReplaceEngine.validate(query, options)
    val counterText: String
        get() = when {
            query.isEmpty() -> ""
            validation is QueryValidation.Invalid -> "Invalid"
            matches.isEmpty() -> "No results"
            else -> "${currentIndex + 1}/${matches.size}"
        }
}

sealed interface FindBarAction {
    data class Query(val text: String) : FindBarAction
    data class Replacement(val text: String) : FindBarAction
    data object ToggleRegex : FindBarAction
    data object ToggleCase : FindBarAction
    data object ToggleWholeWord : FindBarAction
    data object ToggleReplace : FindBarAction
    data object Next : FindBarAction
    data object Previous : FindBarAction
    data object ReplaceOne : FindBarAction
    data object ReplaceAll : FindBarAction
    data object Close : FindBarAction
}

/**
 * Floating glass find & replace bar anchored at the editor's top-right (8dp inset): Tier 2 at 95%
 * alpha with a hairline border, a live-validated regex field, tactile `.*` / `Aa` / `\b` toggle
 * pills, a tabular match counter, and a Replace row that expands with `AnimatedVisibility`.
 */
@Composable
fun FindReplaceBar(state: FindBarState, onAction: (FindBarAction) -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier,
        enter = fadeIn(Motion.switch()) + scaleIn(Motion.spatial(), initialScale = 0.96f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
        exit = fadeOut(Motion.switch()) + scaleOut(Motion.switch(), targetScale = 0.96f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val shape = RoundedCornerShape(12.dp)
        val invalid = state.validation as? QueryValidation.Invalid
        val noResults = state.query.isNotEmpty() && invalid == null && state.matches.isEmpty()
        val chevron by animateFloatAsState(if (state.showReplace) 90f else 0f, Motion.spatial(), label = "replace-chevron")
        Column(
            modifier = Modifier
                .padding(Spacing.sm)
                .widthIn(max = 400.dp)
                .shadow(4.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(shape)
                .background(CodeinTheme.colors.glass)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .animateContentSize(Motion.spatial())
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolbarIconButton(
                    CodeinIcons.ChevronRight,
                    if (state.showReplace) "Hide replace" else "Show replace",
                    size = 28.dp,
                    iconSize = Sizes.IconSm,
                    modifier = Modifier.rotate(chevron),
                ) { onAction(FindBarAction.ToggleReplace) }
                FindField(
                    value = state.query,
                    placeholder = tr("Find"),
                    error = invalid != null,
                    counter = state.counterText,
                    counterIsError = invalid != null || noResults,
                    onChange = { onAction(FindBarAction.Query(it)) },
                    onSubmit = { onAction(FindBarAction.Next) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                ToolbarIconButton(CodeinIcons.ArrowUp, tr("Previous match"), size = 28.dp, enabled = state.matches.isNotEmpty()) { onAction(FindBarAction.Previous) }
                ToolbarIconButton(CodeinIcons.ArrowDown, tr("Next match"), size = 28.dp, enabled = state.matches.isNotEmpty()) { onAction(FindBarAction.Next) }
                ToolbarIconButton(CodeinIcons.Close, tr("Close find"), size = 28.dp) { onAction(FindBarAction.Close) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(start = Sizes.MinTouch, bottom = Spacing.xs),
            ) {
                TogglePill(".*", "Regular expression", state.options.regex, error = invalid != null) { onAction(FindBarAction.ToggleRegex) }
                TogglePill("Aa", "Match case", state.options.matchCase) { onAction(FindBarAction.ToggleCase) }
                TogglePill("\\b", "Whole word", state.options.wholeWord) { onAction(FindBarAction.ToggleWholeWord) }
                if (invalid != null) {
                    Text(
                        "${invalid.message} (at ${invalid.index.coerceAtLeast(0)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = Spacing.xs),
                    )
                }
            }
            AnimatedVisibility(
                visible = state.showReplace,
                enter = expandVertically(Motion.spatial()) + fadeIn(Motion.switch()),
                exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.switch()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Sizes.MinTouch, bottom = Spacing.xs)) {
                    FindField(
                        value = state.replacement,
                        placeholder = if (state.options.regex) "Replace (\$1, \$2 groups)" else "Replace",
                        error = false,
                        counter = "",
                        counterIsError = false,
                        onChange = { onAction(FindBarAction.Replacement(it)) },
                        onSubmit = { onAction(FindBarAction.ReplaceOne) },
                        modifier = Modifier.weight(1f),
                    )
                    ToolbarIconButton(CodeinIcons.Replace, tr("Replace"), size = 28.dp, enabled = state.matches.isNotEmpty()) { onAction(FindBarAction.ReplaceOne) }
                    TogglePill("All", "Replace all", active = state.matches.isNotEmpty(), enabled = state.matches.isNotEmpty()) { onAction(FindBarAction.ReplaceAll) }
                    Spacer(Modifier.width(Spacing.xs))
                }
            }
        }
    }
}

/** A tactile 28dp toggle pill (min 36dp wide, 6dp corners) that lights up with the accent when on. */
@Composable
private fun TogglePill(
    label: String,
    description: String,
    active: Boolean,
    error: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val accent = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (active) accent.copy(alpha = 0.18f) else CodeinTheme.colors.tier3, Motion.switch(), label = "toggle-bg")
    val border by animateColorAsState(if (active) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, Motion.switch(), label = "toggle-border")
    val shape = RoundedCornerShape(Sizes.KeyPillCorner)
    Box(
        Modifier
            .widthIn(min = Sizes.KeyPillMinWidth)
            .height(28.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClickLabel = description) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                active -> accent
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun FindField(
    value: String,
    placeholder: String,
    error: Boolean,
    counter: String,
    counterIsError: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = CodeinTheme.editorPreferences
    val border by animateColorAsState(
        if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        Motion.switch(),
        label = "find-border",
    )
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .height(Sizes.Pill)
            .clip(shape)
            .background(CodeinTheme.colors.base.copy(alpha = 0.6f))
            .border(1.dp, border, shape)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = codeTextStyle(prefs.codingFont, 13f, false).copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
        }
        if (counter.isNotEmpty()) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                counter,
                style = metricsTextStyle(prefs.codingFont, 11f),
                color = if (counterIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
