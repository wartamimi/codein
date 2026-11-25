package com.codein.ide.workspace

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.core.ui.components.BuildButtonState
import com.codein.ide.core.ui.components.BuildRunPill
import com.codein.ide.core.ui.components.HairlineDivider
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing

/** Actions behind the Top Studio Bar's overflow menu. */
data class StudioMenuActions(
    val onSigning: () -> Unit,
    val onEditorSettings: () -> Unit,
    val onNewProject: () -> Unit,
    val onSettings: () -> Unit,
    val onCloseProject: () -> Unit,
)

/**
 * The 52dp Top Studio Bar. Left: drawer/sidebar toggle and a compact `Project • branch` pill.
 * Right: build target chip, the morphing Build & Run pill, the command palette and an overflow menu.
 */
@Composable
fun TopStudioBar(
    state: WorkspaceState,
    compact: Boolean,
    aiVisible: Boolean,
    showAiToggle: Boolean,
    onToggleSidebar: () -> Unit,
    onIntent: (WorkspaceIntent) -> Unit,
    onToggleAi: () -> Unit,
    menu: StudioMenuActions,
) {
    Column(Modifier.fillMaxWidth().background(CodeinTheme.colors.tier1)) {
        Row(
            Modifier.fillMaxWidth().height(Sizes.TopBar).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ToolbarIconButton(if (compact) CodeinIcons.Menu else CodeinIcons.Sidebar, "Toggle explorer", size = 36.dp, onClick = onToggleSidebar)
                ProjectBranchPill(
                    project = state.projectName.ifEmpty { "Project" },
                    branch = state.branch,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = { onIntent(WorkspaceIntent.ShowDock(DockTab.Git)) },
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            BuildTargetChip(state.build.variant, compact) { onIntent(WorkspaceIntent.SetVariant(it)) }
            Spacer(Modifier.width(Spacing.sm))
            BuildRunPill(
                state = buildButtonState(state.build),
                onBuild = { onIntent(WorkspaceIntent.Build) },
                onCancel = { onIntent(WorkspaceIntent.CancelBuild) },
                compact = compact,
            )
            ToolbarIconButton(CodeinIcons.Search, "Command palette (Ctrl+P)") { onIntent(WorkspaceIntent.SetPalette(true)) }
            if (showAiToggle) ToolbarIconButton(CodeinIcons.Sparkle, "Codein AI", active = aiVisible, onClick = onToggleAi)
            OverflowMenu(menu, onSaveAll = { onIntent(WorkspaceIntent.SaveAll) })
        }
        HairlineDivider()
    }
}

/** `Codein • main` pill: project name plus the checked-out Git branch. */
@Composable
private fun ProjectBranchPill(project: String, branch: String?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(Sizes.Pill)
            .clip(CircleShape)
            .background(CodeinTheme.colors.tier2)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClickLabel = "Source control", onClick = onClick)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CodeinIcons.BrandMark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.IconSm))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            project,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (branch != null) {
            Text(" • ", style = MaterialTheme.typography.labelLarge, color = CodeinTheme.colors.textMuted)
            Icon(CodeinIcons.GitBranch, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                branch,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

/** Build target selector chip: Debug / Release / AAB. */
@Composable
private fun BuildTargetChip(variant: BuildVariant, compact: Boolean, onSelect: (BuildVariant) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(Sizes.Pill)
                .clip(RoundedCornerShape(8.dp))
                .background(CodeinTheme.colors.tier2)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Build target") { open = true }
                .padding(start = Spacing.sm, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (variant.producesBundle) CodeinIcons.Package else CodeinIcons.Smartphone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(if (compact) shortVariant(variant) else variant.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(CodeinIcons.ChevronDown, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CodeinTheme.colors.tier2) {
            BuildVariant.entries.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(shortVariant(v), fontWeight = if (v == variant) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                            Text(v.displayName, style = MaterialTheme.typography.labelMedium, color = CodeinTheme.colors.textMuted)
                        }
                    },
                    leadingIcon = { Icon(if (v.producesBundle) CodeinIcons.Package else CodeinIcons.Smartphone, null, modifier = Modifier.size(Sizes.Icon)) },
                    trailingIcon = if (v == variant) ({ Icon(CodeinIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.IconSm)) }) else null,
                    onClick = { open = false; onSelect(v) },
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(menu: StudioMenuActions, onSaveAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIconButton(CodeinIcons.MoreVertical, "More actions") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CodeinTheme.colors.tier2) {
            MenuItem("Signing & AAB export", CodeinIcons.Key) { open = false; menu.onSigning() }
            MenuItem("Editor settings", CodeinIcons.Palette) { open = false; menu.onEditorSettings() }
            MenuItem("New project", CodeinIcons.Plus) { open = false; menu.onNewProject() }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
            MenuItem("Save all", CodeinIcons.Save) { open = false; onSaveAll() }
            MenuItem("Settings & toolchain", CodeinIcons.Settings) { open = false; menu.onSettings() }
            MenuItem("Close project", CodeinIcons.Close) { open = false; menu.onCloseProject() }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(Sizes.Icon)) },
        onClick = onClick,
    )
}

fun shortVariant(v: BuildVariant) = when (v) {
    BuildVariant.DebugApk -> "Debug"
    BuildVariant.ReleaseApk -> "Release"
    BuildVariant.PlayStoreAab -> "AAB"
}

fun buildButtonState(build: BuildUiState): BuildButtonState = when {
    build.running -> BuildButtonState.Running(build.progress.takeIf { it > 0f }, build.currentStep?.let { stageLabel(it) } ?: "Starting")
    build.result is BuildResult.Success -> BuildButtonState.Succeeded
    build.result is BuildResult.Failure -> BuildButtonState.Failed
    else -> BuildButtonState.Idle
}

/**
 * The 36dp file tab strip. Tabs have 12dp horizontal padding, 16dp file-type icons and a 44dp close
 * target; an accent indicator glides between tabs on a no-bounce spring. Long-press closes others.
 */
@Composable
fun EditorTabStrip(tabs: List<OpenFile>, activeIndex: Int, onIntent: (WorkspaceIntent) -> Unit) {
    if (tabs.isEmpty()) return
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // x offset and width (px) of every placed tab, so the indicator can glide to the active one.
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Int>>() }
    LaunchedEffect(activeIndex) { if (activeIndex >= 0) listState.animateScrollToItem(activeIndex) }
    val activeKey = tabs.getOrNull(activeIndex)?.relativePath
    val target = activeKey?.let { bounds[it] }
    val indicatorX by animateDpAsState(with(density) { (target?.first ?: 0f).toDp() }, Motion.spatial(), label = "tab-x")
    val indicatorW by animateDpAsState(with(density) { (target?.second ?: 0).toDp() }, Motion.spatial(), label = "tab-w")
    Column(Modifier.fillMaxWidth().background(CodeinTheme.colors.tier1)) {
        Box(Modifier.fillMaxWidth().height(Sizes.TabStrip - 1.dp)) {
            LazyRow(Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(tabs, key = { _, t -> t.relativePath }) { index, tab ->
                    EditorTab(
                        tab = tab,
                        active = index == activeIndex,
                        onSelect = {
                            if (index != activeIndex) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onIntent(WorkspaceIntent.SelectTab(index))
                        },
                        onCloseOthers = { onIntent(WorkspaceIntent.CloseOtherTabs(index)) },
                        onClose = { onIntent(WorkspaceIntent.CloseTab(index)) },
                        modifier = Modifier.onPlaced { c -> bounds[tab.relativePath] = c.positionInParent().x to c.size.width },
                    )
                }
            }
            if (target != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = indicatorX)
                        .width(indicatorW)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        HairlineDivider()
    }
}

@Composable
private fun EditorTab(
    tab: OpenFile,
    active: Boolean,
    onSelect: () -> Unit,
    onCloseOthers: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val bg by animateColorAsState(if (active) CodeinTheme.colors.base else Color.Transparent, Motion.switch(), label = "tab-bg")
    Row(
        modifier
            .fillMaxHeight()
            .background(bg)
            .combinedClickable(onClickLabel = "Show ${tab.name}", onLongClickLabel = "Close other tabs", onClick = onSelect, onLongClick = onCloseOthers)
            .semantics {
                selected = active
                stateDescription = if (tab.isDirty) "unsaved changes" else ""
            }
            .padding(start = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeBadge(tab.name, size = Sizes.IconSm)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            tab.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        // Dirty tabs show a dot that turns into the close glyph when the tab is active.
        Box(contentAlignment = Alignment.Center) {
            ToolbarIconButton(CodeinIcons.Close, "Close ${tab.name}", size = 24.dp, iconSize = 14.dp, tint = if (tab.isDirty && !active) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant, onClick = onClose)
            val dot by androidx.compose.animation.core.animateFloatAsState(if (tab.isDirty && !active) 1f else 0f, Motion.spatial(), label = "dirty-dot")
            if (dot > 0.01f) {
                Box(
                    Modifier
                        .size(Spacing.sm)
                        .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** The 26dp breadcrumb bar showing the active file's path. */
@Composable
fun Breadcrumbs(relativePath: String?, dirty: Boolean) {
    if (relativePath == null) return
    Row(
        Modifier.fillMaxWidth().height(Sizes.Breadcrumb).background(CodeinTheme.colors.base).padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            val parts = relativePath.split('/')
            parts.forEachIndexed { i, part ->
                Text(
                    part,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == parts.lastIndex) MaterialTheme.colorScheme.onSurface else CodeinTheme.colors.textMuted,
                    maxLines = 1,
                )
                if (i < parts.lastIndex) {
                    Icon(CodeinIcons.ChevronRight, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 2.dp).size(12.dp))
                }
            }
        }
        if (dirty) Text("Unsaved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Width helper so callers can reserve the same gutter as icon buttons. */
internal val TouchGutter: Dp = Sizes.MinTouch
