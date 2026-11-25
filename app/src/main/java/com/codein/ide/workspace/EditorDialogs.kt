package com.codein.ide.workspace

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.theme.CodeinTheme

/** Parses `42` or `42:7` into a 1-based line and column; null when it is not a position. */
fun parseLineColumn(text: String): Pair<Int, Int>? {
    val m = Regex("""^\s*(\d+)\s*(?:[:,]\s*(\d+))?\s*$""").matchEntire(text) ?: return null
    val line = m.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    val column = m.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: 1
    return line to column
}

/** Ctrl+G: jump to `line` or `line:column`. */
@Composable
fun GoToLineDialog(currentLine: Int, onDismiss: () -> Unit, onGo: (Int, Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(currentLine.toString()) }
    val target = parseLineColumn(value)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Go to line")) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(tr("Line or line:column")) },
                singleLine = true,
                isError = value.isNotBlank() && target == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { target?.let { onGo(it.first, it.second) } }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { target?.let { onGo(it.first, it.second) } }, enabled = target != null) { Text(tr("Go")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Every physical-keyboard shortcut Codein handles, grouped as in the palette. */
val KeyboardShortcuts: List<Pair<String, String>> = listOf(
    "Ctrl+S" to "Save",
    "Ctrl+Shift+S" to "Save all",
    "Ctrl+P / Ctrl+E" to "Quick open: files and commands",
    "Ctrl+O" to "Go to symbol in file",
    "Ctrl+G" to "Go to line",
    "Ctrl+F" to "Find in file",
    "Ctrl+H" to "Find and replace in file",
    "Ctrl+Shift+F" to "Search in project",
    "Ctrl+Shift+H" to "Replace in project",
    "Ctrl+Alt+L" to "Format document",
    "F2 / Shift+F6" to "Rename symbol in all files",
    "Ctrl+/" to "Toggle comment",
    "Ctrl+D" to "Duplicate line",
    "Ctrl+Shift+K" to "Delete line",
    "Alt+↑ / Alt+↓" to "Move line up / down",
    "Ctrl+Z / Ctrl+Shift+Z" to "Undo / redo",
    "Ctrl+W" to "Close tab",
    "Ctrl+Tab / Ctrl+Shift+Tab" to "Next / previous tab",
    "Ctrl+B / F5" to "Build & run",
    "Ctrl+`" to "Terminal",
    "Ctrl+J" to "Codein AI",
)

@Composable
fun KeyboardShortcutsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Keyboard shortcuts")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyboardShortcuts.forEach { (keys, action) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(action, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Text(keys, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = CodeinTheme.colors.textMuted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

/** F2: rename a class, function or variable in every Kotlin, Java, XML and Gradle file. */
@Composable
fun RenameSymbolDialog(rename: RenameSymbolUi, onIntent: (WorkspaceIntent) -> Unit) {
    var value by rememberSaveable(rename.symbol) { mutableStateOf(rename.symbol) }
    val valid = Regex("[A-Za-z_][A-Za-z0-9_]*").matches(value.trim()) && value.trim() != rename.symbol
    val uses = rename.uses
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.CancelRenameSymbol) },
        title = { Text("Rename ${rename.symbol}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(tr("New name")) },
                    singleLine = true,
                    isError = value.isNotBlank() && !Regex("[A-Za-z_][A-Za-z0-9_]*").matches(value.trim()),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { if (valid && uses != null) onIntent(WorkspaceIntent.RenameSymbol(value)) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    when {
                        rename.searching -> "Finding uses…"
                        uses == null || uses.totalHits == 0 -> "No uses found"
                        else -> "${uses.totalHits} use(s) in ${uses.files.size} file(s)" + (rename.fileToRename?.let { " · renames $it" } ?: "")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CodeinTheme.colors.textMuted,
                )
                if (uses != null && uses.files.isNotEmpty()) {
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        uses.files.forEach { f ->
                            Text("${f.relativePath}  (${f.hits.size})", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = CodeinTheme.colors.textMuted)
                        }
                    }
                }
                Text(tr("Matches whole words with the same case, including comments and strings. Each file's old text is kept in local history."),
                    style = MaterialTheme.typography.labelSmall,
                    color = CodeinTheme.colors.textMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(WorkspaceIntent.RenameSymbol(value)) }, enabled = valid && uses != null && uses.totalHits > 0) { Text(tr("Rename")) }
        },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.CancelRenameSymbol) }) { Text(tr("Cancel")) } },
    )
}
