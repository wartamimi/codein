package com.codein.ide.editor.search

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.codeTextStyle

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
            else -> "${currentIndex + 1} / ${matches.size}"
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

/** Glassmorphic floating find/replace overlay anchored at the editor's top-right. */
@Composable
fun FindReplaceBar(state: FindBarState, onAction: (FindBarAction) -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = state.visible, modifier = modifier) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val shape = RoundedCornerShape(14.dp)
        val invalid = state.validation as? QueryValidation.Invalid
        Column(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 420.dp)
                .shadow(16.dp, shape, clip = false)
                .clip(shape)
                .background(CodeinTheme.colors.glass)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolbarIconButton(
                    if (state.showReplace) CodeinIcons.ChevronDown else CodeinIcons.ChevronRight,
                    "Toggle replace",
                    size = 30.dp,
                ) { onAction(FindBarAction.ToggleReplace) }
                SearchField(
                    value = state.query,
                    placeholder = "Find",
                    error = invalid != null,
                    onChange = { onAction(FindBarAction.Query(it)) },
                    onSubmit = { onAction(FindBarAction.Next) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                ToolbarIconButton(CodeinIcons.CaseSensitive, "Match case (Aa)", active = state.options.matchCase, size = 30.dp) { onAction(FindBarAction.ToggleCase) }
                ToolbarIconButton(CodeinIcons.WholeWord, "Whole word (\\b)", active = state.options.wholeWord, size = 30.dp) { onAction(FindBarAction.ToggleWholeWord) }
                ToolbarIconButton(CodeinIcons.Regex, "Regex (.*)", active = state.options.regex, size = 30.dp) { onAction(FindBarAction.ToggleRegex) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 30.dp)) {
                Text(
                    state.counterText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (invalid != null || (state.query.isNotEmpty() && state.matches.isEmpty())) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                ToolbarIconButton(CodeinIcons.ArrowUp, "Previous match", size = 30.dp, enabled = state.matches.isNotEmpty()) { onAction(FindBarAction.Previous) }
                ToolbarIconButton(CodeinIcons.ArrowDown, "Next match", size = 30.dp, enabled = state.matches.isNotEmpty()) { onAction(FindBarAction.Next) }
                ToolbarIconButton(CodeinIcons.Close, "Close find", size = 30.dp) { onAction(FindBarAction.Close) }
            }
            if (invalid != null) {
                Text(
                    "Regex error at ${invalid.index.coerceAtLeast(0)}: ${invalid.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 38.dp, end = 8.dp),
                )
            }
            AnimatedVisibility(state.showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 30.dp)) {
                    SearchField(
                        value = state.replacement,
                        placeholder = if (state.options.regex) "Replace (\$1, \$2 groups)" else "Replace",
                        error = false,
                        onChange = { onAction(FindBarAction.Replacement(it)) },
                        onSubmit = { onAction(FindBarAction.ReplaceOne) },
                        modifier = Modifier.weight(1f),
                    )
                    ToolbarIconButton(CodeinIcons.Replace, "Replace", size = 30.dp, enabled = state.matches.isNotEmpty()) { onAction(FindBarAction.ReplaceOne) }
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (state.matches.isNotEmpty()) 0.18f else 0.06f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "All",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.then(
                                if (state.matches.isNotEmpty()) Modifier.clickable { onAction(FindBarAction.ReplaceAll) } else Modifier,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    placeholder: String,
    error: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = CodeinTheme.editorPreferences
    val border = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
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
    Spacer(Modifier.size(4.dp))
}
