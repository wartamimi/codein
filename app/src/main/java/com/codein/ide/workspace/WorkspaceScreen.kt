package com.codein.ide.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codein.ide.ai.ui.AiAssistantPanel
import com.codein.ide.ai.ui.AiAssistantViewModel
import com.codein.ide.compiler.gradle.GradleDependency
import com.codein.ide.compiler.gradle.GradleScriptParser
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.Hairline
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.components.VerticalHairline
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.CodingFont
import com.codein.ide.core.ui.theme.EditorColorSchemes
import com.codein.ide.editor.keys.ExtraKey
import com.codein.ide.editor.keys.ExtraKeysBar
import com.codein.ide.editor.palette.CommandPalette
import com.codein.ide.editor.palette.PaletteCategory
import com.codein.ide.editor.palette.PaletteItem
import com.codein.ide.editor.outline.SymbolKind
import com.codein.ide.editor.outline.SymbolOutline
import com.codein.ide.editor.search.FindReplaceBar
import com.codein.ide.editor.search.GlobalSearchPanel
import com.codein.ide.editor.settings.EditorPreferencesSheet
import com.codein.ide.editor.settings.PreferenceChange
import com.codein.ide.editor.sora.CodeEditorController
import com.codein.ide.editor.sora.EditorDocument
import com.codein.ide.editor.sora.SoraCodeEditor
import com.codein.ide.editor.sora.rememberCodeEditorController
import com.codein.ide.install.OutputActions
import com.codein.ide.logcat.LogcatPanel
import com.codein.ide.logcat.LogcatViewModel
import com.codein.ide.maven.model.MavenCoordinate
import com.codein.ide.maven.ui.DependencyExplorerIntent
import com.codein.ide.maven.ui.DependencyExplorerScreen
import com.codein.ide.maven.ui.DependencyExplorerViewModel
import com.codein.ide.vcs.ui.GitPanel
import com.codein.ide.vcs.ui.GitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ExpandedWidth = 840.dp

/**
 * The adaptive IDE workspace. Phones get a modal file drawer, a full-height editor and a
 * resizable bottom dock (Build, Logcat, AI, Search, Git, Dependencies). Tablets and foldables get
 * a persistent sidebar, the editor, a bottom console and a right-hand AI dock.
 */
