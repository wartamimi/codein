package com.codein.ide.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.Hairline
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.LogLevelColors
import com.codein.ide.core.ui.theme.codeTextStyle

fun LogPriority.color(): Color = when (this) {
    LogPriority.Verbose -> LogLevelColors.Verbose
    LogPriority.Debug -> LogLevelColors.Debug
    LogPriority.Info -> LogLevelColors.Info
    LogPriority.Warn -> LogLevelColors.Warn
    LogPriority.Error, LogPriority.Assert -> LogLevelColors.Error
}

/** Logcat tab: level pills, regex filter, PID lock, pause/clear and a virtualized log list. */
@Composable
fun LogcatPanel(state: LogcatState, onIntent: (LogcatIntent) -> Unit, onStart: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) {
        onStart()
        onDispose { onStop() }
    }
    val visible = state.visible
    val listState = rememberLazyListState()
    LaunchedEffect(visible.size, state.autoScroll) {
        if (state.autoScroll && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogPriority.entries.filter { it != LogPriority.Assert }.forEach { p ->
                PillChip(p.name, selected = state.minPriority == p, accent = p.color(), onClick = { onIntent(LogcatIntent.MinPriority(p)) })
            }
            PidChip(state, onIntent)
            ToolbarIconButton(if (state.paused) CodeinIcons.Play else CodeinIcons.Pause, if (state.paused) "Resume" else "Pause", size = 32.dp, active = state.paused) { onIntent(LogcatIntent.TogglePause) }
            ToolbarIconButton(CodeinIcons.ArrowDown, "Auto-scroll", size = 32.dp, active = state.autoScroll) { onIntent(LogcatIntent.AutoScroll(!state.autoScroll)) }
            ToolbarIconButton(CodeinIcons.Trash, "Clear", size = 32.dp) { onIntent(LogcatIntent.Clear) }
            CountBadge("${visible.size}/${state.entries.size}", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = state.filter,
            onValueChange = { onIntent(LogcatIntent.Filter(it)) },
            singleLine = true,
            isError = state.filterError != null,
            placeholder = { Text("Regex filter on tag or message") },
            supportingText = state.filterError?.let { { Text(it) } },
            textStyle = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        Hairline()
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = CodeinIcons.Terminal,
                    title = if (state.running) "Waiting for log output" else "Logcat stopped",
                    message = "Android only shows this app's own logs unless READ_LOGS is granted with: adb shell pm grant com.codein.ide android.permission.READ_LOGS",
                )
            }
        } else {
            val style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 11.5f, false)
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(visible, key = { it.id }) { e ->
                    val color = e.priority.color()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (e.priority.ordinal >= LogPriority.Error.ordinal) color.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    ) {
                        Text(e.timestamp.substringAfter(' '), style = style, color = CodeinTheme.colors.textMuted)
                        Spacer(Modifier.width(6.dp))
                        Text(e.priority.letter.toString(), style = style, color = color, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text(e.tag, style = style, color = color, maxLines = 1, modifier = Modifier.width(110.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(e.message, style = style, color = if (e.priority.ordinal >= LogPriority.Warn.ordinal) color else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun PidChip(state: LogcatState, onIntent: (LogcatIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillChip(
            state.lockedPid?.let { "PID $it" } ?: "All PIDs",
            icon = if (state.lockedPid != null) CodeinIcons.Lock else CodeinIcons.Unlock,
            selected = state.lockedPid != null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All processes") }, onClick = { open = false; onIntent(LogcatIntent.LockPid(null)) })
            DropdownMenuItem(text = { Text("This app (${android.os.Process.myPid()})") }, onClick = { open = false; onIntent(LogcatIntent.LockPid(android.os.Process.myPid())) })
            state.knownPids.forEach { pid ->
                DropdownMenuItem(text = { Text("PID $pid") }, onClick = { open = false; onIntent(LogcatIntent.LockPid(pid)) })
            }
        }
    }
}
