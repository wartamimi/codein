package com.codein.ide.workspace

import androidx.compose.foundation.background
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
    data class Delete(val row: TreeRow) : ExplorerDialog
}

/** Project file tree with Git status badges and a long-press context menu. */
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
    Column(modifier.fillMaxSize().background(CodeinTheme.colors.panel)) {
        PanelHeader(projectName.ifEmpty { "Explorer" }, icon = CodeinIcons.FolderOpen) {
            ToolbarIconButton(CodeinIcons.FilePlus, "New file", size = 32.dp) { dialog = ExplorerDialog.NewFile("") }
            ToolbarIconButton(CodeinIcons.FolderPlus, "New folder", size = 32.dp) { dialog = ExplorerDialog.NewFolder("") }
            ToolbarIconButton(CodeinIcons.Refresh, "Refresh", size = 32.dp) { onIntent(WorkspaceIntent.RefreshTree) }
        }
        Hairline()
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.relativePath }) { row ->
                val status = if (row.isDirectory) folderStatus[row.relativePath] else gitStatus[row.relativePath]
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(if (row.relativePath == activePath) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    if (row.isDirectory) onIntent(WorkspaceIntent.ToggleDirectory(row.relativePath))
                                    else { onIntent(WorkspaceIntent.OpenFile(row.relativePath)); onFileOpened() }
                                },
                                onLongClick = { menuFor = row.relativePath },
                            )
                            .padding(start = (8 + row.depth * 14).dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (row.isDirectory) {
                            FolderChevron(row.expanded, Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            FolderIcon(row.expanded)
                        } else {
                            Spacer(Modifier.width(16.dp))
                            FileTypeBadge(row.file.name, size = 16.dp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            row.file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = status?.color ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (status != null && !row.isDirectory) GitStatusBadge(status)
                        else if (status != null) Box(Modifier.size(6.dp).background(status.color, androidx.compose.foundation.shape.CircleShape))
                    }
                    DropdownMenu(expanded = menuFor == row.relativePath, onDismissRequest = { menuFor = null }) {
                        val parent = if (row.isDirectory) row.relativePath else row.relativePath.substringBeforeLast('/', "")
                        DropdownMenuItem(text = { Text("New file") }, leadingIcon = { Icon(CodeinIcons.FilePlus, null) }, onClick = { menuFor = null; dialog = ExplorerDialog.NewFile(parent) })
                        DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(CodeinIcons.FolderPlus, null) }, onClick = { menuFor = null; dialog = ExplorerDialog.NewFolder(parent) })
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(CodeinIcons.Type, null) }, onClick = { menuFor = null; dialog = ExplorerDialog.Rename(row) })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(CodeinIcons.Trash, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuFor = null; dialog = ExplorerDialog.Delete(row) })
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is ExplorerDialog.NewFile -> NameDialog("New file in /${d.parent}", "", "MyClass.kt", { dialog = null }) { onIntent(WorkspaceIntent.NewFile(d.parent, it)); dialog = null }
        is ExplorerDialog.NewFolder -> NameDialog("New folder in /${d.parent}", "", "utils", { dialog = null }) { onIntent(WorkspaceIntent.NewFolder(d.parent, it)); dialog = null }
        is ExplorerDialog.Rename -> NameDialog("Rename ${d.row.file.name}", d.row.file.name, "", { dialog = null }) { onIntent(WorkspaceIntent.Rename(d.row.relativePath, it)); dialog = null }
        is ExplorerDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${d.row.file.name}?") },
            text = { Text(if (d.row.isDirectory) "The folder and everything inside it will be deleted." else "The file will be deleted. Unsaved changes are lost.") },
            confirmButton = { TextButton(onClick = { onIntent(WorkspaceIntent.Delete(d.row.relativePath)); dialog = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
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
                supportingText = { if (value.isNotEmpty() && invalid) Text("Names cannot contain slashes") },
                modifier = Modifier.padding(top = 4.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = !invalid) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
