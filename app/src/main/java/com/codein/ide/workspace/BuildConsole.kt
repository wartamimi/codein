package com.codein.ide.workspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildStep
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.LogLevel
import com.codein.ide.compiler.model.Severity
import com.codein.ide.compiler.model.StepStatus
import com.codein.ide.compiler.sdk.ToolchainStatus
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.SegmentedTabs
import com.codein.ide.core.ui.components.SpinningIcon
import com.codein.ide.core.ui.components.formatDuration
import com.codein.ide.core.ui.components.shimmer
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.LogLevelColors
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.core.ui.theme.codeTextStyle
import com.codein.ide.core.ui.theme.metricsTextStyle
import kotlinx.coroutines.delay
import java.io.File

/** Short pipeline stage names used by the stepper chips and the Build & Run pill. */
fun stageLabel(step: BuildStep, variant: BuildVariant? = null): String = when (step) {
    BuildStep.MavenResolve -> "Maven"
    BuildStep.Aapt2Resources -> "AAPT2"
    BuildStep.Compile -> "ECJ"
    BuildStep.Dexing -> "D8"
    BuildStep.SignAndPackage -> if (variant?.producesBundle == true) "Bundletool" else "Package"
}

private enum class BuildView { Problems, Log }

/**
 * Build tab: a horizontal pipeline stepper (Maven → AAPT2 → ECJ → D8 → Package) with live tabular
 * timers and a shimmer on the active stage, a result banner, then tappable diagnostic cards that
 * jump the editor to the error and offer "Fix with AI", or the raw build log.
 */
@Composable
fun BuildConsole(
    build: BuildUiState,
    toolchain: ToolchainStatus?,
    onIntent: (WorkspaceIntent) -> Unit,
    onAiFix: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by rememberSaveable { mutableStateOf(BuildView.Problems) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(build.running) {
        while (build.running) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    Column(modifier.fillMaxSize()) {
        if (build.running) {
            LinearProgressIndicator(progress = { build.progress }, modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = Color.Transparent)
        }
        PipelineStepper(build, now)
        toolchain?.takeIf { it.missing.isNotEmpty() }?.let { tc ->
            Banner(
                icon = CodeinIcons.AlertTriangle,
                color = CodeinTheme.colors.warning,
                text = "Toolchain incomplete: ${tc.missing.joinToString()}",
                action = "Set up",
                onAction = onOpenSettings,
            )
        }
        ResultBanner(build, onIntent, onAiFix)
        val errors = build.diagnostics.count { it.severity == Severity.Error }
        val warnings = build.diagnostics.count { it.severity == Severity.Warning }
        SegmentedTabs(
            items = BuildView.entries.toList(),
            selected = view,
            onSelect = { view = it },
            icon = { if (it == BuildView.Problems) CodeinIcons.AlertCircle else CodeinIcons.Terminal },
            label = { if (it == BuildView.Problems) "Problems · $errors/$warnings" else "Build log · ${build.logs.size}" },
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
        AnimatedContent(
            targetState = view,
            transitionSpec = { fadeIn(Motion.switch()) togetherWith fadeOut(Motion.switch()) },
            label = "build-view",
            modifier = Modifier.weight(1f),
        ) { v ->
            if (v == BuildView.Log) BuildLog(build) else Problems(build.diagnostics, onIntent, onAiFix)
        }
    }
}

@Composable
private fun PipelineStepper(build: BuildUiState, now: Long) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuildStep.entries.forEachIndexed { i, step ->
            if (i > 0) {
                Icon(CodeinIcons.ChevronRight, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.padding(horizontal = Spacing.xs).size(14.dp))
            }
            StageChip(stageLabel(step, build.variant), build.steps[step] ?: StepUi(), now)
        }
    }
}

@Composable
private fun StageChip(label: String, ui: StepUi, now: Long) {
    val running = ui.status == StepStatus.Running
    val (color, icon) = stageVisual(ui.status)
    val border by animateColorAsState(if (running) color else if (ui.status == StepStatus.Failed) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, Motion.switch(), label = "stage-border")
    val bg by animateColorAsState(
        when (ui.status) {
            StepStatus.Running -> color.copy(alpha = 0.12f)
            StepStatus.Succeeded -> color.copy(alpha = 0.08f)
            StepStatus.Failed -> color.copy(alpha = 0.12f)
            else -> CodeinTheme.colors.tier2
        },
        Motion.switch(),
        label = "stage-bg",
    )
    val elapsed = ui.durationMillis ?: ui.startedAt?.let { now - it }
    Row(
        Modifier
            .height(Sizes.Pill)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .shimmer(running, color)
            .animateContentSize(Motion.spatial())
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (running) SpinningIcon(icon, color, Modifier.size(14.dp)) else Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (ui.status == StepStatus.Pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1)
        if (elapsed != null) {
            Text(formatDuration(elapsed), style = metricsTextStyle(CodeinTheme.editorPreferences.codingFont, 10f), color = color)
        }
    }
}

@Composable
private fun stageVisual(status: StepStatus): Pair<Color, ImageVector> = when (status) {
    StepStatus.Pending -> CodeinTheme.colors.textMuted to CodeinIcons.Clock
    StepStatus.Running -> MaterialTheme.colorScheme.primary to CodeinIcons.Refresh
    StepStatus.Succeeded -> CodeinTheme.colors.success to CodeinIcons.CheckCircle
    StepStatus.Failed -> MaterialTheme.colorScheme.error to CodeinIcons.AlertCircle
    StepStatus.Skipped -> CodeinTheme.colors.textMuted to CodeinIcons.Minus
    StepStatus.Cancelled -> CodeinTheme.colors.warning to CodeinIcons.Stop
}

@Composable
private fun Banner(icon: ImageVector, color: Color, text: String, action: String? = null, onAction: () -> Unit = {}, secondary: String? = null, onSecondary: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .heightIn(min = Sizes.MinTouch)
            .padding(start = Spacing.md, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(Sizes.IconSm))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = Spacing.sm), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (secondary != null) BannerButton(secondary, color, onSecondary)
        if (action != null) BannerButton(action, color, onAction)
    }
}

