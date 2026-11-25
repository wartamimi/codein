package com.codein.ide.workspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codein.ide.ai.ui.AiAssistantPanel
import com.codein.ide.editor.sora.CompletionWords
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import com.codein.ide.preview.LayoutPreview
import com.codein.ide.preview.isLayoutFile
import com.codein.ide.terminal.TerminalPanel
import com.codein.ide.terminal.TerminalViewModel
import com.codein.ide.ai.ui.AiAssistantViewModel
import com.codein.ide.compiler.gradle.GradleDependency
import com.codein.ide.compiler.gradle.GradleScriptParser
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.compiler.model.Severity
import com.codein.ide.core.ui.components.DragHandle
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.HairlineDivider
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.PulseDot
import com.codein.ide.core.ui.components.SegmentedTabs
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.components.formatDuration
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.CodingFont
import com.codein.ide.core.ui.theme.EditorColorSchemes
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.core.ui.theme.metricsTextStyle
import com.codein.ide.editor.keys.ExtraKey
import com.codein.ide.editor.keys.ExtraKeysBar
import com.codein.ide.editor.outline.SymbolKind
import com.codein.ide.editor.outline.SymbolOutline
import com.codein.ide.editor.palette.CommandPalette
import com.codein.ide.editor.palette.PaletteCategory
import com.codein.ide.editor.palette.PaletteItem
import com.codein.ide.editor.search.FindReplaceBar
import com.codein.ide.editor.search.GlobalSearchPanel
import com.codein.ide.editor.settings.EditorPreferencesSheet
import com.codein.ide.editor.settings.PreferenceChange
import com.codein.ide.editor.sora.CodeEditorController
import com.codein.ide.editor.sora.EditorDiagnostic
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
import com.codein.ide.signer.ui.KeystoreVaultViewModel
import com.codein.ide.vcs.ui.GitPanel
import com.codein.ide.vcs.ui.GitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** The four tools that live in the explorer sidebar / drawer. */
private enum class SidebarTab(val title: String) { Files("Files"), Search("Search"), Git("Git"), Maven("Maven") }

/** Snap points of the bottom developer dock. */
private enum class DockDetent { Collapsed, Half, Expanded }

private val SidebarDockTabs = setOf(DockTab.Search, DockTab.Git, DockTab.Dependencies)

private fun DockTab.toSidebar(): SidebarTab = when (this) {
    DockTab.Git -> SidebarTab.Git
    DockTab.Dependencies -> SidebarTab.Maven
    else -> SidebarTab.Search
}

