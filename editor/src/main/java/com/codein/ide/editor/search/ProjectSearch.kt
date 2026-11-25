package com.codein.ide.editor.search

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.PanelHeader
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.components.CompactTextField
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.icons.FolderChevron
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.codeTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Applies a project-wide replace to file texts; pure so it can be tested without the UI. */
object ProjectReplace {
    /** Returns the new text of each changed file and the total number of replacements. */
    fun plan(
        files: List<FileHits>,
        excluded: Set<String>,
        query: String,
        replacement: String,
        options: FindOptions,
        currentText: (FileHits) -> String?,
    ): Pair<Map<FileHits, String>, Int> {
        val out = LinkedHashMap<FileHits, String>()
        var total = 0
        files.filter { it.relativePath !in excluded }.forEach { f ->
            val text = currentText(f) ?: return@forEach
            val (next, count) = FindReplaceEngine.replaceAll(text, query, replacement, options)
            if (count > 0 && next != text) { out[f] = next; total += count }
        }
        return out to total
    }
}

data class LineHit(val line: Int, val column: Int, val preview: String, val highlightStart: Int, val highlightEnd: Int, val offset: Int, val length: Int)

data class FileHits(val file: File, val relativePath: String, val hits: List<LineHit>)

data class ProjectSearchResult(val files: List<FileHits>, val totalHits: Int, val scannedFiles: Int, val truncated: Boolean)

/** Cross-file regex search across a project directory with glob include filters. */
object ProjectSearchEngine {

    private val skipDirs = setOf("build", ".gradle", ".git", ".idea", ".kotlin", "node_modules", ".cxx", ".codein")
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024

    /** Converts a comma-separated glob list (`*.kt, *.xml, *.gradle*`) into matchers. */
    fun globMatchers(globs: String): List<Regex> = globs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { glob ->
        val regex = buildString {
            append('^')
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    c == '*' && glob.getOrNull(i + 1) == '*' -> { append(".*"); i++ }
                    c == '*' -> append("[^/]*")
                    c == '?' -> append("[^/]")
                    c == '{' -> append("(?:")
                    c == '}' -> append(")")
                    c == ',' -> append("|")
                    "\\.[]()+^$|".contains(c) -> append('\\').append(c)
                    else -> append(c)
                }
                i++
            }
            append('$')
        }
        Regex(regex, RegexOption.IGNORE_CASE)
    }

    suspend fun search(
        root: File,
        query: String,
        options: FindOptions,
        includeGlobs: String,
        maxHits: Int = 2000,
    ): ProjectSearchResult = withContext(Dispatchers.IO) {
        val pattern = FindReplaceEngine.compile(query, options).getOrNull()
            ?: return@withContext ProjectSearchResult(emptyList(), 0, 0, false)
        val matchers = globMatchers(includeGlobs)
        val results = mutableListOf<FileHits>()
        var total = 0
        var scanned = 0
        var truncated = false
        val files = root.walkTopDown()
            .onEnter { dir -> dir == root || dir.name !in skipDirs }
            .filter { it.isFile && it.length() <= MAX_FILE_BYTES }
            .filter { f -> matchers.isEmpty() || matchers.any { m -> m.matches(f.name) || m.matches(f.relativeTo(root).invariantSeparatorsPath) } }
        for (file in files) {
            currentCoroutineContext().ensureActive()
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            if (text.indexOf('\u0000') >= 0) continue
            scanned++
            val lineStarts = FindReplaceEngine.lineStarts(text)
            val matcher = pattern.matcher(text)
            val hits = mutableListOf<LineHit>()
            while (matcher.find()) {
                if (matcher.end() == matcher.start()) continue
                val line = FindReplaceEngine.lineOf(lineStarts, matcher.start())
                val lineStart = lineStarts[line]
                val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
                val raw = text.substring(lineStart, lineEnd)
                val trimmedLead = raw.length - raw.trimStart().length
                val preview = raw.trim().take(240)
                val hs = (matcher.start() - lineStart - trimmedLead).coerceIn(0, preview.length)
                val he = (matcher.end() - lineStart - trimmedLead).coerceIn(hs, preview.length)
                hits += LineHit(line + 1, matcher.start() - lineStart + 1, preview, hs, he, matcher.start(), matcher.end() - matcher.start())
                total++
                if (total >= maxHits) { truncated = true; break }
            }
            if (hits.isNotEmpty()) results += FileHits(file, file.relativeTo(root).invariantSeparatorsPath, hits)
            if (truncated) break
        }
        ProjectSearchResult(results, total, scanned, truncated)
    }
}

data class GlobalSearchState(
    val query: String = "",
    val replacement: String = "",
    val replaceOpen: Boolean = false,
    /** Relative paths left out of "Replace all". */
    val excluded: Set<String> = emptySet(),
    val includeGlobs: String = "*.kt, *.java, *.xml, *.gradle*",
    val options: FindOptions = FindOptions(),
    val searching: Boolean = false,
    val result: ProjectSearchResult? = null,
)

/** Actions of the project search panel's replace row. */
interface ProjectReplaceActions {
    fun onReplacementChange(text: String)
    fun onToggleReplace()
    fun onToggleExcluded(relativePath: String)
    fun onReplaceAll()
}