@Composable
private fun BannerButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = Sizes.MinTouch).clickable(onClick = onClick).padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultBanner(build: BuildUiState, onIntent: (WorkspaceIntent) -> Unit, onAiFix: () -> Unit) {
    when (val result = build.result) {
        is BuildResult.Success -> Banner(
            icon = CodeinIcons.CheckCircle,
            color = CodeinTheme.colors.success,
            text = "${result.output.name} · ${formatDuration(result.durationMillis)}",
            action = if (!result.variant.producesBundle) "Install" else null,
            onAction = { onIntent(WorkspaceIntent.InstallOutput) },
            secondary = "Share",
            onSecondary = { onIntent(WorkspaceIntent.ShareOutput) },
        )
        is BuildResult.Failure -> Banner(
            icon = CodeinIcons.AlertCircle,
            color = MaterialTheme.colorScheme.error,
            text = "Failed at ${stageLabel(result.failedStep, build.variant)} · ${formatDuration(result.durationMillis)}",
            action = "Fix with AI",
            onAction = onAiFix,
        )
        is BuildResult.Cancelled -> Banner(icon = CodeinIcons.Stop, color = CodeinTheme.colors.warning, text = "Build cancelled")
        null -> Unit
    }
}

@Composable
private fun Problems(diagnostics: List<CompilerDiagnostic>, onIntent: (WorkspaceIntent) -> Unit, onAiFix: () -> Unit) {
    if (diagnostics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(CodeinIcons.CheckCircle, "No problems", "Errors and warnings from AAPT2, ECJ, Kotlin and D8 appear here.")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(diagnostics.sortedBy { it.severity.ordinal }) { d ->
            DiagnosticCard(
                d = d,
                onOpen = { onIntent(WorkspaceIntent.JumpToDiagnostic(d)) },
                onFix = {
                    if (d.file != null) onIntent(WorkspaceIntent.JumpToDiagnostic(d))
                    onAiFix()
                },
            )
        }
    }
}

/** A structured diagnostic: file badge, `Line:Col` pill, concise message and a "Fix with AI" action. */
@Composable
private fun DiagnosticCard(d: CompilerDiagnostic, onOpen: () -> Unit, onFix: () -> Unit) {
    val color = when (d.severity) {
        Severity.Error -> MaterialTheme.colorScheme.error
        Severity.Warning -> CodeinTheme.colors.warning
        Severity.Info -> CodeinTheme.colors.info
    }
    val font = CodeinTheme.editorPreferences.codingFont
    val shape = RoundedCornerShape(12.dp)
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CodeinTheme.colors.tier2)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = d.file != null, onClickLabel = "Go to location", onClick = onOpen)
            .animateContentSize(Motion.spatial()),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = Spacing.lg).clip(CircleShape).background(color))
            Spacer(Modifier.width(Spacing.sm))
            if (d.file != null) {
                val name = File(d.file!!).name
                FileTypeBadge(name, size = Sizes.IconSm)
                Spacer(Modifier.width(Spacing.xs))
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (d.line != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(CodeinTheme.colors.tier3).padding(horizontal = Spacing.xs + 2.dp, vertical = 2.dp),
                    ) {
                        Text("${d.line}:${d.column ?: 1}", style = metricsTextStyle(font, 11f), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                Text(d.source.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            CountBadge(d.source.name, color)
            if (d.severity == Severity.Error) {
                PillChip("Fix with AI", icon = CodeinIcons.Sparkle, selected = true, modifier = Modifier.padding(start = Spacing.xs), onClick = onFix)
            }
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            d.message,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(start = Spacing.md + Spacing.sm + 3.dp, end = Spacing.md, top = Spacing.xs, bottom = if (d.snippet.isNullOrBlank()) Spacing.md else Spacing.xs),
        )
        d.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
            Text(
                snippet.trim(),
                style = codeTextStyle(font, 11.5f, false),
                color = CodeinTheme.colors.textMuted,
                maxLines = 2,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CodeinTheme.colors.base)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun BuildLog(build: BuildUiState) {
    val listState = rememberLazyListState()
    LaunchedEffect(build.logs.size) { if (build.logs.isNotEmpty()) listState.scrollToItem(build.logs.lastIndex) }
    val font = CodeinTheme.editorPreferences.codingFont
    val style = codeTextStyle(font, 11.5f, false)
    if (build.logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(CodeinIcons.Terminal, "No build output yet", "Tap Build & Run to start the on-device pipeline.")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().background(CodeinTheme.colors.base).horizontalScroll(rememberScrollState()).widthIn(min = 600.dp),
        state = listState,
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        items(build.logs, key = { it.id }) { line ->
            val color = when (line.level) {
                LogLevel.Verbose -> LogLevelColors.Verbose
                LogLevel.Info -> MaterialTheme.colorScheme.onSurface
                LogLevel.Warning -> LogLevelColors.Warn
                LogLevel.Error -> LogLevelColors.Error
            }
            Row(
                Modifier
                    .background(if (line.level == LogLevel.Error) LogLevelColors.Error.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(horizontal = Spacing.md),
            ) {
                Text(
                    line.step?.let { stageLabel(it, build.variant).padEnd(10) } ?: " ".repeat(10),
                    style = metricsTextStyle(font, 11f),
                    color = CodeinTheme.colors.textMuted,
                )
                Text(line.message, style = style, color = color, softWrap = false)
            }
        }
    }
}
