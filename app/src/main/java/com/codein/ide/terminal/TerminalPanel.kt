package com.codein.ide.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import java.io.File

/** Dock panel with a line-based Android shell running in the project folder. */
@Composable
fun TerminalPanel(viewModel: TerminalViewModel, projectDir: File?, modifier: Modifier = Modifier) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var historyOffset by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(projectDir) { projectDir?.let(viewModel::open) }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1) }

    fun submit() {
        viewModel.send(input)
        input = ""
        historyOffset = 0
    }

    Column(modifier.background(CodeinTheme.colors.base)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (running) "sh · ${projectDir?.name ?: ""}" else "Shell stopped",
                style = MaterialTheme.typography.labelMedium,
                color = CodeinTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            ToolbarIconButton(CodeinIcons.Stop, "Interrupt running command") { viewModel.interrupt() }
            ToolbarIconButton(CodeinIcons.Trash, "Clear") { viewModel.clear() }
            ToolbarIconButton(CodeinIcons.Refresh, "Restart shell") { viewModel.restart() }
        }
        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = when (line.kind) {
                            TerminalLine.Kind.Error -> MaterialTheme.colorScheme.error
                            TerminalLine.Kind.Command -> MaterialTheme.colorScheme.primary
                            TerminalLine.Kind.System -> CodeinTheme.colors.textMuted
                            TerminalLine.Kind.Output -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(CodeinTheme.colors.tier2).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.Enter -> { submit(); true }
                        e.key == Key.DirectionUp -> {
                            viewModel.historyAt(historyOffset + 1)?.let { input = it; historyOffset++ }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            if (historyOffset > 1) { historyOffset--; input = viewModel.historyAt(historyOffset).orEmpty() } else { historyOffset = 0; input = "" }
                            true
                        }
                        e.isCtrlPressed && e.key == Key.C -> { viewModel.interrupt(); true }
                        e.isCtrlPressed && e.key == Key.L -> { viewModel.clear(); true }
                        else -> false
                    }
                },
            )
            ToolbarIconButton(CodeinIcons.Send, "Run") { submit() }
        }
    }
}