/** Global project search panel with collapsible per-file result cards. */
@Composable
fun GlobalSearchPanel(
    state: GlobalSearchState,
    onQueryChange: (String) -> Unit,
    onGlobsChange: (String) -> Unit,
    onOptionsChange: (FindOptions) -> Unit,
    onOpenHit: (File, LineHit) -> Unit,
    modifier: Modifier = Modifier,
    replace: ProjectReplaceActions? = null,
) {
    val validation = FindReplaceEngine.validate(state.query, state.options)
    var confirmReplace by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        PanelHeader("Search in project", icon = CodeinIcons.Search) {
            if (replace != null) ToolbarIconButton(CodeinIcons.Replace, "Replace in files", active = state.replaceOpen, size = 30.dp) { replace.onToggleReplace() }
            ToolbarIconButton(CodeinIcons.CaseSensitive, "Match case", active = state.options.matchCase, size = 30.dp) { onOptionsChange(state.options.copy(matchCase = !state.options.matchCase)) }
            ToolbarIconButton(CodeinIcons.WholeWord, "Whole word", active = state.options.wholeWord, size = 30.dp) { onOptionsChange(state.options.copy(wholeWord = !state.options.wholeWord)) }
            ToolbarIconButton(CodeinIcons.Regex, "Regex", active = state.options.regex, size = 30.dp) { onOptionsChange(state.options.copy(regex = !state.options.regex)) }
        }
        val invalid = validation as? QueryValidation.Invalid
        CompactTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = if (state.options.regex) "Regex, e.g. fun (\\w+)\\(" else "Search text",
            leadingIcon = CodeinIcons.Search,
            isError = invalid != null,
            textStyle = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 13f, false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        )
        if (invalid != null) {
            Text(invalid.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
        }
        if (replace != null && state.replaceOpen) {
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                CompactTextField(
                    value = state.replacement,
                    onValueChange = replace::onReplacementChange,
                    placeholder = if (state.options.regex) "Replace with, \$1 for groups" else "Replace with",
                    leadingIcon = CodeinIcons.Replace,
                    textStyle = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 13f, false),
                    modifier = Modifier.weight(1f),
                )
                val replaceable = state.result?.let { r -> r.files.filter { it.relativePath !in state.excluded } }.orEmpty()
                TextButton(onClick = { confirmReplace = true }, enabled = replaceable.isNotEmpty() && invalid == null && !state.searching) {
                    Text("Replace all")
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        CompactTextField(
            value = state.includeGlobs,
            onValueChange = onGlobsChange,
            placeholder = "Files to include, e.g. *.kt",
            leadingIcon = CodeinIcons.Filter,
            textStyle = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        )
        Spacer(Modifier.height(Spacing.sm))
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        state.result?.let { r ->
            Text(
                "${r.totalHits} result(s) in ${r.files.size} file(s) · ${r.scannedFiles} scanned" + if (r.truncated) " · truncated" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            items(state.result?.files.orEmpty(), key = { it.file.absolutePath }) { fileHits ->
                FileHitsCard(
                    fileHits,
                    onOpenHit,
                    included = if (replace != null && state.replaceOpen) fileHits.relativePath !in state.excluded else null,
                    onToggleIncluded = { replace?.onToggleExcluded(fileHits.relativePath) },
                )
            }
        }
    }
    if (confirmReplace && replace != null) {
        val files = state.result?.files.orEmpty().filter { it.relativePath !in state.excluded }
        val count = files.sumOf { it.hits.size }
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace in ${files.size} file(s)?") },
            text = {
                Text(
                    "$count match(es) of \"${state.query}\" become \"${state.replacement}\". Files are saved right away; " +
                        "each file's previous version is kept in its local history.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmReplace = false; replace.onReplaceAll() }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FileHitsCard(fileHits: FileHits, onOpenHit: (File, LineHit) -> Unit, included: Boolean? = null, onToggleIncluded: () -> Unit = {}) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CodeinTheme.colors.tier2)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .animateContentSize(com.codein.ide.core.ui.theme.Motion.spatial()),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderChevron(expanded)
            Spacer(Modifier.width(4.dp))
            FileTypeBadge(fileHits.file.name)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(fileHits.file.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(fileHits.relativePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CountBadge(fileHits.hits.size.toString(), MaterialTheme.colorScheme.primary)
            if (included != null) {
                Checkbox(
                    checked = included,
                    onCheckedChange = { onToggleIncluded() },
                    modifier = Modifier.semantics { contentDescription = if (included) "Included in replace" else "Left out of replace" },
                )
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = 4.dp)) {
                val highlight = CodeinTheme.editorScheme.matchHighlight
                val style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false)
                fileHits.hits.take(200).forEach { hit ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenHit(fileHits.file, hit) }.padding(horizontal = 12.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(hit.line.toString(), style = style, color = CodeinTheme.colors.textMuted, modifier = Modifier.width(36.dp))
                        Text(
                            buildAnnotatedString {
                                append(hit.preview.substring(0, hit.highlightStart))
                                withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.Bold)) {
                                    append(hit.preview.substring(hit.highlightStart, hit.highlightEnd))
                                }
                                append(hit.preview.substring(hit.highlightEnd))
                            },
                            style = style,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
