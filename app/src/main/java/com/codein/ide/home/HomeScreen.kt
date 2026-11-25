package com.codein.ide.home

import com.codein.ide.core.ui.theme.CodeinShape
import com.codein.ide.core.ui.prefs.UiPrefs
import com.codein.ide.core.ui.i18n.tr
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.data.ProjectEntity

/** Landing screen: recent projects plus create, import and clone actions. */
@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var cloneDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::importFolder) }
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = runCatching {
            homeContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        viewModel.importZip(uri, name)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is HomeEvent.OpenProject -> onOpenProject(e.path)
                is HomeEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CodeinShape.Surface)
                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(CodeinIcons.BrandMark, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Codein"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(tr("Build Android apps on Android"), style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
                    }
                    ToolbarIconButton(CodeinIcons.Settings, tr("Settings"), onClick = onOpenSettings)
                }
            }
            item {
                if (busy != null) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        Text(busy.orEmpty(), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard("New project", "Compose or Views template", CodeinIcons.Plus, Modifier.weight(1f), enabled = busy == null, onClick = onNewProject)
                    ActionCard("Open folder", "Import via file picker", CodeinIcons.FolderOpen, Modifier.weight(1f), enabled = busy == null) { importLauncher.launch(null) }
                    ActionCard("Clone", "From a Git URL", CodeinIcons.GitBranch, Modifier.weight(1f), enabled = busy == null) { cloneDialog = true }
                }
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard("Import ZIP", "A zipped project or GitHub download", CodeinIcons.Archive, Modifier.weight(1f), enabled = busy == null) {
                        zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    }
                }
            }
            item {
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Recent projects"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    CountBadge(recent.size.toString(), MaterialTheme.colorScheme.primary)
                }
            }
            if (recent.isEmpty()) {
                item {
                    EmptyState(CodeinIcons.Folder, "No projects yet", "Create a project from a template, import a folder or clone a repository to get started.")
                }
            }
            items(recent, key = { it.path }) { project ->
                CodeinCard(Modifier.widthIn(max = 720.dp).fillMaxWidth(), onClick = { onOpenProject(project.path) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FileTypeBadge(if (project.language == "Java") "Main.java" else "Main.kt", size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(project.packageName, project.template, "API ${project.minSdk}–${project.targetSdk}").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = CodeinTheme.colors.textMuted,
                            )
                            Text(tr("Opened ") + DateUtils.getRelativeTimeSpanString(project.lastOpenedAt).toString().lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = CodeinTheme.colors.textMuted,
                            )
                        }
                        ToolbarIconButton(CodeinIcons.Trash, "Delete ${project.name}", size = 32.dp) { deleteTarget = project }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val tourSeen by UiPrefs.tourSeen.collectAsStateWithLifecycle()
    if (!tourSeen) {
        val context = androidx.compose.ui.platform.LocalContext.current
        FirstRunTour(onFinish = { UiPrefs.setTourSeen(context, true) })
    }

    if (cloneDialog) {
        var url by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cloneDialog = false },
            title = { Text(tr("Clone repository")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(url, { url = it }, singleLine = true, placeholder = { Text("https://github.com/user/app.git") })
                    Text(tr("Private repositories use the token saved in the Git panel's credentials."), style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted)
                }
            },
            confirmButton = { TextButton(onClick = { cloneDialog = false; viewModel.clone(url) }, enabled = url.startsWith("http")) { Text(tr("Clone")) } },
            dismissButton = { TextButton(onClick = { cloneDialog = false }) { Text(tr("Cancel")) } },
        )
    }
    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${project.name}?") },
            text = { Text(tr("The project folder and all its files are removed from this device. Pushed Git history is not affected.")) },
            confirmButton = { TextButton(onClick = { viewModel.delete(project); deleteTarget = null }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    CodeinCard(modifier, onClick = if (enabled) onClick else null) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(tr(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(tr(subtitle), style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted)
    }
}