/**
 * The adaptive IDE workspace.
 *
 * - Compact (phones): a 300dp drawer with Files / Search / Git / Maven, a full-bleed editor, and a
 *   multi-detent developer dock (38dp status ticker → 42% → 88%) with Build, Logcat and Codein AI.
 * - Medium / Expanded (tablets, foldables): a 3-pane split, Explorer (260dp) | Editor | Codein AI
 *   (340dp), with draggable 1dp dividers, plus the same dock (Build, Logcat) under the editor.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun WorkspaceScreen(
    projectPath: String,
    onOpenSettings: () -> Unit,
    onCloseProject: () -> Unit,
    onNewProject: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
    aiViewModel: AiAssistantViewModel = hiltViewModel(),
    gitViewModel: GitViewModel = hiltViewModel(),
    logcatViewModel: LogcatViewModel = hiltViewModel(),
    terminalViewModel: TerminalViewModel = hiltViewModel(),
    dependencyViewModel: DependencyExplorerViewModel = hiltViewModel(),
    vaultViewModel: KeystoreVaultViewModel = hiltViewModel(),
) {
    LaunchedEffect(projectPath) {
        viewModel.openProject(projectPath)
        gitViewModel.setProject(File(projectPath))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val controller = rememberCodeEditorController()
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var aiPaneVisible by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var sidebarTab by rememberSaveable { mutableStateOf(SidebarTab.Files) }
    var sidebarRequest by remember { mutableIntStateOf(0) }
    var signingOpen by rememberSaveable { mutableStateOf(false) }
    var lastConsoleTab by rememberSaveable { mutableStateOf(DockTab.Build) }
    var consoleOpen by rememberSaveable { mutableStateOf(false) }

    var pendingRun by remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(controller) { viewModel.commands.collect { command -> controller.execute(command) } }
    // Run: after the system installer returns, open the freshly installed app and follow its logs.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event != androidx.lifecycle.Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val (pkg, since) = pendingRun ?: return@LifecycleEventObserver
            if (OutputActions.installedSince(context, pkg, since)) {
                pendingRun = null
                logcatViewModel.onIntent(com.codein.ide.logcat.LogcatIntent.FollowPackage(pkg))
                if (OutputActions.launch(context, pkg)) viewModel.onIntent(WorkspaceIntent.ShowDock(DockTab.Logcat))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Ask once for notification permission (Android 13+) when the first build starts, so the
    // "build finished" notification and the keep-alive progress notification can be shown.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.build.running) {
        if (state.build.running && !askedNotifications && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Project-wide completion words: re-indexed when the project, its file tree or the open file changes.
    LaunchedEffect(controller, state.projectDir, state.tree.size, state.activeFile?.relativePath) {
        val dir = state.projectDir ?: return@LaunchedEffect
        val symbols = withContext(Dispatchers.IO) { CompletionWords.projectSymbols(dir) }
        controller.setProjectSymbols(symbols)
    }
    LaunchedEffect(Unit) { viewModel.aiContext.collect { aiViewModel.updateContext(it) } }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WorkspaceEffect.Message -> snackbar.showSnackbar(effect.text)
                is WorkspaceEffect.InstallApk -> {
                    pendingRun = OutputActions.packageOf(context, effect.apk)?.let { it to System.currentTimeMillis() }
                    if (!OutputActions.install(context, effect.apk)) {
                        snackbar.showSnackbar("Allow Codein to install apps, then tap Install again")
                    }
                }
                is WorkspaceEffect.ShareFile -> OutputActions.share(context, effect.file, effect.mimeType)
                WorkspaceEffect.OpenAiDock -> aiPaneVisible = true
            }
        }
    }
    LaunchedEffect(Unit) { gitViewModel.messages.collect { snackbar.showSnackbar(it); viewModel.onIntent(WorkspaceIntent.RefreshTree) } }
    LaunchedEffect(Unit) { dependencyViewModel.messages.collect { snackbar.showSnackbar(it); viewModel.onIntent(WorkspaceIntent.RefreshTree) } }
    LaunchedEffect(Unit) { vaultViewModel.messages.collect { snackbar.showSnackbar(it) } }
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
        viewModel.onIntent(WorkspaceIntent.ShowDock(DockTab.Ai))
        aiViewModel.explainAndFix()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(CodeinTheme.colors.base)
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
        val sizeClass = WindowSizeClass.calculateFromSize(DpSize(maxWidth, maxHeight))
        val split = sizeClass.widthSizeClass != WindowWidthSizeClass.Compact
        val aiVisible = split && (aiPaneVisible ?: (sizeClass.widthSizeClass == WindowWidthSizeClass.Expanded))
        val consoleTabs = if (split) listOf(DockTab.Build, DockTab.Logcat, DockTab.Terminal) else listOf(DockTab.Build, DockTab.Logcat, DockTab.Terminal, DockTab.Ai)

        // The view model still speaks in dock tabs. Search / Git / Maven live in the sidebar, and on
        // split layouts Codein AI lives in the right pane, so route those requests there while
        // keeping whichever console tab was open.
        LaunchedEffect(state.dockVisible, state.dockTab, split) {
            val tab = state.dockTab
            if (!state.dockVisible) { consoleOpen = false; return@LaunchedEffect }
            when {
                tab in SidebarDockTabs -> {
                    sidebarTab = tab.toSidebar()
                    sidebarRequest++
                    viewModel.onIntent(if (consoleOpen) WorkspaceIntent.ShowDock(lastConsoleTab) else WorkspaceIntent.HideDock)
                }
                split && tab == DockTab.Ai -> {
                    aiPaneVisible = true
                    viewModel.onIntent(if (consoleOpen) WorkspaceIntent.ShowDock(lastConsoleTab) else WorkspaceIntent.HideDock)
                }
                else -> { lastConsoleTab = tab; consoleOpen = true }
            }
        }

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
                DockTab.Build -> BuildConsole(state.build, state.toolchain, onIntent, aiFix, onOpenSettings, modifier, onAiExplain = { d ->
                    if (d.file != null) onIntent(WorkspaceIntent.JumpToDiagnostic(d))
                    viewModel.onIntent(WorkspaceIntent.ShowDock(DockTab.Ai))
                    aiViewModel.explainError(d)
                })
                DockTab.Logcat -> {
                    val logcat by logcatViewModel.state.collectAsStateWithLifecycle()
                    LogcatPanel(logcat, logcatViewModel::onIntent, logcatViewModel::start, logcatViewModel::stop, modifier, grantCommand = logcatViewModel.grantCommand)
                }
                DockTab.Terminal -> TerminalPanel(terminalViewModel, state.projectDir, modifier)
                else -> {
                    val ai by aiViewModel.state.collectAsStateWithLifecycle()
                    AiAssistantPanel(ai, aiViewModel::onIntent, { onIntent(WorkspaceIntent.ApplyPatch(it)) }, { onIntent(WorkspaceIntent.InsertCode(it)) }, onOpenSettings, modifier)
                }
            }
        }
        val sidebar: @Composable (Modifier, () -> Unit) -> Unit = { modifier, onFileOpened ->
            WorkspaceSidebar(
                tab = sidebarTab,
                onTab = { sidebarTab = it },
                modifier = modifier,
            ) { tab ->
                when (tab) {
                    SidebarTab.Files -> FileExplorer(state.projectName, state.tree, state.gitStatus, state.activeFile?.relativePath, onIntent, onFileOpened)
                    SidebarTab.Search -> GlobalSearchPanel(
                        state = state.search,
                        onQueryChange = { onIntent(WorkspaceIntent.SearchQuery(it)) },
                        onGlobsChange = { onIntent(WorkspaceIntent.SearchGlobs(it)) },
                        onOptionsChange = { onIntent(WorkspaceIntent.SearchOptions(it)) },
                        onOpenHit = { f, hit -> onIntent(WorkspaceIntent.OpenSearchHit(f, hit)); onFileOpened() },
                    )
                    SidebarTab.Git -> {
                        val git by gitViewModel.state.collectAsStateWithLifecycle()
                        GitPanel(git, gitViewModel::onIntent, { onIntent(WorkspaceIntent.OpenFile(it)); onFileOpened() })
                    }
                    SidebarTab.Maven -> {
                        val deps by dependencyViewModel.state.collectAsStateWithLifecycle()
                        DependencyExplorerScreen(deps, dependencyViewModel::onIntent)
                    }
                }
            }
        }
        val menu = StudioMenuActions(
            onSigning = { signingOpen = true },
            onEditorSettings = { onIntent(WorkspaceIntent.SetPreferences(true)) },
            onNewProject = onNewProject,
            onSettings = onOpenSettings,
            onCloseProject = onCloseProject,
        )
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        // Text fields inside the dock (AI prompt, Logcat filter) open the keyboard too. Swapping
        // the dock for the extra-keys bar while one of them has focus would remove the field,
        // drop focus and immediately close the keyboard again, so only swap for the editor.
        var dockHasFocus by remember { mutableStateOf(false) }
        val bottom: @Composable (Dp) -> Unit = { available ->
            if (imeVisible && state.activeFile != null && !dockHasFocus) {
                ExtraKeysBar({ onIntent(WorkspaceIntent.Key(it)) }, canUndo, canRedo, state.errorCount > 0, prefs.hapticExtraKeys)
            } else {
                Box(Modifier.onFocusChanged { dockHasFocus = it.hasFocus }) {
                    DeveloperDock(
                        state = state,
                        tabs = consoleTabs,
                        lastTab = if (lastConsoleTab in consoleTabs) lastConsoleTab else DockTab.Build,
                        available = available,
                        onIntent = onIntent,
                        content = dockContent,
                    )
                }
            }
        }

        if (split) {
            var sidebarVisible by rememberSaveable { mutableStateOf(true) }
            var explorerWidth by rememberSaveable { mutableStateOf(Sizes.ExplorerPane.value) }
            var aiWidth by rememberSaveable { mutableStateOf(Sizes.AiPane.value) }
            LaunchedEffect(sidebarRequest) { if (sidebarRequest > 0) sidebarVisible = true }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                TopStudioBar(
                    state = state,
                    compact = false,
                    aiVisible = aiVisible,
                    showAiToggle = true,
                    onToggleSidebar = { sidebarVisible = !sidebarVisible },
                    onIntent = onIntent,
                    onToggleAi = { aiPaneVisible = !aiVisible },
                    menu = menu,
                )
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedVisibility(
                        sidebarVisible,
                        enter = expandHorizontally(Motion.spatial()) + fadeIn(Motion.switch()),
                        exit = shrinkHorizontally(Motion.spatial()) + fadeOut(Motion.switch()),
                    ) {
                        Row {
                            sidebar(Modifier.width(explorerWidth.dp).fillMaxHeight()) {}
                            PaneDivider { delta -> explorerWidth = (explorerWidth + delta).coerceIn(200f, 420f) }
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val available = maxHeight
                        Column(Modifier.fillMaxSize()) {
                            editorArea(Modifier.weight(1f))
                            bottom(available)
                        }
                    }
                    AnimatedVisibility(
                        aiVisible,
                        enter = expandHorizontally(Motion.spatial(), expandFrom = Alignment.End) + fadeIn(Motion.switch()),
                        exit = shrinkHorizontally(Motion.spatial(), shrinkTowards = Alignment.End) + fadeOut(Motion.switch()),
                    ) {
                        Row {
                            PaneDivider { delta -> aiWidth = (aiWidth - delta).coerceIn(280f, 520f) }
                            Box(Modifier.width(aiWidth.dp).fillMaxHeight().background(CodeinTheme.colors.tier1)) {
                                dockContent(DockTab.Ai, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            LaunchedEffect(sidebarRequest) { if (sidebarRequest > 0) drawerState.open() }
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(Sizes.DrawerWidth),
                        drawerContainerColor = CodeinTheme.colors.tier1,
                        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        sidebar(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) { scope.launch { drawerState.close() } }
                    }
                },
            ) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    TopStudioBar(
                        state = state,
                        compact = true,
                        aiVisible = false,
                        showAiToggle = false,
                        onToggleSidebar = { scope.launch { drawerState.open() } },
                        onIntent = onIntent,
                        onToggleAi = {},
                        menu = menu,
                    )
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val available = maxHeight
                        Column(Modifier.fillMaxSize()) {
                            editorArea(Modifier.weight(1f))
                            bottom(available)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Sizes.StatusBar + Spacing.lg))
    }

    if (state.paletteOpen) {
        val items = rememberPaletteItems(
            state = state,
            onIntent = onIntent,
            onOpenSettings = onOpenSettings,
            onOpenSigning = { signingOpen = true },
            onNewProject = onNewProject,
            toggleAi = { onIntent(WorkspaceIntent.ShowDock(DockTab.Ai)) },
        )
        key(state.paletteQuery) {
            CommandPalette(items = items, onDismiss = { onIntent(WorkspaceIntent.SetPalette(false)) }, initialQuery = state.paletteQuery)
        }
    }
    if (state.preferencesOpen) {
        EditorPreferencesSheet(prefs, onChange = { onIntent(WorkspaceIntent.Preference(it)) }, onDismiss = { onIntent(WorkspaceIntent.SetPreferences(false)) })
    }
    if (signingOpen) {
        val vault by vaultViewModel.state.collectAsStateWithLifecycle()
        SigningSheet(
            vault = vault,
            onVaultIntent = vaultViewModel::onIntent,
            build = state.build,
            onIntent = onIntent,
            onDismiss = { signingOpen = false },
        )
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

/** Unified sidebar: segmented Files / Search / Git / Maven tabs over the selected tool. */
@Composable
private fun WorkspaceSidebar(
    tab: SidebarTab,
    onTab: (SidebarTab) -> Unit,
    modifier: Modifier,
    content: @Composable (SidebarTab) -> Unit,
) {
    Column(modifier.background(CodeinTheme.colors.tier1)) {
        SegmentedTabs(
            items = SidebarTab.entries.toList(),
            selected = tab,
            onSelect = onTab,
            icon = {
                when (it) {
                    SidebarTab.Files -> CodeinIcons.Folder
                    SidebarTab.Search -> CodeinIcons.Regex
                    SidebarTab.Git -> CodeinIcons.GitBranch
                    SidebarTab.Maven -> CodeinIcons.Package
                }
            },
            label = { it.title },
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
        HairlineDivider()
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(Motion.switch()) togetherWith fadeOut(Motion.switch()) },
            label = "sidebar-tab",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { t -> Box(Modifier.fillMaxSize()) { content(t) } }
    }
}