@Composable
fun WorkspaceScreen(
    projectPath: String,
    onOpenSettings: () -> Unit,
    onCloseProject: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
    aiViewModel: AiAssistantViewModel = hiltViewModel(),
    gitViewModel: GitViewModel = hiltViewModel(),
    logcatViewModel: LogcatViewModel = hiltViewModel(),
    dependencyViewModel: DependencyExplorerViewModel = hiltViewModel(),
) {
    LaunchedEffect(projectPath) {
        viewModel.openProject(projectPath)
        gitViewModel.setProject(File(projectPath))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val controller = rememberCodeEditorController()
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var aiDockVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller) { viewModel.commands.collect { command -> controller.execute(command) } }
    LaunchedEffect(Unit) { viewModel.aiContext.collect { aiViewModel.updateContext(it) } }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WorkspaceEffect.Message -> snackbar.showSnackbar(effect.text)
                is WorkspaceEffect.InstallApk -> if (!OutputActions.install(context, effect.apk)) {
                    snackbar.showSnackbar("Allow Codein to install apps, then tap Install again")
                }
                is WorkspaceEffect.ShareFile -> OutputActions.share(context, effect.file, effect.mimeType)
                WorkspaceEffect.OpenAiDock -> aiDockVisible = true
            }
        }
    }
    LaunchedEffect(Unit) { gitViewModel.messages.collect { snackbar.showSnackbar(it); viewModel.onIntent(WorkspaceIntent.RefreshTree) } }
    LaunchedEffect(Unit) { dependencyViewModel.messages.collect { snackbar.showSnackbar(it); viewModel.onIntent(WorkspaceIntent.RefreshTree) } }
    LaunchedEffect(state.projectDir, state.gradleFiles) {
        val dir = state.projectDir ?: return@LaunchedEffect
        val (buildFile, declared) = withContext(Dispatchers.IO) {
            val file = listOf("app/build.gradle.kts", "app/build.gradle").map { File(dir, it) }.firstOrNull { it.isFile }
            val coords = runCatching {
                GradleScriptParser().parseModule(dir, ":app").dependencies
                    .filterIsInstance<GradleDependency.Maven>()
                    .mapNotNull { runCatching { MavenCoordinate.parse(it.coordinate) }.getOrNull() }
            }.getOrDefault(emptyList())
            file to coords
        }
        dependencyViewModel.onIntent(DependencyExplorerIntent.SetTarget(buildFile, declared))
    }

    val onIntent: (WorkspaceIntent) -> Unit = { intent ->
        viewModel.onIntent(intent)
        if (intent is WorkspaceIntent.Key && intent.key == ExtraKey.AiFix && state.errorCount > 0) aiViewModel.explainAndFix()
    }
    val aiFix: () -> Unit = {
        aiDockVisible = true
        viewModel.onIntent(WorkspaceIntent.ShowDock(DockTab.Ai))
        aiViewModel.explainAndFix()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.S -> { onIntent(if (e.isShiftPressed) WorkspaceIntent.SaveAll else WorkspaceIntent.Save); true }
                    Key.P -> { onIntent(WorkspaceIntent.SetPalette(true)); true }
                    Key.O -> { onIntent(WorkspaceIntent.SetPalette(true, "#")); true }
                    Key.F -> { onIntent(if (e.isShiftPressed) WorkspaceIntent.ShowDock(DockTab.Search) else WorkspaceIntent.OpenFind); true }
                    Key.B -> { onIntent(WorkspaceIntent.Build); true }
                    Key.Slash -> { controller.toggleComment(); true }
                    else -> false
                }
            },
    ) {
        val expanded = maxWidth >= ExpandedWidth
        val maxDock = maxHeight * 0.75f
        val editorArea: @Composable (Modifier) -> Unit = { modifier ->
            EditorArea(
                state = state,
                prefs = prefs,
                controller = controller,
                diagnostics = viewModel.diagnosticsFor(state.activeFile),
                onIntent = onIntent,
                onUndoRedo = { u, r -> canUndo = u; canRedo = r },
                onOpenPalette = { onIntent(WorkspaceIntent.SetPalette(true)) },
                modifier = modifier,
            )
        }
        val dockContent: @Composable (DockTab, Modifier) -> Unit = { tab, modifier ->
            when (tab) {
                DockTab.Build -> BuildConsole(state.build, state.toolchain, onIntent, aiFix, onOpenSettings, modifier)
                DockTab.Logcat -> {
                    val logcat by logcatViewModel.state.collectAsStateWithLifecycle()
                    LogcatPanel(logcat, logcatViewModel::onIntent, logcatViewModel::start, logcatViewModel::stop, modifier, grantCommand = logcatViewModel.grantCommand)
                }
                DockTab.Ai -> {
                    val ai by aiViewModel.state.collectAsStateWithLifecycle()
                    AiAssistantPanel(ai, aiViewModel::onIntent, { onIntent(WorkspaceIntent.ApplyPatch(it)) }, { onIntent(WorkspaceIntent.InsertCode(it)) }, onOpenSettings, modifier)
                }
                DockTab.Search -> GlobalSearchPanel(
                    state = state.search,
                    onQueryChange = { onIntent(WorkspaceIntent.SearchQuery(it)) },
                    onGlobsChange = { onIntent(WorkspaceIntent.SearchGlobs(it)) },
                    onOptionsChange = { onIntent(WorkspaceIntent.SearchOptions(it)) },
                    onOpenHit = { f, hit -> onIntent(WorkspaceIntent.OpenSearchHit(f, hit)) },
                    modifier = modifier,
                )
                DockTab.Git -> {
                    val git by gitViewModel.state.collectAsStateWithLifecycle()
                    GitPanel(git, gitViewModel::onIntent, { onIntent(WorkspaceIntent.OpenFile(it)) }, modifier)
                }
                DockTab.Dependencies -> {
                    val deps by dependencyViewModel.state.collectAsStateWithLifecycle()
                    DependencyExplorerScreen(deps, dependencyViewModel::onIntent, modifier)
                }
            }
        }

        if (expanded) {
            var sidebarVisible by rememberSaveable { mutableStateOf(true) }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                TopStudioBar(
                    state = state,
                    compact = false,
                    aiVisible = aiDockVisible,
                    onToggleSidebar = { sidebarVisible = !sidebarVisible },
                    onIntent = onIntent,
                    onToggleAi = { aiDockVisible = !aiDockVisible },
                    onOpenSettings = onOpenSettings,
                    onCloseProject = onCloseProject,
                )
                Hairline()
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedVisibility(sidebarVisible) {
                        Row {
                            FileExplorer(state.projectName, state.tree, state.gitStatus, state.activeFile?.relativePath, onIntent, {}, Modifier.width(280.dp))
                            VerticalHairline()
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        editorArea(Modifier.weight(1f))
                        if (state.activeFile != null && WindowInsets.ime.getBottom(LocalDensity.current) > 0) {
                            ExtraKeysBar({ onIntent(WorkspaceIntent.Key(it)) }, canUndo, canRedo, state.errorCount > 0, prefs.hapticExtraKeys)
                        }
                        val consoleTabs = DockTab.entries - DockTab.Ai
                        if (state.dockVisible && state.dockTab != DockTab.Ai) {
                            ResizableDock(state.dockTab, consoleTabs, onIntent, maxDock) { tab -> dockContent(tab, Modifier.fillMaxSize()) }
                        } else {
                            DockLauncher(consoleTabs, state, onIntent)
                        }
                    }
                    AnimatedVisibility(aiDockVisible) {
                        Row {
                            VerticalHairline()
                            Box(Modifier.width(380.dp).fillMaxHeight().background(CodeinTheme.colors.panel)) { dockContent(DockTab.Ai, Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet(Modifier.width(300.dp), drawerContainerColor = CodeinTheme.colors.panel) {
                        FileExplorer(
                            state.projectName, state.tree, state.gitStatus, state.activeFile?.relativePath, onIntent,
                            onFileOpened = { scope.launch { drawerState.close() } },
                            modifier = Modifier.statusBarsPadding(),
                        )
                    }
                },
            ) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    TopStudioBar(
                        state = state,
                        compact = true,
                        aiVisible = state.dockVisible && state.dockTab == DockTab.Ai,
                        onToggleSidebar = { scope.launch { drawerState.open() } },
                        onIntent = onIntent,
                        onToggleAi = {
                            if (state.dockVisible && state.dockTab == DockTab.Ai) onIntent(WorkspaceIntent.HideDock)
                            else onIntent(WorkspaceIntent.ShowDock(DockTab.Ai))
                        },
                        onOpenSettings = onOpenSettings,
                        onCloseProject = onCloseProject,
                    )
                    Hairline()
                    editorArea(Modifier.weight(1f))
                    if (state.dockVisible) {
                        ResizableDock(state.dockTab, DockTab.entries, onIntent, maxDock) { tab -> dockContent(tab, Modifier.fillMaxSize()) }
                    } else {
                        if (state.activeFile != null) {
                            ExtraKeysBar({ onIntent(WorkspaceIntent.Key(it)) }, canUndo, canRedo, state.errorCount > 0, prefs.hapticExtraKeys)
                        }
                        DockLauncher(DockTab.entries, state, onIntent)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 56.dp))
    }

    if (state.paletteOpen) {
        val items = rememberPaletteItems(state, onIntent, onOpenSettings, toggleAi = { aiDockVisible = !aiDockVisible })
        key(state.paletteQuery) {
            CommandPalette(items = items, onDismiss = { onIntent(WorkspaceIntent.SetPalette(false)) }, initialQuery = state.paletteQuery)
        }
    }
    if (state.preferencesOpen) {
        EditorPreferencesSheet(prefs, onChange = { onIntent(WorkspaceIntent.Preference(it)) }, onDismiss = { onIntent(WorkspaceIntent.SetPreferences(false)) })
    }
}

private fun CodeEditorController.execute(command: EditorCommand) {
    when (command) {
        is EditorCommand.GoTo -> goTo(command.line, command.column)
        is EditorCommand.Select -> select(command.start, command.end)
        is EditorCommand.ReplaceRange -> replaceRange(command.start, command.end, command.text)
        is EditorCommand.Insert -> insert(command.text, command.caretBack)
        is EditorCommand.InsertTab -> insertTab(command.size)
        EditorCommand.Undo -> undo()
        EditorCommand.Redo -> redo()
        EditorCommand.ToggleComment -> toggleComment()
    }
}

@Composable
private fun EditorArea(
    state: WorkspaceState,
    prefs: com.codein.ide.core.ui.prefs.EditorPreferences,
    controller: CodeEditorController,
    diagnostics: List<com.codein.ide.editor.sora.EditorDiagnostic>,
    onIntent: (WorkspaceIntent) -> Unit,
    onUndoRedo: (Boolean, Boolean) -> Unit,
    onOpenPalette: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        EditorTabStrip(state.tabs, state.activeIndex, onIntent)
        val active = state.activeFile
        Breadcrumbs(active?.relativePath, state.cursor.line, state.cursor.column, state.errorCount)
        Box(Modifier.weight(1f).fillMaxWidth().background(prefs.editorScheme.background)) {
            if (active == null) {
                EmptyState(
                    icon = CodeinIcons.Code,
                    title = "No file open",
                    message = "Open a file from the explorer, or press Ctrl+P to jump to any file or command.",
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    TextButton(onClick = onOpenPalette) { Text("Quick open") }
                }
            } else {
                SoraCodeEditor(
                    controller = controller,
                    document = EditorDocument(active.relativePath, active.name, active.content, active.version),
                    preferences = prefs,
                    scheme = prefs.editorScheme,
                    diagnostics = diagnostics,
                    onTextChanged = {
                        onIntent(WorkspaceIntent.TextChanged(it))
                        onUndoRedo(controller.canUndo(), controller.canRedo())
                    },
                    onCursorChanged = { onIntent(WorkspaceIntent.CursorChanged(it)) },
                    onDiagnosticTapped = { d ->
                        state.build.diagnostics.firstOrNull { it.line == d.line && it.message == d.message }?.let { onIntent(WorkspaceIntent.ShowDock(DockTab.Build)) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                FindReplaceBar(state.find, { onIntent(WorkspaceIntent.Find(it)) }, Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/** Bottom dock with a draggable top edge. */
@Composable
private fun ResizableDock(
    selected: DockTab,
    tabs: List<DockTab>,
    onIntent: (WorkspaceIntent) -> Unit,
    maxHeight: Dp,
    content: @Composable (DockTab) -> Unit,
) {
    val density = LocalDensity.current
    var heightPx by rememberSaveable { mutableFloatStateOf(with(density) { 320.dp.toPx() }) }
    val minPx = with(density) { 160.dp.toPx() }
    val maxPx = with(density) { maxHeight.toPx() }.coerceAtLeast(minPx)
    val dragState = rememberDraggableState { delta -> heightPx = (heightPx - delta).coerceIn(minPx, maxPx) }
    Column(Modifier.fillMaxWidth().height(with(density) { heightPx.coerceIn(minPx, maxPx).toDp() }).background(CodeinTheme.colors.panel)) {
        Hairline()
        Box(
            Modifier.fillMaxWidth().height(18.dp).draggable(dragState, Orientation.Vertical),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEach { tab ->
                    PillChip(tab.title, icon = tab.icon(), selected = tab == selected, onClick = { onIntent(WorkspaceIntent.ShowDock(tab)) })
                }
            }
            ToolbarIconButton(CodeinIcons.ChevronDown, "Hide panel", size = 32.dp) { onIntent(WorkspaceIntent.HideDock) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content(selected) }
    }
}

/** Collapsed dock: one row of tab shortcuts with live status. */
@Composable
private fun DockLauncher(tabs: List<DockTab>, state: WorkspaceState, onIntent: (WorkspaceIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().background(CodeinTheme.colors.panel)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val label = when {
                    tab == DockTab.Build && state.errorCount > 0 -> "Build · ${state.errorCount} errors"
                    tab == DockTab.Build && state.build.running -> "Build · ${state.build.currentStep?.title ?: "running"}"
                    else -> tab.title
                }
                PillChip(
                    label,
                    icon = tab.icon(),
                    accent = if (tab == DockTab.Build && state.errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    selected = tab == DockTab.Build && (state.errorCount > 0 || state.build.running),
                    onClick = { onIntent(WorkspaceIntent.ShowDock(tab)) },
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun DockTab.icon() = when (this) {
    DockTab.Build -> CodeinIcons.Hammer
    DockTab.Logcat -> CodeinIcons.Terminal
    DockTab.Ai -> CodeinIcons.Sparkle
    DockTab.Search -> CodeinIcons.Search
    DockTab.Git -> CodeinIcons.GitBranch
    DockTab.Dependencies -> CodeinIcons.Package
}

@Composable
private fun rememberPaletteItems(
    state: WorkspaceState,
    onIntent: (WorkspaceIntent) -> Unit,
    onOpenSettings: () -> Unit,
    toggleAi: () -> Unit,
): List<PaletteItem> {
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(state.projectDir) {
        val dir = state.projectDir ?: return@LaunchedEffect
        files = withContext(Dispatchers.IO) {
            dir.walkTopDown()
                .onEnter { it.name !in setOf("build", ".git", ".gradle", ".idea", ".kotlin") }
                .filter { it.isFile }
                .take(5000)
                .map { it.relativeTo(dir).invariantSeparatorsPath }
                .toList()
        }
    }
    val active = state.activeFile
    val symbols = remember(active?.relativePath, active?.content) {
        active?.let { SymbolOutline.outline(it.relativePath, it.content) }.orEmpty()
    }
    return remember(files, state.build.variant, symbols) {
        buildList {
            symbols.forEachIndexed { i, sym ->
                add(
                    PaletteItem(
                        "s:$i:${sym.line}:${sym.name}",
                        "  ".repeat(sym.depth.coerceAtMost(6)) + sym.name,
                        "${sym.kind.label} · line ${sym.line}" + if (sym.detail.isNotEmpty() && sym.kind == SymbolKind.Element) " · ${sym.detail}" else "",
                        PaletteCategory.Symbol,
                        CodeinIcons.Code,
                    ) { onIntent(WorkspaceIntent.GoToLine(sym.line)) },
                )
            }
            files.forEach { rel ->
                add(PaletteItem("f:$rel", rel.substringAfterLast('/'), rel.substringBeforeLast('/', ""), PaletteCategory.File, fileName = rel.substringAfterLast('/')) { onIntent(WorkspaceIntent.OpenFile(rel)) })
            }
            add(PaletteItem("a:build", "Build & Run", state.build.variant.displayName, PaletteCategory.Action, CodeinIcons.Play, shortcut = "Ctrl+B") { onIntent(WorkspaceIntent.Build) })
            add(PaletteItem("a:save", "Save all", "", PaletteCategory.Action, CodeinIcons.Save, shortcut = "Ctrl+Shift+S") { onIntent(WorkspaceIntent.SaveAll) })
            add(PaletteItem("a:symbols", "Go to symbol in file", "Type # in the palette", PaletteCategory.Action, CodeinIcons.Code, shortcut = "Ctrl+O") { onIntent(WorkspaceIntent.SetPalette(true, "#")) })
            add(PaletteItem("a:find", "Find in file", "", PaletteCategory.Action, CodeinIcons.Search, shortcut = "Ctrl+F") { onIntent(WorkspaceIntent.OpenFind) })
            add(PaletteItem("a:search", "Search in project", "", PaletteCategory.Action, CodeinIcons.Search, shortcut = "Ctrl+Shift+F") { onIntent(WorkspaceIntent.ShowDock(DockTab.Search)) })
            add(PaletteItem("a:ai", "Toggle Codein AI", "", PaletteCategory.Action, CodeinIcons.Sparkle) { toggleAi(); onIntent(WorkspaceIntent.ShowDock(DockTab.Ai)) })
            add(PaletteItem("a:logcat", "Show Logcat", "", PaletteCategory.Action, CodeinIcons.Terminal) { onIntent(WorkspaceIntent.ShowDock(DockTab.Logcat)) })
            add(PaletteItem("a:git", "Source control", "", PaletteCategory.Action, CodeinIcons.GitBranch) { onIntent(WorkspaceIntent.ShowDock(DockTab.Git)) })
            add(PaletteItem("a:deps", "Manage dependencies", "", PaletteCategory.Action, CodeinIcons.Package) { onIntent(WorkspaceIntent.ShowDock(DockTab.Dependencies)) })
            add(PaletteItem("a:prefs", "Editor appearance", "", PaletteCategory.Appearance, CodeinIcons.Palette) { onIntent(WorkspaceIntent.SetPreferences(true)) })
            add(PaletteItem("a:settings", "Settings & toolchain", "", PaletteCategory.Action, CodeinIcons.Settings) { onOpenSettings() })
            BuildVariant.entries.forEach { v ->
                add(PaletteItem("g:${v.name}", "Build variant: ${v.displayName}", "", PaletteCategory.Gradle, CodeinIcons.Layers) { onIntent(WorkspaceIntent.SetVariant(v)) })
                add(PaletteItem("g:run:${v.name}", "assemble ${v.displayName}", "Switch variant and build", PaletteCategory.Gradle, CodeinIcons.Hammer) {
                    onIntent(WorkspaceIntent.SetVariant(v)); onIntent(WorkspaceIntent.Build)
                })
            }
            CodingFont.entries.forEach { font ->
                add(PaletteItem("p:font:${font.id}", "Font: ${font.displayName}", "", PaletteCategory.Appearance, CodeinIcons.Type) { onIntent(WorkspaceIntent.Preference(PreferenceChange.Font(font))) })
            }
            EditorColorSchemes.all.forEach { scheme ->
                add(PaletteItem("p:theme:${scheme.id}", "Theme: ${scheme.displayName}", "", PaletteCategory.Appearance, CodeinIcons.Palette) { onIntent(WorkspaceIntent.Preference(PreferenceChange.Scheme(scheme.id))) })
            }
        }
    }
}
