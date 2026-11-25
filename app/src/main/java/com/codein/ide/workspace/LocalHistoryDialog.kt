package com.codein.ide.workspace

import com.codein.ide.core.ui.i18n.tr
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.Hairline
import com.codein.ide.core.ui.components.PanelHeader
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.vcs.ui.DiffView

/**
 * Lists the versions Codein kept of a file (newest first). Picking one shows what restoring it
 * would change in the current text; Restore puts it back.
 */
@Composable
fun LocalHistoryDialog(history: FileHistoryUi, onIntent: (WorkspaceIntent) -> Unit) {
    Dialog(onDismissRequest = { onIntent(WorkspaceIntent.CloseHistory) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                PanelHeader("Local history · ${history.relativePath.substringAfterLast('/')}", icon = CodeinIcons.History) {
                    ToolbarIconButton(CodeinIcons.Close, tr("Close"), size = 32.dp) { onIntent(WorkspaceIntent.CloseHistory) }
                }
                if (history.versions.isEmpty()) {
                    EmptyState(
                        icon = CodeinIcons.History,
                        title = tr("No earlier versions yet"),
                        message = "Codein keeps the previous text each time it saves or replaces this file (up to ${LocalHistory.MAX_VERSIONS} versions).",
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                    return@Column
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(history.versions, key = { it.time }) { version ->
                        val selected = version == history.selected
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                                .clickable(onClickLabel = "Compare with the current text") { onIntent(WorkspaceIntent.SelectHistoryVersion(version)) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CodeinIcons.Clock, null, Modifier.size(16.dp), tint = CodeinTheme.colors.textMuted)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                DateUtils.getRelativeTimeSpanString(version.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(version.time)),
                                style = MaterialTheme.typography.labelSmall,
                                color = CodeinTheme.colors.textMuted,
                            )
                        }
                    }
                }
                Hairline()
                Text(tr("Changes restoring this version would make"),
                    style = MaterialTheme.typography.labelMedium,
                    color = CodeinTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    DiffView(history.diff)
                }
                Hairline()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { onIntent(WorkspaceIntent.CloseHistory) }) { Text(tr("Close")) }
                    Button(onClick = { history.selected?.let { onIntent(WorkspaceIntent.RestoreHistoryVersion(it)) } }, enabled = history.selected != null) {
                        Text(tr("Restore this version"))
                    }
                }
            }
        }
    }
}
