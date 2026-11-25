package com.codein.ide.vcs.ui

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.Hairline
import com.codein.ide.core.ui.components.PanelHeader
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.icons.GitStatusBadge
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.codeTextStyle

/** Source control panel: branch switcher, changes with stage toggles, inline diff, commit, push/pull, history. */
@Composable
fun GitPanel(state: GitPanelState, onIntent: (GitIntent) -> Unit, onOpenFile: (String) -> Unit, modifier: Modifier = Modifier) {
    var showRemote by remember { mutableStateOf(false) }
    var showCredentials by remember { mutableStateOf(false) }
    var showNewBranch by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        PanelHeader("Source control", icon = CodeinIcons.GitBranch) {
            if (state.status.isRepository) {
                ToolbarIconButton(CodeinIcons.GitPull, "Pull", size = 32.dp, enabled = state.busy == null && state.status.remoteUrl != null) { onIntent(GitIntent.Pull) }
                ToolbarIconButton(CodeinIcons.GitPush, "Push", size = 32.dp, enabled = state.busy == null && state.status.remoteUrl != null && state.status.hasCommits) { onIntent(GitIntent.Push) }
            }
            ToolbarIconButton(CodeinIcons.Key, "Credentials", size = 32.dp) { showCredentials = true }
            ToolbarIconButton(CodeinIcons.Refresh, "Refresh", size = 32.dp) { onIntent(GitIntent.Refresh) }
        }
        if (state.busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            Text(state.busy + "…", style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        Hairline()
        if (!state.status.isRepository) {
            EmptyState(
                icon = CodeinIcons.GitBranch,
                title = "No Git repository",
                message = "Initialize a repository to track changes, commit and push to GitHub, GitLab or any HTTPS remote.",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = { onIntent(GitIntent.Init) }, enabled = state.busy == null) { Text("Initialize repository") }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item { BranchRow(state, onIntent, onNewBranch = { showNewBranch = true }, onRemote = { showRemote = true }) }
                item { CommitBox(state, onIntent) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Changes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        CountBadge(state.status.files.size.toString(), MaterialTheme.colorScheme.primary)
                        if (state.status.files.isNotEmpty()) TextButton(onClick = { onIntent(GitIntent.StageAll) }) { Text("Stage all") }
                    }
                }
                if (state.status.isClean) {
                    item { Text("Working tree clean", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                }
                items(state.status.files.entries.toList(), key = { it.key }) { (path, status) ->
                    ChangeRow(
                        path = path,
                        status = status,
                        staged = path in state.status.staged,
                        selected = path == state.selectedDiffPath,
                        onToggleStage = { onIntent(GitIntent.ToggleStage(path)) },
                        onShowDiff = { onIntent(GitIntent.ShowDiff(if (path == state.selectedDiffPath) null else path)) },
                        onOpen = { onOpenFile(path) },
                        onDiscard = { onIntent(GitIntent.Discard(path)) },
                    )
                    if (path == state.selectedDiffPath) DiffView(state.diffText)
                }
                if (state.log.isNotEmpty()) {
                    item { Text("History", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)) }
                    items(state.log, key = { "c" + it.id }) { commit ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(CodeinIcons.GitCommit, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(commit.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${commit.shortId} · ${commit.author} · ${commit.relativeTime}", style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
    state.review?.let { review -> CommitReviewDialog(review, state.commitMessage, onIntent) }
    if (showRemote) {
        SingleFieldDialog("Remote origin URL", state.status.remoteUrl.orEmpty(), "https://github.com/user/repo.git", KeyboardType.Uri, onDismiss = { showRemote = false }) {
            onIntent(GitIntent.SetRemote(it)); showRemote = false
        }
    }
    if (showNewBranch) {
        SingleFieldDialog("New branch", "", "feature/my-change", KeyboardType.Text, onDismiss = { showNewBranch = false }) {
            onIntent(GitIntent.CreateBranch(it)); showNewBranch = false
        }
    }
    if (showCredentials) {
        CredentialsDialog(
            state,
            onDismiss = { showCredentials = false },
            onSave = { user, token, name, email -> onIntent(GitIntent.SaveCredentials(user, token, name, email)); showCredentials = false },
            onSsh = onIntent,
        )
    }
}

@Composable
private fun BranchRow(state: GitPanelState, onIntent: (GitIntent) -> Unit, onNewBranch: () -> Unit, onRemote: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            PillChip(state.status.branch ?: "detached", icon = CodeinIcons.GitBranch, trailing = CodeinIcons.ChevronDown, selected = true, onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                state.branches.forEach { b ->
                    DropdownMenuItem(
                        text = { Text(b.name + if (b.isRemote) " (remote)" else "", fontWeight = if (b.isCurrent) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menu = false; if (!b.isCurrent) onIntent(GitIntent.Checkout(b.name)) },
                        leadingIcon = { if (b.isCurrent) Icon(CodeinIcons.Check, null, modifier = Modifier.size(16.dp)) },
                    )
                }
                DropdownMenuItem(text = { Text("New branch…") }, onClick = { menu = false; onNewBranch() }, leadingIcon = { Icon(CodeinIcons.Plus, null, modifier = Modifier.size(16.dp)) })
            }
        }
        if (state.status.ahead > 0) CountBadge("↑${state.status.ahead}", CodeinTheme.colors.success)
        if (state.status.behind > 0) CountBadge("↓${state.status.behind}", CodeinTheme.colors.warning)
        PillChip(
            state.status.remoteUrl?.substringAfter("://")?.removeSuffix(".git") ?: "Add remote",
            icon = CodeinIcons.Share,
            onClick = onRemote,
        )
    }
}

@Composable
private fun CommitBox(state: GitPanelState, onIntent: (GitIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = state.commitMessage,
            onValueChange = { onIntent(GitIntent.CommitMessage(it)) },
            placeholder = { Text("Commit message") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val canCommit = state.commitMessage.isNotBlank() && state.busy == null
            Button(onClick = { onIntent(GitIntent.ReviewCommit(stageAll = false)) }, enabled = canCommit && state.status.staged.isNotEmpty()) { Text("Review & commit staged") }
            TextButton(onClick = { onIntent(GitIntent.ReviewCommit(stageAll = true)) }, enabled = canCommit && state.status.files.isNotEmpty()) { Text("Commit all…") }
        }
    }
}

@Composable
private fun ChangeRow(
    path: String,
    status: com.codein.ide.core.ui.icons.GitFileStatus,
    staged: Boolean,
    selected: Boolean,
    onToggleStage: () -> Unit,
    onShowDiff: () -> Unit,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onShowDiff)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = staged, onCheckedChange = { onToggleStage() })
        FileTypeBadge(path.substringAfterLast('/'))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (path.contains('/')) {
                Text(path.substringBeforeLast('/'), style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ToolbarIconButton(CodeinIcons.File, "Open file", size = 30.dp, onClick = onOpen)
        ToolbarIconButton(CodeinIcons.Undo, "Discard changes", size = 30.dp) { confirmDiscard = true }
        GitStatusBadge(status, Modifier.padding(end = 6.dp))
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("This restores $path to the last commit (or deletes it if untracked). It cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
}

/** Full-screen check of every change a commit would include, with the commit button at the bottom. */
@Composable
private fun CommitReviewDialog(review: CommitReview, message: String, onIntent: (GitIntent) -> Unit) {
    var expanded by remember(review) { mutableStateOf(if (review.files.size <= 3) review.files.map { it.path }.toSet() else emptySet()) }
    Dialog(onDismissRequest = { onIntent(GitIntent.CancelReview) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                PanelHeader("Review commit", icon = CodeinIcons.GitCommit) {
                    ToolbarIconButton(CodeinIcons.Close, "Cancel", size = 32.dp) { onIntent(GitIntent.CancelReview) }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(message.trim(), style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${review.files.size} file(s) · +${review.added} −${review.removed}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CodeinTheme.colors.textMuted,
                    )
                }
                Hairline()
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(review.files, key = { it.path }) { file ->
                        val open = file.path in expanded
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(onClickLabel = if (open) "Hide diff" else "Show diff") { expanded = if (open) expanded - file.path else expanded + file.path }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (open) CodeinIcons.ChevronDown else CodeinIcons.ChevronRight, null, Modifier.size(16.dp), tint = CodeinTheme.colors.textMuted)
                            Spacer(Modifier.width(6.dp))
                            FileTypeBadge(file.path.substringAfterLast('/'))
                            Spacer(Modifier.width(8.dp))
                            Text(file.path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("+${file.added}", style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.success)
                            Spacer(Modifier.width(6.dp))
                            Text("−${file.removed}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (open) DiffView(file.diff)
                    }
                }
                Hairline()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { onIntent(GitIntent.CancelReview) }) { Text("Back") }
                    Button(onClick = { onIntent(GitIntent.Commit(review.stageAll)) }, enabled = review.files.isNotEmpty()) {
                        Text("Commit ${review.files.size} file(s)")
                    }
                }
            }
        }
    }
}

/** Colored unified diff (+ green, − red, @@ muted), scrollable sideways; shared with local history. */
@Composable
fun DiffView(diff: String) {
    val prefs = CodeinTheme.editorPreferences
    val style = codeTextStyle(prefs.codingFont, 11.5f, false)
    val add = CodeinTheme.colors.success
    val remove = MaterialTheme.colorScheme.error
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CodeinTheme.editorScheme.background)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        if (diff.isBlank()) Text("No textual changes", style = style, color = CodeinTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 10.dp))
        diff.lineSequence().take(800).forEach { line ->
            val color = when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") -> CodeinTheme.colors.textMuted
                line.startsWith("+") -> add
                line.startsWith("-") -> remove
                line.startsWith("@@") -> CodeinTheme.editorScheme.comment
                else -> CodeinTheme.editorScheme.foreground
            }
            val bg = when {
                line.startsWith("+") && !line.startsWith("+++") -> add.copy(alpha = 0.10f)
                line.startsWith("-") && !line.startsWith("---") -> remove.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
            Text(line, style = style, color = color, softWrap = false, modifier = Modifier.background(bg).padding(horizontal = 10.dp))
        }
    }
}

@Composable
private fun SingleFieldDialog(title: String, initial: String, placeholder: String, keyboard: KeyboardType, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value, { value = it }, singleLine = true, placeholder = { Text(placeholder) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CredentialsDialog(
    state: GitPanelState,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, String) -> Unit,
    onSsh: (GitIntent) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val c = state.credentials
    var user by rememberSaveable { mutableStateOf(c.username) }
    var token by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(c.authorName) }
    var email by rememberSaveable { mutableStateOf(c.authorEmail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Git identity & credentials") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Author name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Author email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(user, { user = it }, label = { Text("HTTPS username") }, singleLine = true)
                OutlinedTextField(
                    token,
                    { token = it },
                    label = { Text(if (c.token.isNotEmpty()) "Replace access token" else "Personal access token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text("Tokens are encrypted with the Android Keystore.", style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted)
                Hairline()
                Text("SSH key (for git@host:user/repo.git remotes)", style = MaterialTheme.typography.titleSmall)
                val publicKey = state.sshPublicKey
                if (publicKey == null) {
                    Text("No key yet. Codein generates an RSA-3072 key stored privately on this device.", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
                    Button(onClick = { onSsh(GitIntent.GenerateSshKey) }, enabled = state.busy == null) { Text("Generate SSH key") }
                } else {
                    Text(
                        publicKey,
                        style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 10.5f, false),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp),
                    )
                    Text("Add this public key to GitHub (Settings > SSH and GPG keys) or your Git host.", style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { clipboard.setText(AnnotatedString(publicKey)) }) { Text("Copy key") }
                        TextButton(onClick = { onSsh(GitIntent.ClearKnownHosts) }) { Text("Forget hosts") }
                        TextButton(onClick = { onSsh(GitIntent.DeleteSshKey) }) { Text("Delete") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(user, token.ifEmpty { null }, name, email) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