/**
 * A 1dp vertical pane divider with a wider invisible grab area. While dragging it thickens to a
 * 2dp accent line.
 */
@Composable
private fun PaneDivider(onDrag: (Float) -> Unit) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val width by animateDpAsState(if (dragging) 2.dp else 1.dp, Motion.spatial(), label = "divider-width")
    val color by animateColorAsState(
        if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        Motion.switch(),
        label = "divider-color",
    )
    Box(Modifier.width(1.dp).fillMaxHeight().zIndex(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredWidth(Spacing.md)
                .fillMaxHeight()
                .draggable(
                    state = rememberDraggableState { delta -> onDrag(with(density) { delta.toDp().value }) },
                    orientation = Orientation.Horizontal,
                    interactionSource = interaction,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(width).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun EditorArea(
    state: WorkspaceState,
    prefs: EditorPreferences,
    controller: CodeEditorController,
    diagnostics: List<EditorDiagnostic>,
    onIntent: (WorkspaceIntent) -> Unit,
    onUndoRedo: (Boolean, Boolean) -> Unit,
    onOpenPalette: () -> Unit,
    modifier: Modifier,
) {
    val scheme = CodeinTheme.editorScheme
    var layoutPreview by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        EditorTabStrip(state.tabs, state.activeIndex, onIntent)
        val active = state.activeFile
        Breadcrumbs(active?.relativePath, active?.isDirty == true)
        // Switching files keeps the same editor view (it is expensive to recreate), so the 180ms
        // vertical slide + fade is applied as a graphics layer on the existing view.
        val switch = remember { Animatable(1f) }
        LaunchedEffect(active?.relativePath) {
            switch.snapTo(0f)
            switch.animateTo(1f, tween(Motion.SwitchMillis))
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(scheme.background)) {
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
                    scheme = scheme,
                    diagnostics = diagnostics,
                    onTextChanged = {
                        onIntent(WorkspaceIntent.TextChanged(it))
                        onUndoRedo(controller.canUndo(), controller.canRedo())
                    },
                    onCursorChanged = { onIntent(WorkspaceIntent.CursorChanged(it)) },
                    onDiagnosticTapped = { d ->
                        state.build.diagnostics.firstOrNull { it.line == d.line && it.message == d.message }?.let { onIntent(WorkspaceIntent.ShowDock(DockTab.Build)) }
                    },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val p = switch.value
                        alpha = 0.35f + 0.65f * p
                        translationY = (1f - p) * Spacing.sm.toPx()
                    },
                )
                FindReplaceBar(state.find, { onIntent(WorkspaceIntent.Find(it)) }, Modifier.align(Alignment.TopEnd))
                if (isLayoutFile(active.relativePath)) {
                    if (layoutPreview) LayoutPreview(active.content, state.projectDir, Modifier.fillMaxSize())
                    PillChip(
                        if (layoutPreview) "Show code" else "Preview layout",
                        icon = if (layoutPreview) CodeinIcons.Code else CodeinIcons.Eye,
                        selected = layoutPreview,
                        onClick = { layoutPreview = !layoutPreview },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
                    )
                }
            }
        }
    }
}

