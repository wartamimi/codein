package com.codein.ide.core.ui.components

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing

// ---------------------------------------------------------------------------------------------
// Hairlines and surfaces
// ---------------------------------------------------------------------------------------------

/** 1dp hairline divider (hairline source at 30% alpha): Codein's replacement for shadows. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

/** Alias of [HairlineDivider] kept for existing call sites. */
@Composable
fun Hairline(modifier: Modifier = Modifier) = HairlineDivider(modifier)

@Composable
fun VerticalHairline(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    VerticalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

/** Tier 1 panel surface with a hairline border. */
@Composable
fun CodeinPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = CodeinTheme.colors.tier1,
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

/** Tier 2 card with a hairline border, optional click handling and an accent border when selected. */
@Composable
fun CodeinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.switch(),
        label = "card-border",
    )
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) CodeinTheme.colors.tier3 else CodeinTheme.colors.tier2)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A titled Tier 2 section with a hairline border. Height changes animate with
 * `animateContentSize()` so expanding settings, diagnostics or diffs never jump.
 */
@Composable
fun StudioSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(CodeinTheme.colors.tier2)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .animateContentSize(Motion.spatial())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = accent, modifier = Modifier.size(Sizes.IconSm)) }
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Pills, chips and buttons
// ---------------------------------------------------------------------------------------------

/** Compact pill chip used for branch, variant, filter and context chips. */
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
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.16f) else CodeinTheme.colors.tier2, Motion.switch(), label = "pill-bg")
    val border by animateColorAsState(if (selected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant, Motion.switch(), label = "pill-border")
    val fg = if (selected) accent else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = Sizes.Chip)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(tr(text), style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, contentDescription = null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
}

/**
 * Icon button with a crisp 18-20dp glyph inside a small visual container, but a full 44x44dp
 * hitbox (`defaultMinSize`) so dense toolbars stay easy to tap. [size] is the visual container.
 */
@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: Color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 32.dp,
    iconSize: Dp = if (size >= 36.dp) Sizes.IconLg else Sizes.Icon,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        Motion.switch(),
        label = "icon-bg",
    )
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Sizes.MinTouch, minHeight = Sizes.MinTouch)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = size / 2 + 4.dp),
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = tr(contentDescription),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = tr(contentDescription), tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(iconSize))
        }
    }
}

/** States for the animated Build & Run pill. */
sealed interface BuildButtonState {
    data object Idle : BuildButtonState
    data class Running(val progress: Float?, val label: String) : BuildButtonState
    data object Succeeded : BuildButtonState
    data object Failed : BuildButtonState
}

/**
 * A pill button that morphs between states: the container color cross-fades, the width springs to
 * fit the new label, and the leading glyph swaps with a short vertical slide + fade (180ms).
 */
