package com.codein.ide.editor.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 56.dp).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CodeinIcons.Command, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search files, # symbols, > actions, @ Gradle", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    CountBadge("Ctrl+P", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), state = listState) {
                    itemsIndexed(filtered, key = { _, pair -> pair.first.id }) { index, (item, match) ->
                        PaletteRow(item, match.positions, index == selected) { onDismiss(); item.action() }
                    }
                }
                if (filtered.isEmpty()) {
                    Text("No matches", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(item: PaletteItem, positions: List<Int>, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            item.fileName != null -> FileTypeBadge(item.fileName)
            item.icon != null -> Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            else -> Spacer(Modifier.size(18.dp))
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
