package com.codein.ide.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme

/** 1dp hairline divider using `outlineVariant`, the Codein replacement for elevation shadows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    VerticalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Elevated panel surface (#161922) with a hairline border. */
@Composable
fun CodeinPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Active card surface (#1E222E) with hairline border and optional click handling. */
@Composable
fun CodeinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Compact pill chip used for branch, variant and context chips. */
@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (selected) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
    val fg = if (selected) accent else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, contentDescription = null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
}

/** Square icon button with a hairline hit area, sized for dense IDE toolbars. */
@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: Color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 36.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.52f))
    }
}

/** States for the animated Build & Run pill. */
sealed interface BuildButtonState {
    data object Idle : BuildButtonState
    data class Running(val progress: Float?, val label: String) : BuildButtonState
    data object Succeeded : BuildButtonState
    data object Failed : BuildButtonState
}

/** Animated Build & Run pill with circular progress and a cancel state. */
@Composable
fun BuildRunPill(
    state: BuildButtonState,
    onBuild: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val running = state is BuildButtonState.Running
    val container = when (state) {
        is BuildButtonState.Running -> MaterialTheme.colorScheme.surfaceContainerHighest
        BuildButtonState.Failed -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val content = when (state) {
        is BuildButtonState.Running -> MaterialTheme.colorScheme.onSurface
        BuildButtonState.Failed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(container)
            .clickable { if (running) onCancel() else onBuild() }
            .padding(horizontal = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            contentKey = { it::class },
            label = "build-pill",
        ) { s ->
            when (s) {
                is BuildButtonState.Running -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp)) {
                    if (s.progress != null) {
                        CircularProgressIndicator(progress = { s.progress }, strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(CodeinIcons.Stop, contentDescription = "Cancel build", tint = content, modifier = Modifier.size(9.dp))
                }
                BuildButtonState.Failed -> Icon(CodeinIcons.AlertCircle, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
                BuildButtonState.Succeeded -> Icon(CodeinIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
                BuildButtonState.Idle -> Icon(CodeinIcons.PlayFilled, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
            }
        }
        if (!compact) {
            Text(
                text = when (state) {
                    is BuildButtonState.Running -> state.label
                    BuildButtonState.Failed -> "Retry"
                    BuildButtonState.Succeeded -> "Run again"
                    BuildButtonState.Idle -> "Build & Run"
                },
                style = MaterialTheme.typography.labelLarge,
                color = content,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Indeterminate spinner icon, used in progress rows. */
@Composable
fun SpinningIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.rotate(angle))
}

/** A titled section header used inside side panels. */
@Composable
fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A tiny count/status badge (e.g., error counts, time badges). */
@Composable
fun CountBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Empty-state block with icon, title and supporting text. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(CodeinTheme.colors.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) action()
    }
}

/** Formats a byte count with binary units and tabular-friendly precision. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}

/** Formats a duration in milliseconds as `850 ms`, `4.2 s` or `1m 05s`. */
fun formatDuration(millis: Long): String = when {
    millis < 1000 -> "$millis ms"
    millis < 60_000 -> String.format(java.util.Locale.US, "%.1f s", millis / 1000.0)
    else -> String.format(java.util.Locale.US, "%dm %02ds", millis / 60_000, (millis / 1000) % 60)
}
