package com.codein.ide.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.core.ui.components.BuildButtonState
import com.codein.ide.core.ui.components.BuildRunPill
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.theme.CodeinTheme

/** Top Studio Bar: drawer toggle, project pill, branch chip, variant picker, Build & Run, search, AI. */
@Composable
fun TopStudioBar(
    state: WorkspaceState,
    compact: Boolean,
    aiVisible: Boolean,
    onToggleSidebar: () -> Unit,
    onIntent: (WorkspaceIntent) -> Unit,
    onToggleAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseProject: () -> Unit,
) {
    var variantMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(CodeinTheme.colors.panel).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarIconButton(if (compact) CodeinIcons.Menu else CodeinIcons.Sidebar, "Toggle file explorer", onClick = onToggleSidebar)
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                PillChip(
                    state.projectName.ifEmpty { "Project" },
                    icon = CodeinIcons.BrandMark,
                    trailing = CodeinIcons.ChevronDown,
                    modifier = Modifier.widthIn(max = if (compact) 150.dp else 240.dp),
                    onClick = { projectMenu = true },
                )
                DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                    DropdownMenuItem(text = { Text("Save all") }, leadingIcon = { Icon(CodeinIcons.Save, null) }, onClick = { projectMenu = false; onIntent(WorkspaceIntent.SaveAll) })
                    DropdownMenuItem(text = { Text("Editor appearance") }, leadingIcon = { Icon(CodeinIcons.Palette, null) }, onClick = { projectMenu = false; onIntent(WorkspaceIntent.SetPreferences(true)) })
                    DropdownMenuItem(text = { Text("Settings & toolchain") }, leadingIcon = { Icon(CodeinIcons.Settings, null) }, onClick = { projectMenu = false; onOpenSettings() })
                    DropdownMenuItem(text = { Text("Close project") }, leadingIcon = { Icon(CodeinIcons.Close, null) }, onClick = { projectMenu = false; onCloseProject() })
                }
            }
            state.branch?.let { branch ->
                PillChip(branch, icon = CodeinIcons.GitBranch, accent = MaterialTheme.colorScheme.tertiary, onClick = { onIntent(WorkspaceIntent.ShowDock(DockTab.Git)) })
            }
            Box {
                PillChip(
                    if (compact) shortVariant(state.build.variant) else state.build.variant.displayName,
                    icon = if (state.build.variant.producesBundle) CodeinIcons.Package else CodeinIcons.Smartphone,
                    trailing = CodeinIcons.ChevronDown,
                    onClick = { variantMenu = true },
                )
                DropdownMenu(expanded = variantMenu, onDismissRequest = { variantMenu = false }) {
                    BuildVariant.entries.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.displayName, fontWeight = if (v == state.build.variant) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { Icon(if (v.producesBundle) CodeinIcons.Package else CodeinIcons.Smartphone, null) },
                            onClick = { variantMenu = false; onIntent(WorkspaceIntent.SetVariant(v)) },
                        )
                    }
                }
            }
        }
        BuildRunPill(
            state = buildButtonState(state.build),
            onBuild = { onIntent(WorkspaceIntent.Build) },
            onCancel = { onIntent(WorkspaceIntent.CancelBuild) },
            compact = compact,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ToolbarIconButton(CodeinIcons.Search, "Quick open (Ctrl+P)") { onIntent(WorkspaceIntent.SetPalette(true)) }
        ToolbarIconButton(CodeinIcons.Sparkle, "Codein AI", active = aiVisible, onClick = onToggleAi)
    }
}

private fun shortVariant(v: BuildVariant) = when (v) {
    BuildVariant.DebugApk -> "Debug"
    BuildVariant.ReleaseApk -> "Release"
    BuildVariant.PlayStoreAab -> "AAB"
}

fun buildButtonState(build: BuildUiState): BuildButtonState = when {
    build.running -> BuildButtonState.Running(build.progress.takeIf { it > 0f }, build.currentStep?.title ?: "Starting")
    build.result is BuildResult.Success -> BuildButtonState.Succeeded
    build.result is BuildResult.Failure -> BuildButtonState.Failed
    else -> BuildButtonState.Idle
}

/** Horizontally scrolling tab strip with dirty dots and close buttons; long-press closes others. */
@Composable
fun EditorTabStrip(tabs: List<OpenFile>, activeIndex: Int, onIntent: (WorkspaceIntent) -> Unit) {
    if (tabs.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) { if (activeIndex >= 0) listState.animateScrollToItem(activeIndex) }
    LazyRow(Modifier.fillMaxWidth().height(38.dp).background(CodeinTheme.colors.panel), state = listState) {
        itemsIndexed(tabs, key = { _, t -> t.relativePath }) { index, tab ->
            val active = index == activeIndex
            Row(
                Modifier
                    .fillMaxHeight()
                    .background(if (active) MaterialTheme.colorScheme.background else Color.Transparent)
                    .combinedClickable(
                        onClick = { onIntent(WorkspaceIntent.SelectTab(index)) },
                        onLongClick = { onIntent(WorkspaceIntent.CloseOtherTabs(index)) },
                    )
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileTypeBadge(tab.name, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    tab.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                if (tab.isDirty) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                ToolbarIconButton(CodeinIcons.Close, "Close ${tab.name}", size = 28.dp) { onIntent(WorkspaceIntent.CloseTab(index)) }
            }
        }
    }
}

/** Path breadcrumbs for the active file. */
@Composable
fun Breadcrumbs(relativePath: String?, cursorLine: Int, cursorColumn: Int, errorCount: Int) {
    if (relativePath == null) return
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            val parts = relativePath.split('/')
            parts.forEachIndexed { i, part ->
                Text(
                    part,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == parts.lastIndex) MaterialTheme.colorScheme.onSurface else CodeinTheme.colors.textMuted,
                )
                if (i < parts.lastIndex) Icon(CodeinIcons.ChevronRight, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.size(12.dp))
            }
        }
        if (errorCount > 0) {
            Icon(CodeinIcons.AlertCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text("$errorCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
        }
        Text("Ln ${cursorLine + 1}, Col ${cursorColumn + 1}", style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted)
    }
}
