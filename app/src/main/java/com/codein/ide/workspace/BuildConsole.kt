package com.codein.ide.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.codein.ide.compiler.model.BuildStep
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.LogLevel
import com.codein.ide.compiler.model.Severity
import com.codein.ide.compiler.model.StepStatus
import com.codein.ide.compiler.sdk.ToolchainStatus
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.SpinningIcon
import com.codein.ide.core.ui.components.formatDuration
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.LogLevelColors
import com.codein.ide.core.ui.theme.codeTextStyle
import kotlinx.coroutines.delay
import java.io.File

/** Build Pipeline tab: five step cards with live timers, clickable diagnostics and the raw log. */
@Composable
fun BuildConsole(
    build: BuildUiState,
    toolchain: ToolchainStatus?,
    onIntent: (WorkspaceIntent) -> Unit,
    onAiFix: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLog by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(build.running) {
        while (build.running) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    Column(modifier.fillMaxSize()) {
        if (build.running) LinearProgressIndicator(progress = { build.progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        toolchain?.takeIf { it.missing.isNotEmpty() }?.let { tc ->
            Row(
                Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(10.dp)).background(CodeinTheme.colors.warning.copy(alpha = 0.12f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CodeinIcons.AlertTriangle, null, tint = CodeinTheme.colors.warning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Toolchain incomplete: ${tc.missing.joinToString()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) { Text("Set up") }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BuildStep.entries.forEach { step ->
                StepCard(step, build.steps[step] ?: StepUi(), now)
            }
        }
        ResultBanner(build, onIntent, onAiFix)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val errors = build.diagnostics.count { it.severity == Severity.Error }
            val warnings = build.diagnostics.count { it.severity == Severity.Warning }
            PillChip("Problems ($errors/$warnings)", icon = CodeinIcons.AlertCircle, selected = !showLog, onClick = { showLog = false })
            PillChip("Build log (${build.logs.size})", icon = CodeinIcons.Terminal, selected = showLog, onClick = { showLog = true })
        }
        Spacer(Modifier.height(4.dp))
        if (showLog) BuildLog(build) else Problems(build.diagnostics, onIntent)
    }
}

@Composable
private fun StepCard(step: BuildStep, ui: StepUi, now: Long) {
    val (color, icon) = when (ui.status) {
        StepStatus.Pending -> CodeinTheme.colors.textMuted to CodeinIcons.Clock
        StepStatus.Running -> MaterialTheme.colorScheme.primary to CodeinIcons.Refresh
        StepStatus.Succeeded -> CodeinTheme.colors.success to CodeinIcons.CheckCircle
        StepStatus.Failed -> MaterialTheme.colorScheme.error to CodeinIcons.AlertCircle
        StepStatus.Skipped -> CodeinTheme.colors.textMuted to CodeinIcons.ChevronRight
        StepStatus.Cancelled -> CodeinTheme.colors.warning to CodeinIcons.Stop
    }
    val elapsed = ui.durationMillis ?: ui.startedAt?.let { now - it }
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CodeinTheme.colors.card)
            .border(1.dp, if (ui.status == StepStatus.Running) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text("${step.index}", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            if (ui.status == StepStatus.Running) SpinningIcon(icon, color, Modifier.size(16.dp))
            else Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(step.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                elapsed != null -> formatDuration(elapsed)
                else -> ui.status.name
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun ResultBanner(build: BuildUiState, onIntent: (WorkspaceIntent) -> Unit, onAiFix: () -> Unit) {
    when (val result = build.result) {
        is BuildResult.Success -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(CodeinTheme.colors.success.copy(alpha = 0.12f)).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CodeinIcons.CheckCircle, null, tint = CodeinTheme.colors.success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("${result.output.name} · ${formatDuration(result.durationMillis)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!result.variant.producesBundle) TextButton(onClick = { onIntent(WorkspaceIntent.InstallOutput) }) { Text("Install") }
            TextButton(onClick = { onIntent(WorkspaceIntent.ShareOutput) }) { Text("Share") }
        }
        is BuildResult.Failure -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CodeinIcons.AlertCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Failed at ${result.failedStep.title} · ${formatDuration(result.durationMillis)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAiFix) {
                Icon(CodeinIcons.Sparkle, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Explain & fix")
            }
        }
        is BuildResult.Cancelled -> Text("Build cancelled", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.warning, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        null -> Unit
    }
}

@Composable
private fun Problems(diagnostics: List<CompilerDiagnostic>, onIntent: (WorkspaceIntent) -> Unit) {
    if (diagnostics.isEmpty()) {
        Text("No problems", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted, modifier = Modifier.padding(12.dp))
        return
    }
    val style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false)
    LazyColumn(Modifier.fillMaxSize()) {
        items(diagnostics.sortedBy { it.severity.ordinal }) { d ->
            val color = when (d.severity) {
                Severity.Error -> MaterialTheme.colorScheme.error
                Severity.Warning -> CodeinTheme.colors.warning
                Severity.Info -> CodeinTheme.colors.info
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = d.file != null) { onIntent(WorkspaceIntent.JumpToDiagnostic(d)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(if (d.severity == Severity.Error) CodeinIcons.AlertCircle else CodeinIcons.AlertTriangle, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.message, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountBadge(d.source.name, color)
                        if (d.file != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                File(d.file!!).name + (d.line?.let { ":$it" } ?: "") + (d.column?.let { ":$it" } ?: ""),
                                style = style,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    d.snippet?.takeIf { it.isNotBlank() }?.let { Text(it.trim(), style = style, color = CodeinTheme.colors.textMuted, maxLines = 2) }
                }
            }
        }
    }
}

@Composable
private fun BuildLog(build: BuildUiState) {
    val listState = rememberLazyListState()
    LaunchedEffect(build.logs.size) { if (build.logs.isNotEmpty()) listState.scrollToItem(build.logs.lastIndex) }
    val style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 11.5f, false)
    LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).widthIn(min = 600.dp), state = listState) {
        items(build.logs, key = { it.id }) { line ->
            val color = when (line.level) {
                LogLevel.Verbose -> LogLevelColors.Verbose
                LogLevel.Info -> MaterialTheme.colorScheme.onSurface
                LogLevel.Warning -> LogLevelColors.Warn
                LogLevel.Error -> LogLevelColors.Error
            }
            Text(
                (line.step?.let { "[${it.index}] " } ?: "") + line.message,
                style = style,
                color = color,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp).background(if (line.level == LogLevel.Error) LogLevelColors.Error.copy(alpha = 0.08f) else Color.Transparent),
            )
        }
    }
}