/**
 * The docked developer sheet. Collapsed it is a 38dp status ticker (build status, error/warning
 * counters, cursor Ln:Col and tab triggers); dragging the handle snaps it to 42% or 88% of the
 * available height on a no-bounce spring, with a haptic tick at every snap point.
 */
@Composable
private fun DeveloperDock(
    state: WorkspaceState,
    tabs: List<DockTab>,
    lastTab: DockTab,
    available: Dp,
    onIntent: (WorkspaceIntent) -> Unit,
    content: @Composable (DockTab, Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var openDetent by rememberSaveable { mutableStateOf(DockDetent.Half) }
    val currentTab = if (state.dockTab in tabs) state.dockTab else lastTab
    val detent = if (state.dockVisible && state.dockTab in tabs) openDetent else DockDetent.Collapsed

    val collapsedPx = with(density) { Sizes.StatusBar.toPx() }
    val availablePx = with(density) { available.toPx() }.coerceAtLeast(collapsedPx)
    fun heightFor(d: DockDetent): Float = when (d) {
        DockDetent.Collapsed -> collapsedPx
        DockDetent.Half -> (availablePx * Sizes.DockHalf).coerceAtLeast(collapsedPx)
        DockDetent.Expanded -> (availablePx * Sizes.DockFull).coerceAtLeast(collapsedPx)
    }
    val height = remember { Animatable(heightFor(detent)) }
    LaunchedEffect(detent, availablePx) { height.animateTo(heightFor(detent), Motion.spatial()) }

    fun settle(d: DockDetent) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (d) {
            DockDetent.Collapsed -> onIntent(WorkspaceIntent.HideDock)
            else -> {
                openDetent = d
                if (!state.dockVisible || state.dockTab !in tabs) onIntent(WorkspaceIntent.ShowDock(currentTab))
            }
        }
        scope.launch { height.animateTo(heightFor(d), Motion.spatial()) }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch { height.snapTo((height.value - delta).coerceIn(collapsedPx, heightFor(DockDetent.Expanded))) }
    }
    val dragModifier = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            val current = height.value
            val target = when {
                velocity < -1200f -> if (current < heightFor(DockDetent.Half)) DockDetent.Half else DockDetent.Expanded
                velocity > 1200f -> if (current > heightFor(DockDetent.Half)) DockDetent.Half else DockDetent.Collapsed
                else -> DockDetent.entries.minBy { abs(heightFor(it) - current) }
            }
            settle(target)
        },
    )
    val open = height.value > collapsedPx + 1f

    Column(
        Modifier
            .fillMaxWidth()
            .height(with(density) { height.value.toDp() })
            .background(CodeinTheme.colors.tier1),
    ) {
        HairlineDivider()
        Box(Modifier.fillMaxWidth().height(Sizes.StatusBar - 1.dp).then(dragModifier)) {
            DragHandle(Modifier.align(Alignment.TopCenter).padding(top = 2.dp), active = open)
            if (detent == DockDetent.Collapsed) {
                StatusTicker(state, tabs, onOpen = { tab -> onIntent(WorkspaceIntent.ShowDock(tab)) })
            } else {
                DockHeader(
                    tabs = tabs,
                    current = currentTab,
                    expanded = detent == DockDetent.Expanded,
                    onTab = { onIntent(WorkspaceIntent.ShowDock(it)) },
                    onToggleExpand = { settle(if (detent == DockDetent.Expanded) DockDetent.Half else DockDetent.Expanded) },
                    onCollapse = { settle(DockDetent.Collapsed) },
                )
            }
        }
        if (open) {
            HairlineDivider()
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn(Motion.switch()) togetherWith fadeOut(Motion.switch()) },
                label = "dock-tab",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { tab -> content(tab, Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun DockHeader(
    tabs: List<DockTab>,
    current: DockTab,
    expanded: Boolean,
    onTab: (DockTab) -> Unit,
    onToggleExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(start = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        SegmentedTabs(
            items = tabs,
            selected = current,
            onSelect = onTab,
            icon = { it.icon() },
            label = { it.title },
            modifier = Modifier.weight(1f).padding(top = Spacing.xs),
        )
        ToolbarIconButton(if (expanded) CodeinIcons.ChevronDown else CodeinIcons.ChevronUp, if (expanded) "Half height" else "Expand panel", size = 28.dp, onClick = onToggleExpand)
        ToolbarIconButton(CodeinIcons.Minus, "Collapse panel", size = 28.dp, onClick = onCollapse)
    }
}

/** Collapsed dock: build status, error/warning counters, cursor position and tab triggers. */
@Composable
private fun StatusTicker(state: WorkspaceState, tabs: List<DockTab>, onOpen: (DockTab) -> Unit) {
    val build = state.build
    val font = CodeinTheme.editorPreferences.codingFont
    val errors = state.errorCount
    val warnings = build.diagnostics.count { it.severity == Severity.Warning }
    Row(Modifier.fillMaxSize().padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(onClickLabel = "Open build") { onOpen(DockTab.Build) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BuildStatus(build, font)
            if (errors > 0 || warnings > 0) Spacer(Modifier.width(Spacing.md))
            if (errors > 0) Counter(CodeinIcons.AlertCircle, errors, MaterialTheme.colorScheme.error, font)
            if (warnings > 0) {
                Spacer(Modifier.width(Spacing.sm))
                Counter(CodeinIcons.AlertTriangle, warnings, CodeinTheme.colors.warning, font)
            }
        }
        if (state.activeFile != null) {
            Text(
                "Ln ${state.cursor.line + 1}:${state.cursor.column + 1}",
                style = metricsTextStyle(font, 11f),
                color = CodeinTheme.colors.textMuted,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        tabs.forEach { tab ->
            ToolbarIconButton(tab.icon(), "Open ${tab.title}", size = 28.dp, iconSize = Sizes.IconSm) { onOpen(tab) }
        }
    }
}

@Composable
private fun BuildStatus(build: BuildUiState, font: CodingFont) {
    val r = build.result
    val (color, label) = when {
        build.running -> MaterialTheme.colorScheme.primary to (build.currentStep?.let { stageLabel(it, build.variant) } ?: "Starting")
        r is BuildResult.Success -> CodeinTheme.colors.success to "Built in ${formatDuration(r.durationMillis)}"
        r is BuildResult.Failure -> MaterialTheme.colorScheme.error to "Failed at ${stageLabel(r.failedStep, build.variant)}"
        r is BuildResult.Cancelled -> CodeinTheme.colors.warning to "Cancelled"
        else -> CodeinTheme.colors.textMuted to "Ready · ${shortVariant(build.variant)}"
    }
    PulseDot(color, active = build.running)
    Spacer(Modifier.width(Spacing.sm))
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    val startedAt = build.startedAt
    if (build.running && startedAt != null) {
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(startedAt) {
            while (true) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(100)
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Text(formatDuration((now - startedAt).coerceAtLeast(0)), style = metricsTextStyle(font, 11f), color = CodeinTheme.colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun Counter(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, color: androidx.compose.ui.graphics.Color, font: CodingFont) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text("$count", style = metricsTextStyle(font, 11f), color = color)
    }
}

private fun DockTab.icon() = when (this) {
    DockTab.Build -> CodeinIcons.Hammer
    DockTab.Logcat -> CodeinIcons.ScrollLock
    DockTab.Terminal -> CodeinIcons.Terminal
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
    onOpenSigning: () -> Unit,
    onNewProject: () -> Unit,
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
            add(PaletteItem("a:search", "Search in project", "", PaletteCategory.Action, CodeinIcons.Regex, shortcut = "Ctrl+Shift+F") { onIntent(WorkspaceIntent.ShowDock(DockTab.Search)) })
            add(PaletteItem("a:ai", "Open Codein AI", "", PaletteCategory.Action, CodeinIcons.Sparkle) { toggleAi() })
            add(PaletteItem("a:logcat", "Show Logcat", "", PaletteCategory.Action, CodeinIcons.ScrollLock) { onIntent(WorkspaceIntent.ShowDock(DockTab.Logcat)) })
            add(PaletteItem("a:terminal", "Show Terminal", "Android shell in the project folder", PaletteCategory.Action, CodeinIcons.Terminal) { onIntent(WorkspaceIntent.ShowDock(DockTab.Terminal)) })
            add(PaletteItem("a:git", "Source control", "", PaletteCategory.Action, CodeinIcons.GitBranch) { onIntent(WorkspaceIntent.ShowDock(DockTab.Git)) })
            add(PaletteItem("a:deps", "Manage Maven dependencies", "", PaletteCategory.Action, CodeinIcons.Package) { onIntent(WorkspaceIntent.ShowDock(DockTab.Dependencies)) })
            add(PaletteItem("a:collapse", "Collapse all folders", "", PaletteCategory.Action, CodeinIcons.CollapseAll) { onIntent(WorkspaceIntent.CollapseAll) })
            add(PaletteItem("a:signing", "Signing & AAB export", "Keystores, SHA fingerprints, signed .aab", PaletteCategory.Build, CodeinIcons.Key) { onOpenSigning() })
            add(PaletteItem("a:new", "New project", "", PaletteCategory.Action, CodeinIcons.Plus) { onNewProject() })
            add(PaletteItem("a:prefs", "Editor appearance", "Font, size, line height, theme", PaletteCategory.Appearance, CodeinIcons.Palette) { onIntent(WorkspaceIntent.SetPreferences(true)) })
            add(PaletteItem("a:settings", "Settings & toolchain", "", PaletteCategory.Action, CodeinIcons.Settings) { onOpenSettings() })
            BuildVariant.entries.forEach { v ->
                add(PaletteItem("g:${v.name}", "Build target: ${shortVariant(v)}", v.displayName, PaletteCategory.Gradle, CodeinIcons.Layers) { onIntent(WorkspaceIntent.SetVariant(v)) })
                add(PaletteItem("g:run:${v.name}", "assemble ${v.displayName}", "Switch target and build", PaletteCategory.Gradle, CodeinIcons.Hammer) {
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
