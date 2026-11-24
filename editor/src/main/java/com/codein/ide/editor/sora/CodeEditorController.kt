package com.codein.ide.editor.sora

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.theme.EditorColorScheme
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme as SoraScheme

/** A diagnostic to render as a squiggle, in 1-based line/column coordinates. */
data class EditorDiagnostic(
    val line: Int,
    val column: Int?,
    val endColumn: Int?,
    val message: String,
    val isError: Boolean,
    val source: String,
)

/** Cursor/selection snapshot in 0-based coordinates. */
data class CursorState(
    val line: Int = 0,
    val column: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val selectedText: String = "",
) {
    val hasSelection: Boolean get() = selectionEnd > selectionStart
}

/**
 * Imperative bridge between Compose state and the Sora [CodeEditor] view. All methods must be
 * called on the main thread. The controller survives recompositions; the view is attached by
 * [SoraCodeEditor].
 */
class CodeEditorController {
    var editor: CodeEditor? = null
        private set

    private var language: EditorLanguage = EditorLanguage.PlainText
    private var appliedSchemeId: String? = null
    private var appliedFont: Int? = null
    private var suppressChangeEvents = false
    private var diagnostics: List<EditorDiagnostic> = emptyList()

    var onTextChanged: ((String) -> Unit)? = null
    var onCursorChanged: ((CursorState) -> Unit)? = null
    var onDiagnosticTapped: ((EditorDiagnostic) -> Unit)? = null

