package com.codein.ide.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.ai.context.AiContextSnapshot
import com.codein.ide.ai.diff.FilePatch
import com.codein.ide.ai.diff.LineDiff
import com.codein.ide.ai.diff.PatchResult
import com.codein.ide.ai.diff.UnifiedDiff
import com.codein.ide.compiler.BuildOrchestrator
import com.codein.ide.compiler.BuildRequest
import com.codein.ide.compiler.model.BuildEvent
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.Severity
import com.codein.ide.compiler.model.StepStatus
import com.codein.ide.compiler.sdk.BuildToolchain
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.prefs.EditorPreferencesRepository
import com.codein.ide.data.OpenTabDao
import com.codein.ide.data.OpenTabEntity
import com.codein.ide.editor.keys.ExtraKey
import com.codein.ide.editor.search.FindBarAction
import com.codein.ide.editor.search.FindReplaceEngine
import com.codein.ide.editor.search.LineHit
import com.codein.ide.editor.search.ProjectSearchEngine
import com.codein.ide.editor.search.ProjectReplace
import com.codein.ide.editor.format.CodeFormatter
import com.codein.ide.editor.settings.PreferenceChange
import com.codein.ide.editor.sora.CursorState
import com.codein.ide.editor.sora.EditorDiagnostic
import com.codein.ide.notify.BuildNotifier
import com.codein.ide.compiler.ecj.LiveJavaChecker
import com.codein.ide.project.ProjectRepository
import com.codein.ide.vcs.git.GitService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface WorkspaceIntent {
    data class ToggleDirectory(val relativePath: String) : WorkspaceIntent
    data object CollapseAll : WorkspaceIntent
    data class OpenFile(val relativePath: String, val line: Int? = null, val column: Int? = null) : WorkspaceIntent
    data class SelectTab(val index: Int) : WorkspaceIntent
    data class CloseTab(val index: Int) : WorkspaceIntent
    data class CloseOtherTabs(val index: Int) : WorkspaceIntent
    data class TextChanged(val text: String) : WorkspaceIntent
    data class CursorChanged(val cursor: CursorState) : WorkspaceIntent
    data object Save : WorkspaceIntent
    data object SaveAll : WorkspaceIntent
    data class NewFile(val parentRelative: String, val name: String) : WorkspaceIntent
    data class NewFolder(val parentRelative: String, val name: String) : WorkspaceIntent
    data class Rename(val relativePath: String, val newName: String) : WorkspaceIntent
    data class Move(val relativePath: String, val targetDirRelative: String) : WorkspaceIntent
    data class NewFromTemplate(val parentRelative: String, val kind: FileTemplateKind, val name: String) : WorkspaceIntent
    data class Delete(val relativePath: String) : WorkspaceIntent
    data object RefreshTree : WorkspaceIntent
    data object OpenFind : WorkspaceIntent
    data class Find(val action: FindBarAction) : WorkspaceIntent
    data class SearchQuery(val query: String) : WorkspaceIntent
    data class SearchGlobs(val globs: String) : WorkspaceIntent
    data class SearchOptions(val options: com.codein.ide.editor.search.FindOptions) : WorkspaceIntent
    data class OpenSearchHit(val file: File, val hit: LineHit) : WorkspaceIntent
    data class SearchReplacement(val text: String) : WorkspaceIntent
    data object ToggleSearchReplace : WorkspaceIntent
    data class ToggleReplaceExcluded(val relativePath: String) : WorkspaceIntent
    data object ReplaceInProject : WorkspaceIntent
    data class ShowHistory(val relativePath: String) : WorkspaceIntent
    data class SelectHistoryVersion(val version: LocalHistory.Version?) : WorkspaceIntent
    data class RestoreHistoryVersion(val version: LocalHistory.Version) : WorkspaceIntent
    data object CloseHistory : WorkspaceIntent
    data object FormatDocument : WorkspaceIntent
    data object ExportZip : WorkspaceIntent
    data object CloseImagePreview : WorkspaceIntent
    /** Replaces [start, end) of the active file in the editor (undoable). */
    data class ReplaceText(val start: Int, val end: Int, val text: String) : WorkspaceIntent
    /** Opens rename for the selection or the word at the cursor. */
    data object StartRenameSymbol : WorkspaceIntent
    data class RenameSymbol(val newName: String) : WorkspaceIntent
    data object CancelRenameSymbol : WorkspaceIntent
    data object Build : WorkspaceIntent
    data object CancelBuild : WorkspaceIntent
    data class SetVariant(val variant: BuildVariant) : WorkspaceIntent
    data class JumpToDiagnostic(val diagnostic: CompilerDiagnostic) : WorkspaceIntent
    data class GoToLine(val line: Int, val column: Int = 1) : WorkspaceIntent
    data class ShowDock(val tab: DockTab) : WorkspaceIntent
    data object HideDock : WorkspaceIntent
    data class SetPalette(val open: Boolean, val query: String = "") : WorkspaceIntent
    data class SetPreferences(val open: Boolean) : WorkspaceIntent
    data class Preference(val change: PreferenceChange) : WorkspaceIntent
    data class Key(val key: ExtraKey) : WorkspaceIntent
    data class ApplyPatch(val patch: FilePatch) : WorkspaceIntent
    /** Puts back the text a file had before the last applied AI diff. */
    data object UndoPatch : WorkspaceIntent
    data class InsertCode(val code: String) : WorkspaceIntent
    data object InstallOutput : WorkspaceIntent
    data object ShareOutput : WorkspaceIntent
    data object RefreshToolchain : WorkspaceIntent
}