@Composable
fun <S> AnimatedPillButton(
    state: S,
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    contentKey: (S) -> Any? = { it },
    label: (S) -> String?,
    leading: @Composable (S) -> Unit,
) {
    val bg by animateColorAsState(container, Motion.switch(), label = "pill-container")
    val fg by animateColorAsState(contentColor, Motion.switch(), label = "pill-content")
    Row(
        modifier = modifier
            .height(Sizes.Pill)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .animateContentSize(Motion.spatial())
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (slideInVertically(tween(Motion.SwitchMillis)) { it / 2 } + fadeIn(tween(Motion.SwitchMillis))) togetherWith
                    (slideOutVertically(tween(Motion.SwitchMillis)) { -it / 2 } + fadeOut(tween(Motion.SwitchMillis)))
            },
            contentKey = contentKey,
            label = "pill-leading",
        ) { s ->
            Box(Modifier.size(Sizes.Icon), contentAlignment = Alignment.Center) { leading(s) }
        }
        val text = label(state)
        if (text != null) {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (slideInVertically(tween(Motion.SwitchMillis)) { it / 2 } + fadeIn(tween(Motion.SwitchMillis))) togetherWith
                        (slideOutVertically(tween(Motion.SwitchMillis)) { -it / 2 } + fadeOut(tween(Motion.SwitchMillis)))
                },
                label = "pill-label",
            ) { t ->
                Text(t, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/**
 * The unified Build & Run pill: Play icon → circular progress with a Stop glyph → success/error
 * badge. Fires a long-press haptic when a running build completes or fails.
 */
@Composable
fun BuildRunPill(
    state: BuildButtonState,
    onBuild: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(state::class) {
        val running = state is BuildButtonState.Running
        if (wasRunning && !running) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        wasRunning = running
    }
    val success = CodeinTheme.colors.success
    val container = when (state) {
        is BuildButtonState.Running -> CodeinTheme.colors.tier3
        BuildButtonState.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        BuildButtonState.Succeeded -> success.copy(alpha = 0.18f)
        BuildButtonState.Idle -> MaterialTheme.colorScheme.primary
    }
    val content = when (state) {
        is BuildButtonState.Running -> MaterialTheme.colorScheme.onSurface
        BuildButtonState.Failed -> MaterialTheme.colorScheme.error
        BuildButtonState.Succeeded -> success
        BuildButtonState.Idle -> MaterialTheme.colorScheme.onPrimary
    }
    AnimatedPillButton(
        state = state,
        onClick = { if (state is BuildButtonState.Running) onCancel() else onBuild() },
        container = container,
        contentColor = content,
        modifier = modifier,
        contentKey = { it::class },
        label = { s ->
            if (compact) null else when (s) {
                is BuildButtonState.Running -> s.label
                BuildButtonState.Failed -> "Retry"
                BuildButtonState.Succeeded -> "Run again"
                BuildButtonState.Idle -> "Build & Run"
            }
        },
    ) { s ->
        when (s) {
            is BuildButtonState.Running -> {
                val p = s.progress
                if (p != null) {
                    CircularProgressIndicator(progress = { p }, strokeWidth = 2.dp, modifier = Modifier.size(Sizes.Icon), color = MaterialTheme.colorScheme.primary)
                } else {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(Sizes.Icon), color = MaterialTheme.colorScheme.primary)
                }
                Icon(CodeinIcons.Stop, contentDescription = "Cancel build", tint = content, modifier = Modifier.size(8.dp))
            }
            BuildButtonState.Failed -> Icon(CodeinIcons.AlertCircle, contentDescription = "Build failed", tint = content, modifier = Modifier.size(Sizes.IconSm))
            BuildButtonState.Succeeded -> Icon(CodeinIcons.CheckCircle, contentDescription = "Build succeeded", tint = content, modifier = Modifier.size(Sizes.IconSm))
            BuildButtonState.Idle -> Icon(CodeinIcons.PlayFilled, contentDescription = "Build and run", tint = content, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Segmented icon tabs with a gliding Tier 3 indicator (no-bounce spring). Used for the explorer
 * sidebar (Files / Search / Git / Maven) and two-tab modals.
 */
@Composable
fun <T> SegmentedTabs(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    label: (T) -> String,
    showLabels: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(CodeinTheme.colors.base)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(Spacing.xs / 2),
    ) {
        val count = items.size.coerceAtLeast(1)
        val segment = maxWidth / count
        val index = items.indexOf(selected).coerceAtLeast(0)
        val offset by animateDpAsState(segment * index, Motion.spatial(), label = "segment-indicator")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(CodeinTheme.colors.tier3),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            items.forEach { item ->
                val isSelected = item == selected
                val fg by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    Motion.switch(),
                    label = "segment-fg",
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (!isSelected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(item)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) Icon(icon(item), contentDescription = tr(label(item)), tint = if (isSelected) MaterialTheme.colorScheme.primary else fg, modifier = Modifier.size(Sizes.IconSm))
                    if (showLabels) {
                        if (icon != null) Spacer(Modifier.width(Spacing.xs))
                        Text(tr(label(item)), style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Centered 32x4dp pill drag handle for sheets and docks. */
@Composable
fun DragHandle(modifier: Modifier = Modifier, active: Boolean = false) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        Motion.switch(),
        label = "handle",
    )
    Box(modifier.size(Sizes.DragHandleWidth, Sizes.DragHandleHeight).clip(CircleShape).background(color))
}

/** Compact single-line input (32dp) on Tier 2 with a hairline that turns red on [isError]. */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    onSubmit: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(8.dp)
    val border by animateColorAsState(
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        Motion.switch(),
        label = "field-border",
    )
    Row(
        modifier
            .height(Sizes.Pill)
            .clip(shape)
            .background(CodeinTheme.colors.tier2)
            .border(1.dp, border, shape)
            .padding(start = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = textStyle, color = CodeinTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing()
        Spacer(Modifier.width(Spacing.xs))
    }
}

// ---------------------------------------------------------------------------------------------
// Motion helpers
// ---------------------------------------------------------------------------------------------

/** A soft light band sweeping across the element while [active]: used on running build stages. */
fun Modifier.shimmer(active: Boolean, color: Color): Modifier = composed {
    if (!active) return@composed this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmer-x")
    drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.22f), Color.Transparent),
                start = Offset(w * (progress - 0.35f), 0f),
                end = Offset(w * (progress + 0.35f), size.height),
            ),
        )
    }
}

/** Indeterminate spinner icon, used in progress rows. */
@Composable
fun SpinningIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.rotate(angle))
}

/** Pulsing dot used as a live indicator (running build, streaming AI, live Logcat). */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, active: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse-alpha")
    Box(modifier.size(Spacing.sm).clip(CircleShape).background(color.copy(alpha = if (active) alpha else 1f)))
}

// ---------------------------------------------------------------------------------------------
// Headers, badges and empty states
// ---------------------------------------------------------------------------------------------

/** A titled section header used inside side panels (44dp, so icon buttons keep their hitbox). */
@Composable
fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(Sizes.MinTouch).padding(start = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.IconSm))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            tr(title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            .padding(horizontal = Spacing.xs + 2.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Empty-state block with icon, title and supporting text. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CodeinTheme.colors.tier2)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Text(tr(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(tr(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
