package com.codein.ide.logcat

import com.codein.ide.core.ui.theme.CodeinShape
import com.codein.ide.core.ui.i18n.tr
import com.codein.ide.core.ui.theme.metricsTextStyle
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.core.ui.components.CompactTextField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.TextButton
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
fun LogcatPanel(
    state: LogcatState,
    onIntent: (LogcatIntent) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    grantCommand: String = "adb shell pm grant com.codein.ide android.permission.READ_LOGS",
) {
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
        // Sticky filter bar: regex field, then level pills and stream controls.
        Column(Modifier.fillMaxWidth().background(CodeinTheme.colors.tier1).padding(top = Spacing.sm)) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                CompactTextField(
                    value = state.filter,
                    onValueChange = { onIntent(LogcatIntent.Filter(it)) },
                    placeholder = tr("Regex filter on tag or message"),
                    leadingIcon = CodeinIcons.Filter,
                    isError = state.filterError != null,
                    textStyle = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false),
                    modifier = Modifier.weight(1f),
                )
                ToolbarIconButton(if (state.paused) CodeinIcons.Play else CodeinIcons.Pause, if (state.paused) "Resume" else "Pause", size = 28.dp, active = state.paused) { onIntent(LogcatIntent.TogglePause) }
                ToolbarIconButton(CodeinIcons.ScrollLock, if (state.autoScroll) "Auto-scroll on" else "Auto-scroll off", size = 28.dp, active = state.autoScroll) { onIntent(LogcatIntent.AutoScroll(!state.autoScroll)) }
                ToolbarIconButton(CodeinIcons.Trash, tr("Clear"), size = 28.dp) { onIntent(LogcatIntent.Clear) }
            }
            state.filterError?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogPriority.entries.filter { it != LogPriority.Assert }.forEach { p ->
                    LevelToggle(p, selected = state.minPriority == p) { onIntent(LogcatIntent.MinPriority(p)) }
                }
                Spacer(Modifier.width(Spacing.xs))
                PidChip(state, onIntent)
                CountBadge("${visible.size}/${state.entries.size}", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Hairline()
        }
        if (!state.canReadAllLogs) ReadLogsBanner(grantCommand)
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = CodeinIcons.Terminal,
                    title = if (state.running) "Waiting for log output" else "Logcat stopped",
                    message = if (state.canReadAllLogs) "Logs from every app on the device appear here." else "Only Codein's own logs are visible until READ_LOGS is granted (see above).",
                )
            }
        } else {
            val font = CodeinTheme.editorPreferences.codingFont
            val style = codeTextStyle(font, 11.5f, false)
            val stamp = metricsTextStyle(font, 11f)
            LazyColumn(Modifier.fillMaxSize().background(CodeinTheme.colors.base), state = listState, contentPadding = PaddingValues(vertical = Spacing.xs)) {
                items(visible, key = { it.id }) { e ->
                    val color = e.priority.color()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (e.priority.ordinal >= LogPriority.Error.ordinal) color.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = Spacing.sm, vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(e.timestamp.substringAfter(' '), style = stamp, color = CodeinTheme.colors.textMuted)
                        Spacer(Modifier.width(Spacing.sm))
                        Box(
                            Modifier.size(16.dp).clip(CodeinShape.Surface).background(color.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(e.priority.letter.toString(), style = stamp, color = color, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(e.tag, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(104.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(e.message, style = style, color = if (e.priority.ordinal >= LogPriority.Warn.ordinal) color else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** Compact colored level tag (V / D / I / W / E) that selects the minimum priority. */
@Composable
private fun LevelToggle(priority: LogPriority, selected: Boolean, onClick: () -> Unit) {
    val color = priority.color()
    val shape = CodeinShape.Surface
    Box(
        Modifier
            .size(width = 36.dp, height = 28.dp)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.22f) else CodeinTheme.colors.tier2)
            .border(1.dp, if (selected) color.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Show ${priority.name} and above", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(priority.letter.toString(), style = metricsTextStyle(CodeinTheme.editorPreferences.codingFont, 12f), color = color, fontWeight = FontWeight.Bold)
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
            DropdownMenuItem(text = { Text(tr("All processes")) }, onClick = { open = false; onIntent(LogcatIntent.LockPid(null)) })
            DropdownMenuItem(text = { Text("This app (${android.os.Process.myPid()})") }, onClick = { open = false; onIntent(LogcatIntent.LockPid(android.os.Process.myPid())) })
            state.knownPids.forEach { pid ->
                DropdownMenuItem(text = { Text("PID $pid") }, onClick = { open = false; onIntent(LogcatIntent.LockPid(pid)) })
            }
        }
    }
}

/**
 * Android grants READ_LOGS only to system apps or over adb, so Codein explains the one-time grant
 * (from a computer, or from the phone itself with Wireless debugging + an adb client such as Termux).
 */
@Composable
private fun ReadLogsBanner(command: String) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(CodeinTheme.colors.warning.copy(alpha = 0.12f), CodeinShape.Surface)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Showing only Codein's logs. Grant READ_LOGS once to see your installed app's logs."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "How") }
        }
        if (expanded) {
            Text(
                "1. Enable Developer options > USB debugging (or Wireless debugging).\n" +
                    "2. From a computer, or from Termux on this phone after pairing with Wireless debugging, run:",
                style = MaterialTheme.typography.bodySmall,
                color = CodeinTheme.colors.textMuted,
            )
            Spacer(Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    command,
                    style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 11.5f, false),
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
                ToolbarIconButton(CodeinIcons.Copy, tr("Copy command"), size = 32.dp) { clipboard.setText(AnnotatedString(command)) }
            }
            Text(
                "3. Force-stop and reopen Codein. The grant survives app updates but not reinstalling.",
                style = MaterialTheme.typography.bodySmall,
                color = CodeinTheme.colors.textMuted,
            )
        }
    }
}