/** MVI state holder for the IDE workspace: files, tabs, find/search, the build pipeline and dock. */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val tabDao: OpenTabDao,
    private val orchestrator: BuildOrchestrator,
    private val toolchain: BuildToolchain,
    private val preferencesRepository: EditorPreferencesRepository,
    private val git: GitService,
    private val buildNotifier: BuildNotifier,
    private val liveJava: LiveJavaChecker,
) : ViewModel() {

    /** Problems from typing-time Java checks, by canonical file path; replaced by a build's own. */
    private val liveDiagnostics = MutableStateFlow<Map<String, List<CompilerDiagnostic>>>(emptyMap())
    private var liveJob: Job? = null

    private val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    val preferences: StateFlow<EditorPreferences> =
        preferencesRepository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, EditorPreferences())

    private val _commands = MutableSharedFlow<EditorCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<EditorCommand> = _commands.asSharedFlow()

    private val _effects = MutableSharedFlow<WorkspaceEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<WorkspaceEffect> = _effects.asSharedFlow()

    /** Called by the AI dock to receive the latest editor and build context. */
    private val _aiContext = MutableStateFlow(AiContextSnapshot())
    val aiContext: StateFlow<AiContextSnapshot> = _aiContext.asStateFlow()

    private val versions = AtomicLong(1)
    private val logIds = AtomicLong(1)
    private var buildJob: Job? = null
    private var searchJob: Job? = null
    private var autoSaveJob: Job? = null
    private var persistJob: Job? = null

    fun openProject(path: String) {
        val dir = File(path)
        if (_state.value.projectDir == dir) return
        viewModelScope.launch {
            projects.touch(path)
            val name = projects.find(path)?.name ?: dir.name
            _state.value = WorkspaceState(projectDir = dir, projectName = name, toolchain = toolchain.status())
            refreshTree()
            refreshGit()
            restoreTabs(dir)
            launch { loadBuildHistory(dir) }
            publishAiContext()
        }
    }

    fun onIntent(intent: WorkspaceIntent) {
        when (intent) {
            is WorkspaceIntent.ToggleDirectory -> {
                _state.update { s ->
                    val expanded = if (intent.relativePath in s.expanded) s.expanded - intent.relativePath else s.expanded + intent.relativePath
                    s.copy(expanded = expanded)
                }
                refreshTree()
            }
            WorkspaceIntent.CollapseAll -> {
                _state.update { it.copy(expanded = setOf("")) }
                refreshTree()
            }
            is WorkspaceIntent.OpenFile -> openFile(intent.relativePath, intent.line, intent.column)
            is WorkspaceIntent.SelectTab -> selectTab(intent.index)
            is WorkspaceIntent.CloseTab -> closeTab(intent.index)
            is WorkspaceIntent.CloseOtherTabs -> closeOthers(intent.index)
            is WorkspaceIntent.TextChanged -> onTextChanged(intent.text)
            is WorkspaceIntent.CursorChanged -> {
                _state.update { s ->
                    val tabs = s.tabs.mapIndexed { i, t -> if (i == s.activeIndex) t.copy(cursorLine = intent.cursor.line, cursorColumn = intent.cursor.column) else t }
                    s.copy(cursor = intent.cursor, tabs = tabs)
                }
                publishAiContext()
            }
            WorkspaceIntent.Save -> viewModelScope.launch { saveActive() }
            WorkspaceIntent.SaveAll -> viewModelScope.launch { saveAll() }
            is WorkspaceIntent.NewFile -> fileOp {
                val target = resolve(intent.parentRelative, intent.name)
                if (target.exists()) error("${intent.name} already exists")
                target.parentFile?.mkdirs()
                target.writeText(starterContent(target))
                expandParents(target)
                refreshTree()
                openFile(relative(target))
                "Created ${intent.name}"
            }
            is WorkspaceIntent.NewFromTemplate -> fileOp {
                val dir = projectDir()
                val namespace = listOf("app/build.gradle.kts", "app/build.gradle").map { File(dir, it) }.firstOrNull { it.isFile }
                    ?.readText()?.let { Regex("""namespace\s*=?\s*["']([^"']+)["']""").find(it)?.groupValues?.get(1) }
                val out = FileTemplates.generate(intent.kind, intent.parentRelative, intent.name, namespace)
                val targets = out.files.mapKeys { (rel, _) -> File(dir, rel).normalize() }
                targets.keys.forEach { f ->
                    if (!f.path.startsWith(dir.normalize().path)) error("Path escapes the project")
                    if (f.exists()) error("${f.name} already exists")
                }
                targets.forEach { (f, text) -> f.parentFile?.mkdirs(); f.writeText(text) }
                out.activityToRegister?.let { activity ->
                    val manifest = File(dir, "app/src/main/AndroidManifest.xml")
                    if (manifest.isFile) manifest.writeText(FileTemplates.registerActivity(manifest.readText(), activity, namespace))
                }
                val first = targets.keys.first()
                expandParents(first)
                refreshTree()
                openFile(relative(first))
                "Created " + targets.keys.joinToString { it.name } + (out.activityToRegister?.let { " and registered it in the manifest" } ?: "")
            }
            is WorkspaceIntent.Move -> fileOp {
                val source = File(projectDir(), intent.relativePath)
                val targetDir = (if (intent.targetDirRelative.isBlank()) projectDir() else File(projectDir(), intent.targetDirRelative.trim('/'))).normalize()
                if (!targetDir.path.startsWith(projectDir().normalize().path)) error("Path escapes the project")
                if (source.isDirectory && targetDir.path.startsWith(source.normalize().path)) error("Cannot move a folder into itself")
                val target = File(targetDir, source.name)
                if (target.exists()) error("${source.name} already exists there")
                targetDir.mkdirs()
                if (!source.renameTo(target)) error("Move failed")
                if (target.isFile) runCatching { LocalHistory.moved(projectDir(), source, target) }
                val oldPrefix = intent.relativePath
                val newRel = relative(target)
                _state.update { s ->
                    s.copy(tabs = s.tabs.map { t ->
                        when {
                            t.relativePath == oldPrefix -> t.copy(relativePath = newRel, file = target)
                            t.relativePath.startsWith("$oldPrefix/") -> {
                                val rel = newRel + t.relativePath.removePrefix(oldPrefix)
                                t.copy(relativePath = rel, file = File(projectDir(), rel))
                            }
                            else -> t
                        }
                    })
                }
                expandParents(target)
                refreshTree()
                persistTabs()
                "Moved ${source.name} to /${relative(targetDir)}" + if (source.extension in setOf("kt", "java")) ". Update its package line if the folder changed." else ""
            }
            is WorkspaceIntent.NewFolder -> fileOp {
                val target = resolve(intent.parentRelative, intent.name)
                if (!target.mkdirs()) error("Could not create ${intent.name}")
                expandParents(target)
                refreshTree()
                "Created folder ${intent.name}"
            }
            is WorkspaceIntent.Rename -> fileOp {
                val source = File(projectDir(), intent.relativePath)
                val target = File(source.parentFile, intent.newName)
                validateName(intent.newName)
                if (target.exists()) error("${intent.newName} already exists")
                if (!source.renameTo(target)) error("Rename failed")
                if (target.isFile) runCatching { LocalHistory.moved(projectDir(), source, target) }
                val oldPrefix = intent.relativePath
                val newRel = relative(target)
                _state.update { s ->
                    s.copy(tabs = s.tabs.map { t ->
                        when {
                            t.relativePath == oldPrefix -> t.copy(relativePath = newRel, file = target)
                            t.relativePath.startsWith("$oldPrefix/") -> {
                                val rel = newRel + t.relativePath.removePrefix(oldPrefix)
                                t.copy(relativePath = rel, file = File(projectDir(), rel))
                            }
                            else -> t
                        }
                    })
                }
                refreshTree()
                persistTabs()
                "Renamed to ${intent.newName}"
            }
            is WorkspaceIntent.Delete -> fileOp {
                val target = File(projectDir(), intent.relativePath)
                if (!target.deleteRecursively()) error("Delete failed")
                _state.update { s ->
                    val remaining = s.tabs.filterNot { it.relativePath == intent.relativePath || it.relativePath.startsWith(intent.relativePath + "/") }
                    s.copy(tabs = remaining, activeIndex = if (remaining.isEmpty()) -1 else s.activeIndex.coerceIn(0, remaining.lastIndex))
                }
                refreshTree()
                persistTabs()
                "Deleted ${target.name}"
            }
            WorkspaceIntent.RefreshTree -> { refreshTree(); refreshGit() }
            WorkspaceIntent.OpenFind -> {
                val selection = _state.value.cursor.selectedText.takeIf { it.isNotEmpty() && !it.contains('\n') }
                _state.update { it.copy(find = it.find.copy(visible = true, query = selection ?: it.find.query)) }
                recomputeMatches()
            }
            is WorkspaceIntent.Find -> onFind(intent.action)
            is WorkspaceIntent.SearchQuery -> { _state.update { it.copy(search = it.search.copy(query = intent.query)) }; runSearch() }
            is WorkspaceIntent.SearchGlobs -> { _state.update { it.copy(search = it.search.copy(includeGlobs = intent.globs)) }; runSearch() }
            is WorkspaceIntent.SearchOptions -> { _state.update { it.copy(search = it.search.copy(options = intent.options)) }; runSearch() }
            is WorkspaceIntent.OpenSearchHit -> openFile(relative(intent.file), intent.hit.line, intent.hit.column)
            is WorkspaceIntent.SearchReplacement -> _state.update { it.copy(search = it.search.copy(replacement = intent.text)) }
            WorkspaceIntent.ToggleSearchReplace -> _state.update { it.copy(search = it.search.copy(replaceOpen = !it.search.replaceOpen)) }
            is WorkspaceIntent.ToggleReplaceExcluded -> _state.update { st ->
                val ex = st.search.excluded
                st.copy(search = st.search.copy(excluded = if (intent.relativePath in ex) ex - intent.relativePath else ex + intent.relativePath))
            }
            WorkspaceIntent.ReplaceInProject -> replaceInProject()
            is WorkspaceIntent.ShowHistory -> viewModelScope.launch {
                val dir = _state.value.projectDir ?: return@launch
                val versions = withContext(Dispatchers.IO) { LocalHistory.versions(dir, File(dir, intent.relativePath)) }
                _state.update { it.copy(history = FileHistoryUi(intent.relativePath, versions)) }
                versions.firstOrNull()?.let { onIntent(WorkspaceIntent.SelectHistoryVersion(it)) }
            }
            is WorkspaceIntent.SelectHistoryVersion -> viewModelScope.launch {
                val h = _state.value.history ?: return@launch
                val version = intent.version
                if (version == null) { _state.update { it.copy(history = h.copy(selected = null, diff = "")) }; return@launch }
                val current = currentText(h.relativePath)
                val diff = withContext(Dispatchers.Default) {
                    val old = runCatching { version.read() }.getOrDefault("")
                    LineDiff.unified(h.relativePath, current, old).ifEmpty { "Same as the current text" }
                }
                _state.update { st -> st.history?.let { st.copy(history = it.copy(selected = version, diff = diff)) } ?: st }
            }
            is WorkspaceIntent.RestoreHistoryVersion -> viewModelScope.launch {
                val h = _state.value.history ?: return@launch
                val text = withContext(Dispatchers.IO) { runCatching { intent.version.read() }.getOrNull() } ?: return@launch emit("That version can no longer be read")
                val stamp = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(intent.version.time))
                val open = _state.value.tabs.indexOfFirst { it.relativePath == h.relativePath }
                if (open >= 0) {
                    // Into the editor as an unsaved change, so Undo and "don't save" still work.
                    _state.update { st ->
                        st.copy(
                            history = null,
                            activeIndex = open,
                            tabs = st.tabs.mapIndexed { i, t -> if (i == open) t.copy(content = text, version = versions.getAndIncrement()) else t },
                        )
                    }
                    recomputeMatches()
                    publishAiContext()
                    emit("Restored the version from $stamp. Save to keep it.")
                } else {
                    val file = File(projectDir(), h.relativePath)
                    withContext(Dispatchers.IO) { recordHistory(file); file.parentFile?.mkdirs(); file.writeText(text) }
                    _state.update { it.copy(history = null) }
                    openFile(h.relativePath)
                    refreshGit()
                    emit("Restored the version from $stamp")
                }
            }
            WorkspaceIntent.CloseHistory -> _state.update { it.copy(history = null) }
            WorkspaceIntent.StartRenameSymbol -> {
                val dir = _state.value.projectDir ?: return
                val tab = _state.value.activeFile ?: return emit("Open a file and put the cursor on a name")
                val symbol = _state.value.cursor.selectedText.trim().takeIf { identifier.matches(it) }
                    ?: wordAt(tab.content, _state.value.cursor.line, _state.value.cursor.column)
                    ?: return emit("Put the cursor on a class, function or variable name")
                _state.update { it.copy(rename = RenameSymbolUi(symbol)) }
                viewModelScope.launch {
                    val uses = ProjectSearchEngine.search(dir, symbol, renameOptions, RENAME_GLOBS, maxHits = 20_000)
                    _state.update { st -> st.rename?.takeIf { it.symbol == symbol }?.let { st.copy(rename = it.copy(uses = uses, searching = false)) } ?: st }
                }
            }
            WorkspaceIntent.CancelRenameSymbol -> _state.update { it.copy(rename = null) }
            is WorkspaceIntent.RenameSymbol -> {
                val rename = _state.value.rename ?: return
                val uses = rename.uses ?: return
                val newName = intent.newName.trim()
                if (!identifier.matches(newName)) return emit("\"$newName\" is not a valid name")
                _state.update { it.copy(rename = null) }
                viewModelScope.launch {
                    val (total, files) = replaceEverywhere(uses, emptySet(), rename.symbol, newName, renameOptions)
                    // class Foo in Foo.kt: rename the file too, as Android Studio does.
                    val moved = rename.fileToRename?.let { rel ->
                        val source = File(projectDir(), rel)
                        val target = File(source.parentFile, newName + "." + source.extension)
                        if (target.exists() || !withContext(Dispatchers.IO) { source.renameTo(target) }) return@let null
                        runCatching { LocalHistory.moved(projectDir(), source, target) }
                        val newRel = relative(target)
                        _state.update { st -> st.copy(tabs = st.tabs.map { t -> if (t.relativePath == rel) t.copy(relativePath = newRel, file = target) else t }) }
                        persistTabs()
                        target.name
                    }
                    refreshTree()
                    emit("Renamed ${rename.symbol} to $newName: $total use(s) in $files file(s)" + (moved?.let { ", file renamed to $it" } ?: ""))
                }
            }
            WorkspaceIntent.CloseImagePreview -> _state.update { it.copy(imagePreview = null) }
            is WorkspaceIntent.ReplaceText -> _commands.tryEmit(EditorCommand.ReplaceRange(intent.start, intent.end, intent.text))
            WorkspaceIntent.ExportZip -> viewModelScope.launch {
                val dir = _state.value.projectDir ?: return@launch
                saveAll()
                emit("Packing ${dir.name}…")
                runCatching { projects.exportZip(dir) }
                    .onSuccess { _effects.tryEmit(WorkspaceEffect.ShareFile(it, "application/zip")) }
                    .onFailure { emit("Could not create the ZIP: ${it.message}") }
            }
            WorkspaceIntent.FormatDocument -> {
                val tab = _state.value.activeFile ?: return
                val language = CodeFormatter.languageFor(tab.name) ?: return emit("Codein cannot format ${tab.name.substringAfterLast('.')} files")
                viewModelScope.launch {
                    val formatted = withContext(Dispatchers.Default) { CodeFormatter.format(tab.content, language, preferences.value.tabSize) }
                    if (formatted == tab.content) emit("${tab.name} is already formatted")
                    else { _commands.tryEmit(EditorCommand.ReplaceAllText(formatted)); emit("Formatted ${tab.name}") }
                }
            }
            WorkspaceIntent.Build -> startBuild()
            WorkspaceIntent.CancelBuild -> buildJob?.cancel()
            is WorkspaceIntent.SetVariant -> _state.update { it.copy(build = it.build.copy(variant = intent.variant)) }
            is WorkspaceIntent.JumpToDiagnostic -> jumpTo(intent.diagnostic)
            is WorkspaceIntent.GoToLine -> _commands.tryEmit(EditorCommand.GoTo(intent.line, intent.column))
            is WorkspaceIntent.ShowDock -> _state.update { it.copy(dockVisible = true, dockTab = intent.tab) }
            WorkspaceIntent.HideDock -> _state.update { it.copy(dockVisible = false) }
            is WorkspaceIntent.SetPalette -> _state.update { it.copy(paletteOpen = intent.open, paletteQuery = intent.query) }
            is WorkspaceIntent.SetPreferences -> _state.update { it.copy(preferencesOpen = intent.open) }
            is WorkspaceIntent.Preference -> viewModelScope.launch { applyPreference(intent.change) }
            is WorkspaceIntent.Key -> onExtraKey(intent.key)
            is WorkspaceIntent.ApplyPatch -> applyPatch(intent.patch)
            WorkspaceIntent.UndoPatch -> undoPatch()
            is WorkspaceIntent.InsertCode -> {
                if (_state.value.activeFile == null) emit("Open a file first to insert code")
                else _commands.tryEmit(EditorCommand.Insert(intent.code))
            }
            WorkspaceIntent.InstallOutput -> (_state.value.build.result as? BuildResult.Success)?.let { emitOutput(it, install = true) }
            WorkspaceIntent.ShareOutput -> (_state.value.build.result as? BuildResult.Success)?.let { emitOutput(it, install = false) }
            WorkspaceIntent.RefreshToolchain -> _state.update { it.copy(toolchain = toolchain.status()) }
        }
    }

    // ------------------------------------------------------------------ files & tabs

    private fun projectDir(): File = _state.value.projectDir ?: error("No project open")

    private fun relative(file: File): String = file.relativeTo(projectDir()).invariantSeparatorsPath

    private fun resolve(parentRelative: String, name: String): File {
        validateName(name)
        val parent = if (parentRelative.isEmpty()) projectDir() else File(projectDir(), parentRelative)
        val target = File(parent, name).canonicalFile
        if (!target.path.startsWith(projectDir().canonicalPath)) error("Path escapes the project")
        return target
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name.contains('\u0000') || name == "." || name == "..") error("Invalid name")
    }

    private fun expandParents(target: File) {
        val rels = mutableSetOf<String>()
        var parent = target.parentFile
        val root = projectDir()
        while (parent != null && parent != root && parent.path.startsWith(root.path)) {
            rels += parent.relativeTo(root).invariantSeparatorsPath
            parent = parent.parentFile
        }
        _state.update { it.copy(expanded = it.expanded + rels) }
    }

    private fun refreshTree() {
        val dir = _state.value.projectDir ?: return
        viewModelScope.launch {
            val expanded = _state.value.expanded
            val (rows, gradle) = withContext(Dispatchers.IO) {
                val r = FileTreeBuilder.build(dir, expanded)
                val g = dir.walkTopDown().maxDepth(3)
                    .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
                    .filter { it.isFile && (it.name.endsWith(".gradle") || it.name.endsWith(".gradle.kts") || it.name == "libs.versions.toml") }
                    .map { it.relativeTo(dir).invariantSeparatorsPath }.sorted().toList()
                r to g
            }
            _state.update { it.copy(tree = rows, gradleFiles = gradle) }
        }
    }

    private fun refreshGit() {
        val dir = _state.value.projectDir ?: return
        viewModelScope.launch {
            val status = runCatching { git.status(dir) }.getOrNull()
            _state.update { it.copy(gitStatus = status?.files.orEmpty(), branch = status?.branch) }
        }
    }

    private fun openFile(relativePath: String, line: Int? = null, column: Int? = null) {
        val s = _state.value
        val existing = s.tabs.indexOfFirst { it.relativePath == relativePath }
        if (existing >= 0) {
            selectTab(existing)
            if (line != null) _commands.tryEmit(EditorCommand.GoTo(line, column ?: 1))
            return
        }
        if (com.codein.ide.preview.isImageFile(relativePath)) {
            _state.update { it.copy(imagePreview = File(projectDir(), relativePath)) }
            return
        }
        viewModelScope.launch {
            val file = File(projectDir(), relativePath)
            val content = withContext(Dispatchers.IO) {
                when {
                    !file.isFile -> null
                    file.length() > MAX_OPEN_BYTES -> null
                    else -> runCatching { file.readText() }.getOrNull()?.takeIf { it.indexOf('\u0000') < 0 }
                }
            }
            if (content == null) {
                emit("${file.name} is binary or too large to edit")
                return@launch
            }
            val tab = OpenFile(relativePath, file, content, content, versions.getAndIncrement())
            _state.update { st ->
                val tabs = st.tabs + tab
                st.copy(tabs = tabs, activeIndex = tabs.lastIndex, find = st.find.copy(matches = emptyList(), currentIndex = -1))
            }
            if (line != null) {
                delay(EDITOR_SETTLE_MS)
                _commands.tryEmit(EditorCommand.GoTo(line, column ?: 1))
            }
            recomputeMatches()
            persistTabs()
            publishAiContext()
        }
    }

    private fun selectTab(index: Int) {
        val s = _state.value
        if (index !in s.tabs.indices || index == s.activeIndex) return
        // Re-key the document so the editor reloads the tab's current (possibly unsaved) text.
        _state.update { st ->
            st.copy(
                tabs = st.tabs.mapIndexed { i, t -> if (i == index) t.copy(version = versions.getAndIncrement()) else t },
                activeIndex = index,
            )
        }
        val tab = _state.value.tabs[index]
        viewModelScope.launch {
            delay(EDITOR_SETTLE_MS)
            _commands.tryEmit(EditorCommand.GoTo(tab.cursorLine + 1, tab.cursorColumn + 1))
        }
        recomputeMatches()
        persistTabs()
        publishAiContext()
    }

    private fun closeTab(index: Int) {
        val s = _state.value
        val tab = s.tabs.getOrNull(index) ?: return
        if (tab.isDirty && preferences.value.autoSave) viewModelScope.launch { write(tab) }
        val tabs = s.tabs.toMutableList().also { it.removeAt(index) }
        val newActive = when {
            tabs.isEmpty() -> -1
            index < s.activeIndex -> s.activeIndex - 1
            index == s.activeIndex -> index.coerceAtMost(tabs.lastIndex)
            else -> s.activeIndex
        }
        _state.update {
            it.copy(
                tabs = tabs.mapIndexed { i, t -> if (i == newActive && index == s.activeIndex) t.copy(version = versions.getAndIncrement()) else t },
                activeIndex = newActive,
            )
        }
        persistTabs()
        publishAiContext()
    }

    private fun closeOthers(index: Int) {
        val keep = _state.value.tabs.getOrNull(index) ?: return
        _state.update { it.copy(tabs = listOf(keep.copy(version = versions.getAndIncrement())), activeIndex = 0) }
        persistTabs()
    }

    private fun onTextChanged(text: String) {
        val s = _state.value
        val active = s.activeFile ?: return
        if (active.content == text) return
        _state.update { st -> st.copy(tabs = st.tabs.mapIndexed { i, t -> if (i == st.activeIndex) t.copy(content = text) else t }) }
        if (s.find.visible) recomputeMatches()
        if (active.file.extension == "java") scheduleLiveCheck(active.file, text)
        if (preferences.value.autoSave) {
            autoSaveJob?.cancel()
            autoSaveJob = viewModelScope.launch {
                delay(AUTO_SAVE_DELAY_MS)
                saveActive(quiet = true)
            }
        }
        publishAiContext()
    }

    private suspend fun saveActive(quiet: Boolean = false) {
        val tab = _state.value.activeFile ?: return
        if (!tab.isDirty) return
        if (write(tab) && !quiet) emit("Saved ${tab.name}")
    }

    private suspend fun saveAll(): Boolean {
        var ok = true
        _state.value.tabs.filter { it.isDirty }.forEach { ok = write(it) && ok }
        return ok
    }

    /** The open tab's text of [relativePath], or the file's text on disk. */
    private suspend fun currentText(relativePath: String): String =
        _state.value.tabs.firstOrNull { it.relativePath == relativePath }?.content
            ?: withContext(Dispatchers.IO) { runCatching { File(projectDir(), relativePath).readText() }.getOrDefault("") }

    /** Keeps the on-disk text of [file] in local history before Codein overwrites it. */
    private fun recordHistory(file: File) {
        val dir = _state.value.projectDir ?: return
        runCatching { LocalHistory.record(dir, file) }
    }

    private suspend fun write(tab: OpenFile): Boolean {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                recordHistory(tab.file)
                val temp = File(tab.file.parentFile, ".${tab.file.name}.codein-tmp")
                temp.writeText(tab.content)
                if (!temp.renameTo(tab.file)) {
                    tab.file.writeText(tab.content)
                    temp.delete()
                }
            }
        }
        return result.fold(
            onSuccess = {
                _state.update { st -> st.copy(tabs = st.tabs.map { if (it.relativePath == tab.relativePath) it.copy(savedContent = tab.content) else it }) }
                refreshGit()
                true
            },
            onFailure = { emit("Could not save ${tab.name}: ${it.message}"); false },
        )
    }

    private suspend fun restoreTabs(dir: File) {
        val saved = tabDao.tabsFor(dir.absolutePath)
        if (saved.isEmpty()) {
            val main = withContext(Dispatchers.IO) {
                dir.walkTopDown().onEnter { it.name != "build" && it.name != ".git" }
                    .firstOrNull { it.isFile && (it.name == "MainActivity.kt" || it.name == "MainActivity.java") }
            }
            main?.let {
                expandParents(it)
                refreshTree()
                openFile(relative(it))
            }
            return
        }
        val tabs = withContext(Dispatchers.IO) {
            saved.mapNotNull { e ->
                val file = File(dir, e.relativePath)
                val text = if (file.isFile && file.length() <= MAX_OPEN_BYTES) runCatching { file.readText() }.getOrNull() else null
                text?.let { OpenFile(e.relativePath, file, it, it, versions.getAndIncrement(), e.cursorLine, e.cursorColumn) }
            }
        }
        val activeRel = saved.firstOrNull { it.active }?.relativePath
        _state.update { it.copy(tabs = tabs, activeIndex = tabs.indexOfFirst { t -> t.relativePath == activeRel }.takeIf { i -> i >= 0 } ?: (if (tabs.isEmpty()) -1 else 0)) }
        _state.value.activeFile?.let { tab ->
            delay(EDITOR_SETTLE_MS)
            _commands.tryEmit(EditorCommand.GoTo(tab.cursorLine + 1, tab.cursorColumn + 1))
        }
    }

    private fun persistTabs() {
        val dir = _state.value.projectDir ?: return
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(300)
            val s = _state.value
            tabDao.clear(dir.absolutePath)
            tabDao.insertAll(s.tabs.mapIndexed { i, t -> OpenTabEntity(dir.absolutePath, t.relativePath, i, i == s.activeIndex, t.cursorLine, t.cursorColumn) })
        }
    }

    private fun fileOp(block: suspend () -> String) {
        viewModelScope.launch {
            val message = runCatching { withContext(Dispatchers.IO) { block() } }.getOrElse { it.message ?: "Operation failed" }
            emit(message)
            refreshGit()
        }
    }

    private fun starterContent(file: File): String {
        val rel = relative(file)
        val pkg = Regex("""src/\w+/(?:java|kotlin)/(.+)/[^/]+$""").find(rel)?.groupValues?.get(1)?.replace('/', '.')
        val className = file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_]"), "")
        return when (file.extension) {
            "kt" -> (pkg?.let { "package $it\n\n" } ?: "") + "class $className {\n}\n"
            "java" -> (pkg?.let { "package $it;\n\n" } ?: "") + "public class $className {\n}\n"
            "xml" -> if (rel.contains("/res/layout")) {
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n\n</LinearLayout>\n"
            } else "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"
            else -> ""
        }
    }

    // ------------------------------------------------------------------ find & search

    private fun onFind(action: FindBarAction) {
        val find = _state.value.find
        when (action) {
            is FindBarAction.Query -> { _state.update { it.copy(find = it.find.copy(query = action.text)) }; recomputeMatches(select = true) }
            is FindBarAction.Replacement -> _state.update { it.copy(find = it.find.copy(replacement = action.text)) }
            FindBarAction.ToggleRegex -> { _state.update { it.copy(find = it.find.copy(options = it.find.options.copy(regex = !it.find.options.regex))) }; recomputeMatches() }
            FindBarAction.ToggleCase -> { _state.update { it.copy(find = it.find.copy(options = it.find.options.copy(matchCase = !it.find.options.matchCase))) }; recomputeMatches() }
            FindBarAction.ToggleWholeWord -> { _state.update { it.copy(find = it.find.copy(options = it.find.options.copy(wholeWord = !it.find.options.wholeWord))) }; recomputeMatches() }
            FindBarAction.ToggleReplace -> _state.update { it.copy(find = it.find.copy(showReplace = !it.find.showReplace)) }
            FindBarAction.Next -> moveMatch(+1)
            FindBarAction.Previous -> moveMatch(-1)
            FindBarAction.ReplaceOne -> {
                val active = _state.value.activeFile ?: return
                val match = find.matches.getOrNull(find.currentIndex) ?: return
                val replacement = FindReplaceEngine.replacementFor(active.content, match, find.query, find.replacement, find.options)
                _commands.tryEmit(EditorCommand.ReplaceRange(match.start, match.end, replacement))
            }
            FindBarAction.ReplaceAll -> {
                val active = _state.value.activeFile ?: return
                val (text, count) = FindReplaceEngine.replaceAll(active.content, find.query, find.replacement, find.options)
                if (count > 0) {
                    replaceActiveText(text)
                    emit("Replaced $count occurrence(s)")
                }
            }
            FindBarAction.Close -> _state.update { it.copy(find = it.find.copy(visible = false, matches = emptyList(), currentIndex = -1)) }
        }
    }

    private fun recomputeMatches(select: Boolean = false) {
        val s = _state.value
        val active = s.activeFile
        if (!s.find.visible || active == null || s.find.query.isEmpty()) {
            _state.update { it.copy(find = it.find.copy(matches = emptyList(), currentIndex = -1)) }
            return
        }
        val matches = FindReplaceEngine.findAll(active.content, s.find.query, s.find.options)
        val cursorOffset = s.cursor.selectionStart
        val index = if (matches.isEmpty()) -1 else matches.indexOfFirst { it.start >= cursorOffset }.let { if (it < 0) 0 else it }
        _state.update { it.copy(find = it.find.copy(matches = matches, currentIndex = index)) }
        if (select && index >= 0) _commands.tryEmit(EditorCommand.Select(matches[index].start, matches[index].end))
    }

    private fun moveMatch(delta: Int) {
        val find = _state.value.find
        if (find.matches.isEmpty()) return
        val next = ((find.currentIndex + delta) % find.matches.size + find.matches.size) % find.matches.size
        _state.update { it.copy(find = it.find.copy(currentIndex = next)) }
        val m = find.matches[next]
        _commands.tryEmit(EditorCommand.Select(m.start, m.end))
    }

    /**
     * Replaces the search matches in every included file and saves them. Open tabs are replaced in
     * their current (possibly unsaved) text so typing is not lost; each file's old text goes to
     * local history first.
     */
    private fun replaceInProject() {
        val search = _state.value.search
        val result = search.result ?: return
        viewModelScope.launch {
            val (total, files) = replaceEverywhere(result, search.excluded, search.query, search.replacement, search.options)
            emit("Replaced $total match(es) in $files file(s)")
            runSearch()
        }
    }

    /** Writes the replacement into every file of [result] (open tabs included); returns matches and files changed. */
    private suspend fun replaceEverywhere(
        result: com.codein.ide.editor.search.ProjectSearchResult,
        excluded: Set<String>,
        query: String,
        replacement: String,
        options: com.codein.ide.editor.search.FindOptions,
    ): Pair<Int, Int> {
        val tabsByPath = _state.value.tabs.associateBy { it.file.absolutePath }
        val (plan, total) = withContext(Dispatchers.IO) {
            ProjectReplace.plan(result.files, excluded, query, replacement, options) { f ->
                tabsByPath[f.file.absolutePath]?.content ?: runCatching { f.file.readText() }.getOrNull()
            }
        }
        val written = withContext(Dispatchers.IO) {
            plan.mapNotNull { (f, text) ->
                runCatching {
                    recordHistory(f.file)
                    f.file.writeText(text)
                    f.file.absolutePath to text
                }.getOrNull()
            }.toMap()
        }
        _state.update { st ->
            st.copy(tabs = st.tabs.map { t ->
                written[t.file.absolutePath]?.let { text -> t.copy(content = text, savedContent = text, version = versions.getAndIncrement()) } ?: t
            })
        }
        recomputeMatches()
        publishAiContext()
        refreshGit()
        return total to written.size
    }

    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val renameOptions = com.codein.ide.editor.search.FindOptions(regex = false, matchCase = true, wholeWord = true)

    /** The identifier at 0-based [line]/[column] of [text], or null. */
    private fun wordAt(text: String, line: Int, column: Int): String? {
        val lineText = text.lineSequence().drop(line).firstOrNull() ?: return null
        if (lineText.isEmpty()) return null
        var start = column.coerceIn(0, lineText.length)
        var end = start
        fun part(c: Char) = c.isLetterOrDigit() || c == '_'
        while (start > 0 && part(lineText[start - 1])) start--
        while (end < lineText.length && part(lineText[end])) end++
        return lineText.substring(start, end).takeIf { identifier.matches(it) }
    }

    private fun runSearch() {
        val dir = _state.value.projectDir ?: return
        searchJob?.cancel()
        val search = _state.value.search
        if (search.query.isEmpty()) {
            _state.update { it.copy(search = it.search.copy(result = null, searching = false)) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(search = it.search.copy(searching = true)) }
            val result = ProjectSearchEngine.search(dir, search.query, search.options, search.includeGlobs)
            _state.update { it.copy(search = it.search.copy(searching = false, result = result)) }
        }
    }

    // ------------------------------------------------------------------ editor helpers

    private fun replaceActiveText(text: String) {
        _state.update { st ->
            st.copy(tabs = st.tabs.mapIndexed { i, t -> if (i == st.activeIndex) t.copy(content = text, version = versions.getAndIncrement()) else t })
        }
        recomputeMatches()
        publishAiContext()
    }

    private fun onExtraKey(key: ExtraKey) {
        when (key) {
            ExtraKey.Tab -> _commands.tryEmit(EditorCommand.InsertTab(preferences.value.tabSize))
            is ExtraKey.Symbol -> _commands.tryEmit(EditorCommand.Insert(key.text, key.caretBack))
            ExtraKey.Undo -> _commands.tryEmit(EditorCommand.Undo)
            ExtraKey.Redo -> _commands.tryEmit(EditorCommand.Redo)
            ExtraKey.ToggleComment -> _commands.tryEmit(EditorCommand.ToggleComment)
            ExtraKey.Find -> onIntent(WorkspaceIntent.OpenFind)
            ExtraKey.AiFix -> {
                _state.update { it.copy(dockVisible = true, dockTab = DockTab.Ai) }
                _effects.tryEmit(WorkspaceEffect.OpenAiDock)
            }
        }
    }

    /** File and text before the last AI diff; [existed] is false when the diff created the file. */
    private data class PatchUndo(val relativePath: String, val text: String, val existed: Boolean)
    private var lastPatchUndo: PatchUndo? = null

    private fun undoPatch() {
        val undo = lastPatchUndo ?: return emit("Nothing to undo")
        lastPatchUndo = null
        viewModelScope.launch {
            val file = File(projectDir(), undo.relativePath)
            val openIndex = _state.value.tabs.indexOfFirst { it.relativePath == undo.relativePath }
            if (!undo.existed) {
                if (openIndex >= 0) closeTabQuietly(openIndex)
                withContext(Dispatchers.IO) { file.delete() }
                refreshTree()
                emit("Removed ${file.name}, which the AI diff had created")
            } else if (openIndex >= 0) {
                _state.update { st ->
                    st.copy(tabs = st.tabs.mapIndexed { i, t -> if (i == openIndex) t.copy(content = undo.text, version = versions.getAndIncrement()) else t })
                }
                emit("Undid the AI change to ${file.name}")
            } else {
                withContext(Dispatchers.IO) { file.writeText(undo.text) }
                emit("Undid the AI change to ${file.name}")
            }
            recomputeMatches()
            publishAiContext()
            refreshGit()
        }
    }

    private fun closeTabQuietly(index: Int) {
        _state.update { st ->
            val tabs = st.tabs.filterIndexed { i, _ -> i != index }
            st.copy(tabs = tabs, activeIndex = if (tabs.isEmpty()) -1 else st.activeIndex.coerceAtMost(tabs.lastIndex))
        }
        persistTabs()
    }

    private fun applyPatch(patch: FilePatch) {
        viewModelScope.launch {
            val s = _state.value
            val dir = s.projectDir ?: return@launch
            val targetRel = patch.targetPath?.removePrefix("./")?.let { path -> resolvePatchPath(dir, path) } ?: s.activeFile?.relativePath
            if (targetRel == null) {
                emit("The diff does not name a file and no file is open")
                return@launch
            }
            val openIndex = s.tabs.indexOfFirst { it.relativePath == targetRel }
            val file = File(dir, targetRel)
            val original = if (openIndex >= 0) s.tabs[openIndex].content else withContext(Dispatchers.IO) { if (file.isFile) file.readText() else "" }
            when (val result = UnifiedDiff.apply(original, patch)) {
                is PatchResult.Failed -> emit("Could not apply diff to ${file.name}: ${result.reason}")
                is PatchResult.Applied -> {
                    lastPatchUndo = PatchUndo(targetRel, original, existed = openIndex >= 0 || file.isFile)
                    if (openIndex >= 0) {
                        _state.update { st ->
                            st.copy(tabs = st.tabs.mapIndexed { i, t -> if (i == openIndex) t.copy(content = result.text, version = versions.getAndIncrement()) else t }, activeIndex = openIndex)
                        }
                    } else {
                        withContext(Dispatchers.IO) { recordHistory(file); file.parentFile?.mkdirs(); file.writeText(result.text) }
                        refreshTree()
                        openFile(targetRel)
                    }
                    val fuzzy = if (result.fuzzyHunks > 0) " (${result.fuzzyHunks} matched ignoring whitespace)" else ""
                    _effects.tryEmit(WorkspaceEffect.Message("Applied ${result.hunksApplied} change(s) to ${file.name}$fuzzy. Review, then save.", "Undo", WorkspaceIntent.UndoPatch))
                    recomputeMatches()
                    publishAiContext()
                    refreshGit()
                }
            }
        }
    }

    /** Diffs may use project-relative paths or just a file name; find the best match. */
    private suspend fun resolvePatchPath(dir: File, path: String): String? = withContext(Dispatchers.IO) {
        if (path == "/dev/null") return@withContext null
        if (File(dir, path).exists()) return@withContext path
        val name = path.substringAfterLast('/')
        val candidates = dir.walkTopDown().onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.isFile && it.name == name }.toList()
        val best = candidates.maxByOrNull { c -> c.relativeTo(dir).invariantSeparatorsPath.commonSuffixWith(path).length }
        best?.relativeTo(dir)?.invariantSeparatorsPath ?: path
    }

    private suspend fun applyPreference(change: PreferenceChange) {
        when (change) {
            is PreferenceChange.Font -> preferencesRepository.setCodingFont(change.font)
            is PreferenceChange.FontSize -> preferencesRepository.setFontSize(change.sp)
            is PreferenceChange.LineHeight -> preferencesRepository.setLineHeight(change.multiplier)
            is PreferenceChange.Ligatures -> preferencesRepository.setLigatures(change.enabled)
            is PreferenceChange.TabSize -> preferencesRepository.setTabSize(change.size)
            is PreferenceChange.SoftWrap -> preferencesRepository.setSoftWrap(change.enabled)
            is PreferenceChange.Scheme -> preferencesRepository.setEditorScheme(change.id)
            is PreferenceChange.DynamicColor -> preferencesRepository.setDynamicColor(change.enabled)
            is PreferenceChange.ThemeMode -> preferencesRepository.setThemeMode(change.mode)
            is PreferenceChange.AutoSave -> preferencesRepository.setAutoSave(change.enabled)
            is PreferenceChange.IndentGuides -> preferencesRepository.setIndentGuides(change.enabled)
            is PreferenceChange.Haptics -> preferencesRepository.setHapticExtraKeys(change.enabled)
        }
    }

    // ------------------------------------------------------------------ build

    private fun startBuild() {
        val dir = _state.value.projectDir ?: return
        if (buildJob?.isActive == true) return
        buildJob = viewModelScope.launch {
            if (!saveAll()) return@launch
            val variant = _state.value.build.variant
            _state.update {
                it.copy(
                    dockVisible = true,
                    dockTab = DockTab.Build,
                    build = BuildUiState(running = true, variant = variant, startedAt = System.currentTimeMillis(), history = it.build.history, outputSize = it.build.outputSize),
                    toolchain = toolchain.status(),
                )
            }
            val name = _state.value.projectName.ifBlank { dir.name }
            liveJob?.cancel()
            liveDiagnostics.value = emptyMap()
            buildNotifier.started(name)
            try {
                orchestrator.build(BuildRequest(dir, ":app", variant)).collect { event -> onBuildEvent(event) }
            } finally {
                when (val r = _state.value.build.result) {
                    is BuildResult.Success -> buildNotifier.finished(name, true, "${r.output.name} in ${r.durationMillis / 1000} s", r.output)
                    is BuildResult.Failure -> buildNotifier.finished(
                        name,
                        false,
                        "Failed at ${r.failedStep.title}: " + (r.diagnostics.firstOrNull { it.severity == Severity.Error }?.message ?: "see the build console"),
                        null,
                    )
                    else -> buildNotifier.finished(name, false, "Build cancelled", null)
                }
                _state.update { st ->
                    val steps = st.build.steps.mapValues { (_, ui) -> if (ui.status == StepStatus.Running) ui.copy(status = StepStatus.Cancelled) else ui }
                    st.copy(build = st.build.copy(running = false, steps = steps, result = st.build.result ?: BuildResult.Cancelled(System.currentTimeMillis() - (st.build.startedAt ?: 0L))))
                }
                publishAiContext()
                refreshTree()
                // Also after Cancel: the job is cancelled, so record outside its cancellation.
                withContext(kotlinx.coroutines.NonCancellable) { recordBuild(dir, variant) }
            }
        }
    }

    private suspend fun loadBuildHistory(dir: File) {
        val history = withContext(Dispatchers.IO) { runCatching { BuildHistory.load(dir) }.getOrDefault(emptyList()) }
        val size = withContext(Dispatchers.IO) {
            history.firstOrNull { it.outcome == BuildRecord.Outcome.Success }?.outputPath?.let(::File)?.takeIf { it.isFile }?.let(ApkSizeAnalyzer::analyze)
        }
        _state.update { it.copy(build = it.build.copy(history = history, outputSize = size)) }
    }

    /** Adds the finished build to the History tab and measures its APK/AAB. */
    private suspend fun recordBuild(dir: File, variant: BuildVariant) {
        val build = _state.value.build
        val result = build.result ?: return
        val output = (result as? BuildResult.Success)?.output
        val record = BuildRecord(
            time = System.currentTimeMillis(),
            variant = variant.displayName,
            outcome = when (result) {
                is BuildResult.Success -> BuildRecord.Outcome.Success
                is BuildResult.Failure -> BuildRecord.Outcome.Failure
                is BuildResult.Cancelled -> BuildRecord.Outcome.Cancelled
            },
            durationMillis = result.durationMillis,
            errors = build.diagnostics.count { it.severity == Severity.Error },
            warnings = build.diagnostics.count { it.severity == Severity.Warning },
            outputBytes = output?.length() ?: 0,
            summary = when (result) {
                is BuildResult.Success -> output?.name.orEmpty()
                is BuildResult.Failure -> "${result.failedStep.title}: " + (result.diagnostics.firstOrNull { it.severity == Severity.Error }?.message?.take(160) ?: "failed")
                is BuildResult.Cancelled -> "Cancelled"
            },
            outputPath = output?.absolutePath,
        )
        val (history, size) = withContext(Dispatchers.IO) {
            val h = runCatching { BuildHistory.append(dir, record) }.getOrElse { listOf(record) + build.history }
            h to (output?.let(ApkSizeAnalyzer::analyze) ?: build.outputSize)
        }
        _state.update { it.copy(build = it.build.copy(history = history, outputSize = size)) }
    }

    private fun onBuildEvent(event: BuildEvent) {
        when (event) {
            is BuildEvent.Started -> appendLog(null, com.codein.ide.compiler.model.LogLevel.Info, "Building ${event.projectName} · ${event.variant.displayName}", event.timestamp)
            is BuildEvent.StepStarted -> _state.update { st ->
                buildNotifier.progress(st.projectName, event.step.title, st.build.progress.takeIf { it > 0f })
                st.copy(build = st.build.copy(currentStep = event.step, steps = st.build.steps + (event.step to StepUi(StepStatus.Running, null, event.timestamp))))
            }
            is BuildEvent.StepFinished -> _state.update { st ->
                st.copy(build = st.build.copy(steps = st.build.steps + (event.step to StepUi(event.status, event.durationMillis, null))))
            }
            is BuildEvent.Log -> appendLog(event.step, event.level, event.message, event.timestamp)
            is BuildEvent.Diagnostic -> _state.update { st -> st.copy(build = st.build.copy(diagnostics = st.build.diagnostics + event.diagnostic)) }
            is BuildEvent.Progress -> _state.update { st -> st.copy(build = st.build.copy(progress = event.fraction)) }
            is BuildEvent.Finished -> {
                _state.update { st -> st.copy(build = st.build.copy(result = event.result, running = false, progress = 1f, currentStep = null)) }
                when (val r = event.result) {
                    is BuildResult.Success -> {
                        emit("Build succeeded in ${r.durationMillis / 1000.0}s: ${r.output.name}")
                        emitOutput(r, install = !r.variant.producesBundle)
                    }
                    is BuildResult.Failure -> {
                        val errors = r.diagnostics.count { it.severity == Severity.Error }
                        emit("Build failed at ${r.failedStep.title}" + if (errors > 0) " with $errors error(s)" else "")
                        r.diagnostics.firstOrNull { it.severity == Severity.Error && it.file != null }?.let { jumpTo(it) }
                    }
                    is BuildResult.Cancelled -> emit("Build cancelled")
                }
            }
        }
    }

    private fun appendLog(step: com.codein.ide.compiler.model.BuildStep?, level: com.codein.ide.compiler.model.LogLevel, message: String, timestamp: Long) {
        _state.update { st ->
            val logs = (st.build.logs + BuildLogLine(logIds.getAndIncrement(), step, level, message, timestamp)).takeLast(MAX_LOG_LINES)
            st.copy(build = st.build.copy(logs = logs))
        }
    }

    private fun emitOutput(result: BuildResult.Success, install: Boolean) {
        if (install && !result.variant.producesBundle) _effects.tryEmit(WorkspaceEffect.InstallApk(result.output))
        else _effects.tryEmit(
            WorkspaceEffect.ShareFile(result.output, if (result.variant.producesBundle) "application/octet-stream" else "application/vnd.android.package-archive"),
        )
    }

    private fun jumpTo(diagnostic: CompilerDiagnostic) {
        val dir = _state.value.projectDir ?: return
        val path = diagnostic.file ?: return
        val file = File(path).let { if (it.isAbsolute) it else File(dir, path) }
        if (!file.canonicalPath.startsWith(dir.canonicalPath)) {
            emit("${file.name} is outside the project")
            return
        }
        expandParents(file)
        refreshTree()
        openFile(relative(file), diagnostic.line ?: 1, diagnostic.column ?: 1)
    }

    /** Diagnostics for the active file, converted to editor squiggles. */
    private fun scheduleLiveCheck(file: File, text: String) {
        val dir = _state.value.projectDir ?: return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            delay(LIVE_CHECK_DELAY_MS)
            val moduleDir = File(dir, "app")
            val found = withContext(Dispatchers.Default) { runCatching { liveJava.check(moduleDir, mapOf(file to text)) }.getOrNull() } ?: return@launch
            liveDiagnostics.update { current -> current + found.mapKeys { it.key.canonicalPath } }
            _state.update { it.copy(liveCheckVersion = it.liveCheckVersion + 1) }
        }
    }

    fun diagnosticsFor(tab: OpenFile?): List<EditorDiagnostic> {
        tab ?: return emptyList()
        val dir = _state.value.projectDir ?: return emptyList()
        liveDiagnostics.value[tab.file.canonicalPath]?.let { live ->
            return live.filter { it.line != null }.map { d -> EditorDiagnostic(d.line!!, d.column, d.endColumn, d.message, d.severity == Severity.Error, "Live") }
        }
        return _state.value.build.diagnostics.mapNotNull { d ->
            val f = d.file?.let { File(it).let { file -> if (file.isAbsolute) file else File(dir, it) } } ?: return@mapNotNull null
            if (f.canonicalPath != tab.file.canonicalPath || d.line == null) return@mapNotNull null
            EditorDiagnostic(d.line!!, d.column, d.endColumn, d.message, d.severity == Severity.Error, d.source.name)
        }
    }

    // ------------------------------------------------------------------ AI context

    private fun publishAiContext() {
        val s = _state.value
        val dir = s.projectDir ?: return
        viewModelScope.launch {
            val gradle = withContext(Dispatchers.IO) {
                s.gradleFiles.take(6).associateWith { rel -> runCatching { File(dir, rel).readText() }.getOrDefault("") }
            }
            val tree = s.tree.take(400).map { "  ".repeat(it.depth) + it.file.name + if (it.isDirectory) "/" else "" }
            _aiContext.value = AiContextSnapshot(
                projectName = s.projectName,
                projectRoot = dir,
                currentFilePath = s.activeFile?.relativePath,
                currentFileText = s.activeFile?.content,
                selectionText = s.cursor.selectedText,
                cursorLine = s.cursor.line,
                diagnostics = s.build.diagnostics,
                gradleFiles = gradle,
                fileTree = tree,
            )
        }
    }

    private fun emit(message: String) {
        _effects.tryEmit(WorkspaceEffect.Message(message))
    }

    override fun onCleared() {
        val dirty = _state.value.tabs.filter { it.isDirty }
        if (dirty.isNotEmpty() && preferences.value.autoSave) {
            dirty.forEach { tab -> runCatching { tab.file.writeText(tab.content) } }
        }
    }

    private companion object {
        const val RENAME_GLOBS = "*.kt, *.java, *.xml, *.kts, *.gradle"
        const val MAX_OPEN_BYTES = 4L * 1024 * 1024
        const val AUTO_SAVE_DELAY_MS = 1200L
        const val LIVE_CHECK_DELAY_MS = 900L
        const val SEARCH_DEBOUNCE_MS = 250L
        const val EDITOR_SETTLE_MS = 120L
        const val MAX_LOG_LINES = 4000
    }
}
