package com.codein.ide.editor.sora

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.theme.EditorColorScheme
import io.github.rosemoe.sora.widget.CodeEditor

/** The document shown in the editor; [key] identifies the open file (e.g., its absolute path). */
data class EditorDocument(val key: String, val fileName: String, val content: String, val version: Long)

@Composable
fun rememberCodeEditorController(): CodeEditorController = remember { CodeEditorController() }

/**
 * Reactive Compose wrapper around Sora's [CodeEditor].
 *
 * - The document is pushed into the view only when [document] key/version changes, so typing does
 *   not cause a feedback loop.
 * - Preferences (font, size, line height, ligatures, tab size, wrap) and the color scheme are
 *   re-applied whenever they change.
 */
@Composable
fun SoraCodeEditor(
    controller: CodeEditorController,
    document: EditorDocument?,
    preferences: EditorPreferences,
    scheme: EditorColorScheme,
    diagnostics: List<EditorDiagnostic>,
    onTextChanged: (String) -> Unit,
    onCursorChanged: (CursorState) -> Unit,
    onDiagnosticTapped: (EditorDiagnostic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textCallback = rememberUpdatedState(onTextChanged)
    val cursorCallback = rememberUpdatedState(onCursorChanged)
    val diagnosticCallback = rememberUpdatedState(onDiagnosticTapped)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextMateSetup.ensureInitialized(ctx)
            CodeEditor(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = true
                controller.attach(this)
                controller.onTextChanged = { textCallback.value(it) }
                controller.onCursorChanged = { cursorCallback.value(it) }
                controller.onDiagnosticTapped = { diagnosticCallback.value(it) }
                controller.applyScheme(scheme)
                controller.applyPreferences(ctx, preferences)
            }
        },
    )

    LaunchedEffect(scheme.id) { controller.applyScheme(scheme) }
    LaunchedEffect(preferences) { controller.applyPreferences(context, preferences) }
    LaunchedEffect(document?.key, document?.version) {
        if (document != null) controller.setDocument(document.content, EditorLanguage.forFileName(document.fileName))
        else controller.setDocument("", EditorLanguage.PlainText)
    }
    LaunchedEffect(diagnostics) { controller.applyDiagnostics(diagnostics) }
    DisposableEffect(controller) { onDispose { controller.detach() } }
}