    internal fun attach(view: CodeEditor) {
        editor = view
        view.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
            if (!suppressChangeEvents) onTextChanged?.invoke(view.text.toString())
        }
        view.subscribeEvent(SelectionChangeEvent::class.java) { _, _ -> onCursorChanged?.invoke(cursorState()) }
        view.subscribeEvent(ClickEvent::class.java) { event, _ ->
            val pos = event.charPosition
            diagnosticAt(pos.line + 1, pos.column + 1)?.let { d -> onDiagnosticTapped?.invoke(d) }
        }
    }

    internal fun detach() {
        editor?.release()
        editor = null
        appliedSchemeId = null
        appliedFont = null
    }

    fun text(): String = editor?.text?.toString().orEmpty()

    /** Replaces the whole document without firing a change callback (used when switching tabs). */
    fun setDocument(content: String, newLanguage: EditorLanguage) {
        val view = editor ?: return
        suppressChangeEvents = true
        try {
            if (newLanguage != language || view.editorLanguage is EmptyLanguage) {
                language = newLanguage
                view.setEditorLanguage(
                    if (newLanguage == EditorLanguage.PlainText) EmptyLanguage()
                    else TextMateLanguage.create(newLanguage.scopeName, true),
                )
            }
            if (view.text.toString() != content) view.setText(content)
        } finally {
            suppressChangeEvents = false
        }
        applyDiagnostics(diagnostics)
    }

    fun applyPreferences(context: Context, prefs: EditorPreferences) {
        val view = editor ?: return
        if (appliedFont != prefs.codingFont.fontRes) {
            val typeface = ResourcesCompat.getFont(context, prefs.codingFont.fontRes) ?: Typeface.MONOSPACE
            view.typefaceText = typeface
            view.typefaceLineNumber = typeface
            appliedFont = prefs.codingFont.fontRes
        }
        view.setTextSize(prefs.fontSizeSp)
        view.setLineSpacing(0f, prefs.lineHeightMultiplier)
        view.tabWidth = prefs.tabSize
        view.setWordwrap(prefs.softWrap)
        view.setLigatureEnabled(prefs.ligatures && prefs.codingFont.supportsLigatures)
        view.isLineNumberEnabled = true
        view.props.useICULibToSelectWords = true
        view.props.symbolPairAutoCompletion = true
        view.isBlockLineEnabled = prefs.showIndentGuides
    }

    fun applyScheme(scheme: EditorColorScheme) {
        val view = editor ?: return
        if (appliedSchemeId == scheme.id) return
        TextMateSetup.applyTheme(scheme)
        val colors = runCatching { TextMateColorScheme.create(ThemeRegistry.getInstance()) }.getOrElse { SoraScheme() }
        colors.setColor(SoraScheme.WHOLE_BACKGROUND, scheme.background.toArgb())
        colors.setColor(SoraScheme.TEXT_NORMAL, scheme.foreground.toArgb())
        colors.setColor(SoraScheme.LINE_NUMBER_BACKGROUND, scheme.gutterBackground.toArgb())
        colors.setColor(SoraScheme.LINE_NUMBER, scheme.lineNumber.toArgb())
        colors.setColor(SoraScheme.LINE_NUMBER_CURRENT, scheme.lineNumberActive.toArgb())
        colors.setColor(SoraScheme.CURRENT_LINE, scheme.currentLine.toArgb())
        colors.setColor(SoraScheme.SELECTED_TEXT_BACKGROUND, scheme.selection.toArgb())
        colors.setColor(SoraScheme.SELECTION_INSERT, scheme.cursor.toArgb())
        colors.setColor(SoraScheme.SELECTION_HANDLE, scheme.cursor.toArgb())
        colors.setColor(SoraScheme.BLOCK_LINE, scheme.indentGuide.toArgb())
        colors.setColor(SoraScheme.BLOCK_LINE_CURRENT, scheme.lineNumber.toArgb())
        colors.setColor(SoraScheme.LINE_DIVIDER, scheme.gutterBackground.toArgb())
        colors.setColor(SoraScheme.MATCHED_TEXT_BACKGROUND, scheme.matchHighlight.toArgb())
        colors.setColor(SoraScheme.PROBLEM_ERROR, scheme.error.toArgb())
        colors.setColor(SoraScheme.PROBLEM_WARNING, scheme.warning.toArgb())
        view.colorScheme = colors
        appliedSchemeId = scheme.id
    }

    /** Converts 1-based diagnostics to Sora diagnostic regions (squiggles). */
    fun applyDiagnostics(list: List<EditorDiagnostic>) {
        diagnostics = list
        val view = editor ?: return
        val content = view.text
        val container = DiagnosticsContainer()
        list.forEach { d ->
            val line = (d.line - 1).coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
            val lineLength = content.getColumnCount(line)
            val startCol = ((d.column ?: 1) - 1).coerceIn(0, lineLength)
            val endCol = (d.endColumn?.minus(1) ?: lineLength).coerceIn(startCol, lineLength).let { if (it == startCol) lineLength else it }
            val start = content.getCharIndex(line, startCol)
            val end = content.getCharIndex(line, endCol).coerceAtLeast(start + 1).coerceAtMost(content.length)
            if (end > start) {
                container.addDiagnostic(
                    DiagnosticRegion(start, end, if (d.isError) DiagnosticRegion.SEVERITY_ERROR else DiagnosticRegion.SEVERITY_WARNING),
                )
            }
        }
        view.diagnostics = container
    }

    private fun diagnosticAt(line: Int, column: Int): EditorDiagnostic? = diagnostics.firstOrNull { d ->
        d.line == line && (d.column == null || column >= d.column) && (d.endColumn == null || column <= d.endColumn)
    } ?: diagnostics.firstOrNull { it.line == line }

    fun cursorState(): CursorState {
        val view = editor ?: return CursorState()
        val cursor = view.cursor
        val start = cursor.left
        val end = cursor.right
        val selected = if (end > start) view.text.subSequence(start, end).toString() else ""
        return CursorState(cursor.leftLine, cursor.leftColumn, start, end, selected)
    }

    fun undo() { editor?.takeIf { it.canUndo() }?.undo() }
    fun redo() { editor?.takeIf { it.canRedo() }?.redo() }
    fun canUndo(): Boolean = editor?.canUndo() == true
    fun canRedo(): Boolean = editor?.canRedo() == true

    /** Inserts text at the cursor (replacing any selection). [caretBack] moves the caret left afterwards. */
    fun insert(text: String, caretBack: Int = 0) {
        val view = editor ?: return
        view.commitText(text)
        if (caretBack > 0) {
            val cursor = view.cursor
            val index = (cursor.left - caretBack).coerceAtLeast(0)
            val pos = view.text.indexer.getCharPosition(index)
            view.setSelection(pos.line, pos.column)
        }
    }

    fun insertTab(tabSize: Int) = insert(" ".repeat(tabSize))

    fun goTo(line: Int, column: Int = 1) {
        val view = editor ?: return
        val l = (line - 1).coerceIn(0, (view.text.lineCount - 1).coerceAtLeast(0))
        val c = (column - 1).coerceIn(0, view.text.getColumnCount(l))
        view.setSelection(l, c)
        view.ensureSelectionVisible()
    }

    /** Selects the character range [start, end) and scrolls it into view. */
    fun select(start: Int, end: Int) {
        val view = editor ?: return
        val content = view.text
        val s = content.indexer.getCharPosition(start.coerceIn(0, content.length))
        val e = content.indexer.getCharPosition(end.coerceIn(0, content.length))
        view.setSelectionRegion(s.line, s.column, e.line, e.column)
        view.ensureSelectionVisible()
    }

    /** Replaces the character range [start, end) with [replacement]. */
    fun replaceRange(start: Int, end: Int, replacement: String) {
        val view = editor ?: return
        val content = view.text
        val s = content.indexer.getCharPosition(start.coerceIn(0, content.length))
        val e = content.indexer.getCharPosition(end.coerceIn(0, content.length))
        content.replace(s.line, s.column, e.line, e.column, replacement)
    }

    /** Replaces the full document as a single undoable edit (used by Replace All / Apply Diff). */
    fun replaceAll(newText: String) {
        val view = editor ?: return
        val content = view.text
        val lastLine = (content.lineCount - 1).coerceAtLeast(0)
        content.replace(0, 0, lastLine, content.getColumnCount(lastLine), newText)
    }

    /** Toggles line comments (or block comments for XML) on the selected lines. */
    fun toggleComment() {
        val view = editor ?: return
        val content = view.text
        val cursor = view.cursor
        val firstLine = cursor.leftLine
        val lastLine = cursor.rightLine
        val lineComment = language.lineComment
        if (lineComment == null) {
            val block = language.blockComment ?: return
            val start = content.getCharIndex(firstLine, 0)
            val end = content.getCharIndex(lastLine, content.getColumnCount(lastLine))
            val selected = content.subSequence(start, end).toString()
            val trimmed = selected.trim()
            val replacement = if (trimmed.startsWith(block.first) && trimmed.endsWith(block.second)) {
                selected.replaceFirst(block.first + " ", "").replaceFirst(block.first, "")
                    .let { s -> s.substring(0, s.lastIndexOf(block.second)).trimEnd() + s.substring(s.lastIndexOf(block.second) + block.second.length) }
            } else {
                "${block.first} $selected ${block.second}"
            }
            content.replace(firstLine, 0, lastLine, content.getColumnCount(lastLine), replacement)
            return
        }
        val lines = (firstLine..lastLine).map { content.getLineString(it) }
        val allCommented = lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith(lineComment) }
        content.beginBatchEdit()
        try {
            for ((offset, text) in lines.withIndex()) {
                val line = firstLine + offset
                if (text.isBlank()) continue
                val indent = text.indexOfFirst { !it.isWhitespace() }
                if (allCommented) {
                    val remove = if (text.startsWith("$lineComment ", indent)) lineComment.length + 1 else lineComment.length
                    content.delete(line, indent, line, indent + remove)
                } else {
                    val minIndent = lines.filter { it.isNotBlank() }.minOf { l -> l.indexOfFirst { c -> !c.isWhitespace() } }
                    content.insert(line, minIndent, "$lineComment ")
                }
            }
        } finally {
            content.endBatchEdit()
        }
    }
}
