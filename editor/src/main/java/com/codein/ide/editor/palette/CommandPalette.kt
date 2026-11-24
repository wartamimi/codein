package com.codein.ide.editor.palette

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.HairlineDivider
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge

enum class PaletteCategory(val label: String) { File("File"), Symbol("Symbol"), Action("Action"), Gradle("Gradle"), Appearance("Appearance"), Build("Build") }

/** An entry in the Quick Open / Command Palette. */
data class PaletteItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val category: PaletteCategory,
    val icon: ImageVector? = null,
    val fileName: String? = null,
    val shortcut: String? = null,
    val action: () -> Unit,
)

/** Fuzzy subsequence matching with scoring for consecutive and word-boundary hits. */
object FuzzyMatcher {
    data class Match(val score: Int, val positions: List<Int>)

    fun match(query: String, candidate: String): Match? {
        if (query.isEmpty()) return Match(0, emptyList())
        val q = query.lowercase()
        val c = candidate.lowercase()
        val positions = ArrayList<Int>(q.length)
        var score = 0
        var ci = 0
        var lastMatch = -2
        for (ch in q) {
            if (ch == ' ') continue
            var found = -1
            while (ci < c.length) {
                if (c[ci] == ch) { found = ci; break }
                ci++
            }
            if (found < 0) return null
            positions += found
            score += 10
            if (found == lastMatch + 1) score += 15
            if (found == 0 || candidate[found - 1] in "/._- " || (candidate[found].isUpperCase() && found > 0 && candidate[found - 1].isLowerCase())) score += 20
            lastMatch = found
            ci = found + 1
        }
        score -= (candidate.length - q.length).coerceAtLeast(0) / 4
        if (c.startsWith(q)) score += 30
        return Match(score, positions)
    }
}

/** Fast fuzzy-search modal (Ctrl+P) for files, actions, Gradle tasks, fonts and themes. */
@Composable
fun CommandPalette(
    items: List<PaletteItem>,
    onDismiss: () -> Unit,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    var selected by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val filtered = remember(query, items) {
        val (prefixCategory, text) = when {
            query.startsWith(">") -> PaletteCategory.Action to query.drop(1).trim()
            query.startsWith("@") -> PaletteCategory.Gradle to query.drop(1).trim()
            query.startsWith("#") -> PaletteCategory.Symbol to query.drop(1).trim()
            else -> null to query.trim()
        }
        items.asSequence()
            .filter { item ->
                when (prefixCategory) {
                    // Symbols only appear when asked for with '#', so they never crowd file results.
                    null -> item.category != PaletteCategory.Symbol
                    PaletteCategory.Action -> item.category != PaletteCategory.File && item.category != PaletteCategory.Symbol
                    else -> item.category == prefixCategory
                }
            }
            .mapNotNull { item ->
                val m = FuzzyMatcher.match(text, item.title) ?: FuzzyMatcher.match(text, item.subtitle)?.let { it.copy(score = it.score - 25, positions = emptyList()) }
                m?.let { item to it }
            }
            .sortedByDescending { it.second.score }
            .take(80)
            .toList()
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(filtered.size) { selected = selected.coerceIn(0, (filtered.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(selected) { if (filtered.isNotEmpty()) listState.animateScrollToItem(selected) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.xl + Sizes.TopBar).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .widthIn(max = Sizes.PaletteMaxWidth)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CodeinTheme.colors.glass)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .animateContentSize(Motion.spatial()),
            ) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CodeinIcons.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.Icon))
                    Spacer(Modifier.width(Spacing.md))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Files, # symbols, > commands, @ build tasks",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CodeinTheme.colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it; selected = 0 },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                filtered.getOrNull(selected)?.first?.let { onDismiss(); it.action() }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionDown -> { selected = (selected + 1).coerceAtMost(filtered.lastIndex); true }
                                        Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                                        Key.Escape -> { onDismiss(); true }
                                        else -> false
                                    }
                                },
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    CountBadge("Ctrl+P", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HairlineDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ScopeHint("Files", "", query) { query = ""; selected = 0 }
                    ScopeHint("Symbols", "#", query) { query = "#"; selected = 0 }
                    ScopeHint("Commands", ">", query) { query = ">"; selected = 0 }
                    ScopeHint("Build", "@", query) { query = "@"; selected = 0 }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), state = listState, contentPadding = PaddingValues(bottom = Spacing.xs)) {
                    itemsIndexed(filtered, key = { _, pair -> pair.first.id }) { index, (item, match) ->
                        PaletteRow(item, match.positions, index == selected) { onDismiss(); item.action() }
                    }
                }
                if (filtered.isEmpty()) {
                    Text("No matches", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg))
                }
            }
        }
    }
}

/** A scope shortcut chip; it is highlighted when the query starts with its prefix. */
@Composable
private fun ScopeHint(label: String, prefix: String, query: String, onClick: () -> Unit) {
    val active = if (prefix.isEmpty()) query.firstOrNull()?.let { it !in "#>@" } ?: true else query.startsWith(prefix)
    PillChip(if (prefix.isEmpty()) label else "$prefix $label", selected = active, onClick = onClick)
}

@Composable
private fun PaletteRow(item: PaletteItem, positions: List<Int>, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CodeinTheme.colors.tier3 else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = Sizes.MinTouch)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        when {
            item.fileName != null -> FileTypeBadge(item.fileName, size = Sizes.IconSm)
            item.icon != null -> Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.IconSm))
            else -> Spacer(Modifier.size(Sizes.IconSm))
        }
        Column(Modifier.weight(1f)) {
            val accent = MaterialTheme.colorScheme.primary
            Text(
                buildAnnotatedString {
                    item.title.forEachIndexed { i, ch ->
                        if (i in positions) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(ch) } else append(ch)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotEmpty()) {
                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(item.category.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        item.shortcut?.let { CountBadge(it, MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
