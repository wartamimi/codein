package com.codein.ide.workspace

import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildStep
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.LogLevel
import com.codein.ide.compiler.model.StepStatus
import com.codein.ide.compiler.sdk.ToolchainStatus
import com.codein.ide.core.ui.icons.GitFileStatus
import com.codein.ide.editor.search.FindBarState
import com.codein.ide.editor.search.GlobalSearchState
import com.codein.ide.editor.sora.CursorState
import java.io.File

/** Renaming [symbol] everywhere in the project's sources: its uses, found as whole words with matching case. */
data class RenameSymbolUi(
    val symbol: String,
    val uses: com.codein.ide.editor.search.ProjectSearchResult? = null,
    val searching: Boolean = true,
) {
    /** A source file named after the symbol (class Foo in Foo.kt), renamed along with it. */
    val fileToRename: String? get() = uses?.files?.firstOrNull { it.file.nameWithoutExtension == symbol && it.file.extension in setOf("kt", "java") }?.relativePath
}

/** Versions of [relativePath] kept by [LocalHistory] and the diff of the [selected] one against the current text. */
data class FileHistoryUi(
    val relativePath: String,
    val versions: List<LocalHistory.Version>,
    val selected: LocalHistory.Version? = null,
    val diff: String = "",
)

/** One visible row of the flattened file explorer tree. */
data class TreeRow(
    val file: File,
    val relativePath: String,
    val depth: Int,
    val isDirectory: Boolean,
    val expanded: Boolean,
)

/** An open editor tab. [version] changes only when the text is replaced from outside the editor. */
data class OpenFile(
    val relativePath: String,
    val file: File,
    val content: String,
    val savedContent: String,
    val version: Long,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
) {
    val name: String get() = file.name
    val isDirty: Boolean get() = content != savedContent
}

data class StepUi(val status: StepStatus = StepStatus.Pending, val durationMillis: Long? = null, val startedAt: Long? = null)

data class BuildLogLine(val id: Long, val step: BuildStep?, val level: LogLevel, val message: String, val timestamp: Long)

data class BuildUiState(
    val running: Boolean = false,
    val variant: BuildVariant = BuildVariant.DebugApk,
    val steps: Map<BuildStep, StepUi> = BuildStep.entries.associateWith { StepUi() },
    val logs: List<BuildLogLine> = emptyList(),
    val diagnostics: List<CompilerDiagnostic> = emptyList(),
    val progress: Float = 0f,
    val result: BuildResult? = null,
    val startedAt: Long? = null,
    val currentStep: BuildStep? = null,
    /** Earlier builds of this project, newest first. */
    val history: List<BuildRecord> = emptyList(),
    /** Size breakdown of the newest APK/AAB, when one exists. */
    val outputSize: ApkSize? = null,
)

enum class DockTab(val title: String) { Build("Build"), Logcat("Logcat"), Terminal("Terminal"), Ai("Codein AI"), Search("Search"), Git("Git"), Dependencies("Dependencies") }

data class WorkspaceState(
    val projectDir: File? = null,
    val projectName: String = "",
    val tree: List<TreeRow> = emptyList(),
    val expanded: Set<String> = setOf(""),
    val gitStatus: Map<String, GitFileStatus> = emptyMap(),
    val branch: String? = null,
    val tabs: List<OpenFile> = emptyList(),
    val activeIndex: Int = -1,
    /** Bumped when typing-time Java checks produce new problems, so the editor redraws them. */
    val liveCheckVersion: Int = 0,
    val cursor: CursorState = CursorState(),
    val find: FindBarState = FindBarState(),
    val search: GlobalSearchState = GlobalSearchState(),
    /** Rename-symbol dialog, or null when closed. */
    val rename: RenameSymbolUi? = null,
    /** Local history dialog of one file, or null when closed. */
    val history: FileHistoryUi? = null,
    val build: BuildUiState = BuildUiState(),
    val dockTab: DockTab = DockTab.Build,
    val dockVisible: Boolean = false,
    val paletteOpen: Boolean = false,
    val paletteQuery: String = "",
    val preferencesOpen: Boolean = false,
    val toolchain: ToolchainStatus? = null,
    val gradleFiles: List<String> = emptyList(),
) {
    val activeFile: OpenFile? get() = tabs.getOrNull(activeIndex)
    val errorCount: Int get() = build.diagnostics.count { it.severity == com.codein.ide.compiler.model.Severity.Error }
}

/** Imperative commands the view model sends to the editor controller. */
sealed interface EditorCommand {
    data class GoTo(val line: Int, val column: Int) : EditorCommand
    data class Select(val start: Int, val end: Int) : EditorCommand
    data class ReplaceRange(val start: Int, val end: Int, val text: String) : EditorCommand
    data class Insert(val text: String, val caretBack: Int = 0) : EditorCommand
    data class InsertTab(val size: Int) : EditorCommand
    data object Undo : EditorCommand
    data object Redo : EditorCommand
    data object ToggleComment : EditorCommand
    /** Replaces the whole document in one undoable step (Format document). */
    data class ReplaceAllText(val text: String) : EditorCommand
}

/** One-shot UI effects. */
sealed interface WorkspaceEffect {
    /** A snackbar; with [action] it shows [actionLabel] and sends [action] when tapped. */
    data class Message(val text: String, val actionLabel: String? = null, val action: Any? = null) : WorkspaceEffect
    data class InstallApk(val apk: File) : WorkspaceEffect
    data class ShareFile(val file: File, val mimeType: String) : WorkspaceEffect
    data object OpenAiDock : WorkspaceEffect
}

/** Builds the flattened, sorted file tree for the expanded directories. */
object FileTreeBuilder {
    private val hidden = setOf(".git", ".gradle", ".idea", "build", ".kotlin", ".cxx", ".codein")

    fun build(root: File, expanded: Set<String>): List<TreeRow> {
        val rows = ArrayList<TreeRow>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles().orEmpty()
                .filter { it.name !in hidden }
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            for (child in children) {
                val rel = child.relativeTo(root).invariantSeparatorsPath
                val isDir = child.isDirectory
                val open = isDir && rel in expanded
                rows += TreeRow(child, rel, depth, isDir, open)
                if (open) walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return rows
    }
}
