package com.codein.ide.workspace

import com.codein.ide.core.ui.theme.CodeinShape
import com.codein.ide.core.ui.i18n.tr
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.HairlineDivider
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.Hairline
import com.codein.ide.core.ui.components.PanelHeader
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.icons.FolderChevron
import com.codein.ide.core.ui.icons.FolderIcon
import com.codein.ide.core.ui.icons.GitFileStatus
import com.codein.ide.core.ui.icons.GitStatusBadge
import com.codein.ide.core.ui.theme.CodeinTheme

private sealed interface ExplorerDialog {
    data class NewFile(val parent: String) : ExplorerDialog
    data class NewFolder(val parent: String) : ExplorerDialog
    data class Rename(val row: TreeRow) : ExplorerDialog
    data class Move(val row: TreeRow) : ExplorerDialog
    data class FromTemplate(val parent: String, val kind: FileTemplateKind) : ExplorerDialog
    data class Delete(val row: TreeRow) : ExplorerDialog
}

/**
 * Project file tree: 32dp rows, 16dp indentation with hairline guides per depth, 16dp color-coded
 * file icons, right-aligned Git badges, a quick-action bar and a long-press context menu.
 */
@Composable
fun FileExplorer(
    projectName: String,
    rows: List<TreeRow>,
    gitStatus: Map<String, GitFileStatus>,
    activePath: String?,
    onIntent: (WorkspaceIntent) -> Unit,
    onFileOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    // Folder status = strongest status of any descendant.
    val folderStatus = remember(gitStatus) {
        val map = HashMap<String, GitFileStatus>()
        gitStatus.forEach { (path, status) ->
            var p = path.substringBeforeLast('/', "")
            while (p.isNotEmpty()) {
                val prev = map[p]
                if (prev == null || status.ordinal > prev.ordinal) map[p] = status
                p = p.substringBeforeLast('/', "")
            }
        }
        map
    }
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val activeAccent = MaterialTheme.colorScheme.primary
    val activeBg = CodeinTheme.colors.tier3
    Column(modifier.fillMaxSize()) {
        PanelHeader(projectName.ifEmpty { "Explorer" }, icon = CodeinIcons.FolderOpen) {
            ToolbarIconButton(CodeinIcons.FilePlus, tr("New file"), size = 28.dp) { dialog = ExplorerDialog.NewFile("") }
            ToolbarIconButton(CodeinIcons.FolderPlus, tr("New folder"), size = 28.dp) { dialog = ExplorerDialog.NewFolder("") }
            ToolbarIconButton(CodeinIcons.CollapseAll, tr("Collapse all"), size = 28.dp) { onIntent(WorkspaceIntent.CollapseAll) }
            ToolbarIconButton(CodeinIcons.Refresh, tr("Refresh"), size = 28.dp) { onIntent(WorkspaceIntent.RefreshTree) }
        }
        HairlineDivider()
        if (rows.isEmpty()) {
            EmptyState(CodeinIcons.Folder, "Empty project", "Create a file or folder with the buttons above.", Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            items(rows, key = { it.relativePath }) { row ->
                val status = if (row.isDirectory) folderStatus[row.relativePath] else gitStatus[row.relativePath]
                val active = row.relativePath == activePath
                Box(Modifier.animateItem()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(Sizes.TreeRow)
                            .padding(horizontal = Spacing.xs)
                            .clip(CodeinShape.Surface)
                            .background(if (active) activeBg else Color.Transparent)
                            .combinedClickable(
                                onClickLabel = if (row.isDirectory) (if (row.expanded) "Collapse" else "Expand") else "Open",
                                onLongClickLabel = "File actions",
                                onClick = {
                                    if (row.isDirectory) onIntent(WorkspaceIntent.ToggleDirectory(row.relativePath))
                                    else { onIntent(WorkspaceIntent.OpenFile(row.relativePath)); onFileOpened() }
                                },
                                onLongClick = { menuFor = row.relativePath },
                            )
                            .semantics(mergeDescendants = true) {
                                stateDescription = listOfNotNull(
                                    if (row.isDirectory) (if (row.expanded) "expanded folder" else "collapsed folder") else null,
                                    gitStatus[row.relativePath]?.name?.lowercase(),
                                    if (active) "open in editor" else null,
                                ).joinToString(", ")
                            }
                            .drawBehind {
                                // One hairline guide per ancestor directory, centred under its chevron.
                                val indent = Sizes.TreeIndent.toPx()
                                val start = Spacing.sm.toPx() + indent / 2
                                for (d in 0 until row.depth) {
                                    val x = start + d * indent
                                    drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                                }
                                if (active) {
                                    drawRect(activeAccent, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                                }
                            }
                            .padding(start = Spacing.sm + Sizes.TreeIndent * row.depth, end = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (row.isDirectory) {
                            FolderChevron(row.expanded, Modifier.size(Sizes.IconSm))
                            Spacer(Modifier.width(Spacing.xs))
                            FolderIcon(row.expanded, Modifier.size(Sizes.IconSm))
                        } else {
                            Spacer(Modifier.width(Sizes.IconSm + Spacing.xs))
                            FileTypeBadge(row.file.name, size = Sizes.IconSm)
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            row.file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = status?.color ?: if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (status != null && !row.isDirectory) {
                            Box(
                                Modifier.size(Sizes.IconSm).clip(CodeinShape.Surface).background(status.color.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) { GitStatusBadge(status) }
                        } else if (status != null) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(status.color))
                        }
                    }
                    DropdownMenu(expanded = menuFor == row.relativePath, onDismissRequest = { menuFor = null }, containerColor = CodeinTheme.colors.tier2) {
                        val parent = if (row.isDirectory) row.relativePath else row.relativePath.substringBeforeLast('/', "")
                        DropdownMenuItem(text = { Text(tr("New file")) }, leadingIcon = { Icon(CodeinIcons.FilePlus, null, Modifier.size(Sizes.Icon)) }, onClick = { menuFor = null; dialog = ExplorerDialog.NewFile(parent) })
                        DropdownMenuItem(text = { Text(tr("New folder")) }, leadingIcon = { Icon(CodeinIcons.FolderPlus, null, Modifier.size(Sizes.Icon)) }, onClick = { menuFor = null; dialog = ExplorerDialog.NewFolder(parent) })
                        FileTemplateKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text("New ${kind.title}") },
                                leadingIcon = { Icon(CodeinIcons.Code, null, Modifier.size(Sizes.Icon)) },
                                onClick = { menuFor = null; dialog = ExplorerDialog.FromTemplate(parent, kind) },
                            )
                        }
                        DropdownMenuItem(text = { Text(tr("Rename")) }, leadingIcon = { Icon(CodeinIcons.Type, null, Modifier.size(Sizes.Icon)) }, onClick = { menuFor = null; dialog = ExplorerDialog.Rename(row) })
                        DropdownMenuItem(text = { Text(tr("Move to…")) }, leadingIcon = { Icon(CodeinIcons.Folder, null, Modifier.size(Sizes.Icon)) }, onClick = { menuFor = null; dialog = ExplorerDialog.Move(row) })
                        if (!row.isDirectory) {
                            DropdownMenuItem(text = { Text(tr("Local history")) }, leadingIcon = { Icon(CodeinIcons.History, null, Modifier.size(Sizes.Icon)) }, onClick = { menuFor = null; onIntent(WorkspaceIntent.ShowHistory(row.relativePath)) })
                        }
                        DropdownMenuItem(text = { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(CodeinIcons.Trash, null, Modifier.size(Sizes.Icon), tint = MaterialTheme.colorScheme.error) }, onClick = { menuFor = null; dialog = ExplorerDialog.Delete(row) })
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is ExplorerDialog.NewFile -> NameDialog("New file in /${d.parent}", "", "MyClass.kt", { dialog = null }) { onIntent(WorkspaceIntent.NewFile(d.parent, it)); dialog = null }
        is ExplorerDialog.NewFolder -> NameDialog("New folder in /${d.parent}", "", "utils", { dialog = null }) { onIntent(WorkspaceIntent.NewFolder(d.parent, it)); dialog = null }
        is ExplorerDialog.FromTemplate -> NameDialog("New ${d.kind.title} in /${d.parent}", "", d.kind.hint, { dialog = null }) {
            onIntent(WorkspaceIntent.NewFromTemplate(d.parent, d.kind, it)); dialog = null
        }
        is ExplorerDialog.Move -> MoveDialog(d.row, folders = rows.filter { it.isDirectory }.map { it.relativePath }, onDismiss = { dialog = null }) { target ->
            onIntent(WorkspaceIntent.Move(d.row.relativePath, target)); dialog = null
        }
        is ExplorerDialog.Rename -> NameDialog("Rename ${d.row.file.name}", d.row.file.name, "", { dialog = null }) { onIntent(WorkspaceIntent.Rename(d.row.relativePath, it)); dialog = null }
        is ExplorerDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${d.row.file.name}?") },
            text = { Text(if (d.row.isDirectory) "The folder and everything inside it will be deleted." else "The file will be deleted. Unsaved changes are lost.") },
            confirmButton = { TextButton(onClick = { onIntent(WorkspaceIntent.Delete(d.row.relativePath)); dialog = null }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Cancel")) } },
        )
        null -> Unit
    }
}

/** Picks the destination folder for "Move to…": type a path or pick a visible folder. */
@Composable
private fun MoveDialog(row: TreeRow, folders: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(row.relativePath.substringBeforeLast('/', "")) }
    val invalid = value.contains("..") || value.contains('\\')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${row.file.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(tr("Destination folder")) },
                    placeholder = { Text("app/src/main/java/…") },
                    singleLine = true,
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    folders.filter { it != row.relativePath && !it.startsWith(row.relativePath + "/") }.forEach { folder ->
                        Text(
                            "/$folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (folder == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { value = folder }.padding(vertical = Spacing.xs),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim().trim('/')) }, enabled = !invalid) { Text(tr("Move")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, placeholder: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val invalid = value.isBlank() || value.contains('/') || value.contains('\\')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                isError = value.isNotEmpty() && invalid,
                supportingText = { if (value.isNotEmpty() && invalid) Text(tr("Names cannot contain slashes")) },
                modifier = Modifier.padding(top = 4.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = !invalid) { Text(tr("OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
