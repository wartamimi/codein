package com.codein.ide.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

/** One key of the terminal's key bar: a small rounded label, highlighted while [active]. */
@Composable
private fun ExtraKey(label: String, description: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else CodeinTheme.colors.tier3)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Dock panel with a line-based Android shell running in the project folder. */
@Composable
fun TerminalPanel(viewModel: TerminalViewModel, projectDir: File?, modifier: Modifier = Modifier) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var ctrl by remember { mutableStateOf(false) }
    var historyOffset by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(projectDir) { projectDir?.let(viewModel::open) }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1) }

    fun setText(text: String) { input = TextFieldValue(text, TextRange(text.length)) }

    fun submit() {
        viewModel.send(input.text)
        setText("")
        historyOffset = 0
    }

    fun historyUp() { viewModel.historyAt(historyOffset + 1)?.let { setText(it); historyOffset++ } }
    fun historyDown() {
        if (historyOffset > 1) { historyOffset--; setText(viewModel.historyAt(historyOffset).orEmpty()) } else { historyOffset = 0; setText("") }
    }
    fun tab() {
        val result = viewModel.complete(input.text)
        setText(result.text)
        if (result.candidates.isNotEmpty()) viewModel.showCandidates(result.candidates)
    }
    fun moveCursor(by: Int) {
        val at = (input.selection.start + by).coerceIn(0, input.text.length)
        input = input.copy(selection = TextRange(at))
    }
    fun insert(text: String) {
        val sel = input.selection
        val next = input.text.replaceRange(sel.min, sel.max, text)
        input = TextFieldValue(next, TextRange(sel.min + text.length))
    }
    /** Ctrl+letter from the key bar or a hardware keyboard. Returns true when it did something. */
    fun control(letter: Char): Boolean = when (letter.lowercaseChar()) {
        'c' -> { viewModel.interrupt(); setText(""); true }
        'l' -> { viewModel.clear(); true }
        'u' -> { setText(""); true }
        'a' -> { input = input.copy(selection = TextRange(0)); true }
        'e' -> { input = input.copy(selection = TextRange(input.text.length)); true }
        'd' -> { if (input.text.isEmpty()) viewModel.send("exit"); true }
        else -> false
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
                onValueChange = { next ->
                    if (ctrl && next.text.length == input.text.length + 1) {
                        val typed = next.text[next.selection.start - 1]
                        ctrl = false
                        if (control(typed)) return@BasicTextField
                    }
                    input = next
                },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.Enter -> { submit(); true }
                        e.key == Key.DirectionUp -> { historyUp(); true }
                        e.key == Key.DirectionDown -> { historyDown(); true }
                        e.key == Key.Tab -> { tab(); true }
                        e.isCtrlPressed && e.key == Key.C -> control('c')
                        e.isCtrlPressed && e.key == Key.L -> control('l')
                        e.isCtrlPressed && e.key == Key.U -> control('u')
                        e.isCtrlPressed && e.key == Key.A -> control('a')
                        e.isCtrlPressed && e.key == Key.E -> control('e')
                        e.isCtrlPressed && e.key == Key.D -> control('d')
                        else -> false
                    }
                },
            )
            ToolbarIconButton(CodeinIcons.Send, "Run") { submit() }
        }
        // Keys a phone keyboard lacks. Ctrl stays armed until the next letter (Ctrl then C interrupts).
        Row(
            Modifier.fillMaxWidth().background(CodeinTheme.colors.tier2).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ExtraKey("Tab", "Complete file name") { tab() }
            ExtraKey("Ctrl", "Control, then a letter: C interrupts, L clears, U erases the line", active = ctrl) { ctrl = !ctrl }
            ExtraKey("Esc", "Erase the line") { setText(""); ctrl = false }
            ExtraKey("←", "Cursor left") { moveCursor(-1) }
            ExtraKey("→", "Cursor right") { moveCursor(1) }
            ExtraKey("↑", "Previous command") { historyUp() }
            ExtraKey("↓", "Next command") { historyDown() }
            listOf("/", "-", "|", "~", ">", "&", "*", ".").forEach { symbol -> ExtraKey(symbol, "Type $symbol") { insert(symbol) } }
        }
    }
}
