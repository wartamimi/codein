package com.codein.ide.ai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.codein.ide.ai.diff.FilePatch
import com.codein.ide.ai.diff.HunkLine
import com.codein.ide.ai.markdown.MarkdownParser
import com.codein.ide.ai.markdown.MdBlock
import com.codein.ide.ai.markdown.MdInline
import com.codein.ide.ai.model.ChatRole
import com.codein.ide.ai.model.ContextChip
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.Hairline
import com.codein.ide.core.ui.components.PanelHeader
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.icons.FileTypeBadge
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.codeTextStyle

/**
 * The Codein AI dock: context chips, streaming markdown conversation with diff and code cards,
 * and a prompt composer. Edits are delegated to the host through [onApplyDiff] / [onInsertCode].
 */
@Composable
fun AiAssistantPanel(
    state: AiAssistantState,
    onIntent: (AiIntent) -> Unit,
    onApplyDiff: (FilePatch) -> Unit,
    onInsertCode: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PanelHeader("Codein AI", icon = CodeinIcons.Sparkle) {
            if (state.modelLabel.isNotEmpty()) {
                Text(state.modelLabel, style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted, maxLines = 1, modifier = Modifier.widthIn(max = 140.dp))
            }
            ToolbarIconButton(CodeinIcons.Trash, "Clear conversation", size = 32.dp, enabled = state.messages.isNotEmpty()) { onIntent(AiIntent.Clear) }
            ToolbarIconButton(CodeinIcons.Settings, "AI provider settings", size = 32.dp, onClick = onOpenSettings)
        }
        Hairline()
        val listState = rememberLazyListState()
        val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
        LaunchedEffect(state.messages.size, lastLength) {
            if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE / 2)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                EmptyState(
                    icon = CodeinIcons.Sparkle,
                    title = if (state.configured) "Ask about your code" else "Connect an AI provider",
                    message = if (state.configured) {
                        "Attach the current file, selection, build errors or Gradle files with the chips below. Suggested edits arrive as diffs you can apply."
                    } else {
                        "Add an Anthropic, OpenAI-compatible or Gemini API key. Keys are encrypted with the Android Keystore."
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    if (!state.configured) TextButton(onClick = onOpenSettings) { Text("Open AI settings") }
                    else if (state.hasBuildError) TextButton(onClick = { onIntent(AiIntent.ExplainAndFix) }) { Text("Explain & fix build error") }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.messages, key = { it.id }) { message ->
                        if (message.role == ChatRole.User) UserBubble(message) else AssistantMessage(message, onApplyDiff, onInsertCode) { onIntent(AiIntent.Retry) }
                    }
                }
            }
        }
        Hairline()
        ChipRow(state, onIntent)
        Composer(state, onIntent)
    }
}

@Composable
private fun ChipRow(state: AiAssistantState, onIntent: (AiIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ContextChip.entries.forEach { chip ->
            val available = when (chip) {
                ContextChip.CurrentFile -> state.currentFileName != null
                ContextChip.Selection -> state.hasSelection
                ContextChip.BuildError -> state.hasBuildError
                ContextChip.Gradle -> state.hasGradle
                ContextChip.FileTree -> true
            }
            val label = if (chip == ContextChip.CurrentFile && state.currentFileName != null) "@${state.currentFileName}" else chip.label
            PillChip(
                text = label,
                selected = chip in state.chips && available,
                accent = if (chip == ContextChip.BuildError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(if (available) 1f else 0.45f),
                onClick = { onIntent(AiIntent.ToggleChip(chip)) },
            )
        }
    }
}

@Composable
private fun Composer(state: AiAssistantState, onIntent: (AiIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp, max = 160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (state.input.isEmpty()) {
                Text(
                    if (state.hasBuildError) "Ask, or tap AI Fix to explain the build error…" else "Ask Codein AI…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CodeinTheme.colors.textMuted,
                )
            }
            BasicTextField(
                value = state.input,
                onValueChange = { onIntent(AiIntent.Input(it)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        val gradient = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(gradient)
                .clickable(enabled = state.streaming || state.input.isNotBlank()) {
                    onIntent(if (state.streaming) AiIntent.Stop else AiIntent.Send)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.streaming) CodeinIcons.Stop else CodeinIcons.Send,
                contentDescription = if (state.streaming) "Stop generating" else "Send",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun UserBubble(message: AiMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyMedium) }
        }
        if (message.attachedChips.isNotEmpty()) {
            Text(
                message.attachedChips.joinToString("  ") { it.label },
                style = MaterialTheme.typography.labelSmall,
                color = CodeinTheme.colors.textMuted,
                modifier = Modifier.padding(top = 3.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun AssistantMessage(message: AiMessage, onApplyDiff: (FilePatch) -> Unit, onInsertCode: (String) -> Unit, onRetry: () -> Unit) {
    val blocks = remember(message.text) { MarkdownParser.parse(message.text) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(CodeinIcons.Sparkle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Codein AI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (message.streaming) {
                Spacer(Modifier.width(8.dp))
                TypingDots()
            }
            Spacer(Modifier.weight(1f))
            message.usage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted) }
        }
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    block.text,
                    style = when (block.level) { 1 -> MaterialTheme.typography.titleMedium; 2 -> MaterialTheme.typography.titleSmall; else -> MaterialTheme.typography.labelLarge },
                    fontWeight = FontWeight.SemiBold,
                )
                is MdBlock.Paragraph -> InlineText(block.text)
                is MdBlock.ListItem -> Row(Modifier.padding(start = (block.indent * 14).dp)) {
                    Text(block.marker, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                    InlineText(block.text)
                }
                is MdBlock.Quote -> Row {
                    Box(Modifier.width(3.dp).heightIn(min = 18.dp).background(MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.width(8.dp))
                    InlineText(block.text)
                }
                is MdBlock.Code -> CodeCard(block, onInsertCode)
                is MdBlock.Diff -> DiffCard(block, onApplyDiff)
                MdBlock.Rule -> Hairline()
            }
        }
        message.error?.let { error ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CodeinIcons.AlertCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "phase")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val active = phase.toInt() == i
            Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = if (active) 1f else 0.3f)))
        }
    }
}

@Composable
private fun InlineText(text: String) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val link = MaterialTheme.colorScheme.primary
    val codeFamily = CodeinTheme.editorPreferences.codingFont.family
    val uri = LocalUriHandler.current
    val spans = remember(text) { MarkdownParser.inline(text) }
    val annotated = buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is MdInline.Text -> append(span.text)
                is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
                is MdInline.InlineCode -> withStyle(SpanStyle(fontFamily = codeFamily, background = codeBg)) { append(" ${span.text} ") }
                is MdInline.Link -> {
                    pushStringAnnotation("url", span.url)
                    withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(span.text) }
                    pop()
                }
            }
        }
    }
    val hasLinks = spans.any { it is MdInline.Link }
    SelectionContainer {
        Text(
            annotated,
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (hasLinks) Modifier.clickable {
                annotated.getStringAnnotations("url", 0, annotated.length).firstOrNull()?.let { runCatching { uri.openUri(it.item) } }
            } else Modifier,
        )
    }
}

@Composable
private fun CodeCard(block: MdBlock.Code, onInsertCode: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scheme = CodeinTheme.editorScheme
    val prefs = CodeinTheme.editorPreferences
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Row(Modifier.fillMaxWidth().background(scheme.gutterBackground).padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CodeinIcons.Code, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(block.language.ifEmpty { "code" }, style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted, modifier = Modifier.weight(1f))
            ToolbarIconButton(CodeinIcons.Copy, "Copy code", size = 30.dp) { clipboard.setText(AnnotatedString(block.code)) }
            ToolbarIconButton(CodeinIcons.ArrowDown, "Insert at cursor", size = 30.dp, enabled = block.complete) { onInsertCode(block.code) }
        }
        SelectionContainer {
            Text(
                block.code,
                style = codeTextStyle(prefs.codingFont, 12.5f, prefs.ligatures),
                color = scheme.foreground,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp),
            )
        }
    }
}

@Composable
private fun DiffCard(block: MdBlock.Diff, onApplyDiff: (FilePatch) -> Unit) {
    val scheme = CodeinTheme.editorScheme
    val prefs = CodeinTheme.editorPreferences
    val addColor = CodeinTheme.colors.success
    val removeColor = MaterialTheme.colorScheme.error
    val style = codeTextStyle(prefs.codingFont, 12f, false)
    if (block.patches.isEmpty()) {
        // Still streaming (or unparseable): show the raw text so nothing is hidden.
        Text(block.raw, style = style, color = scheme.foreground, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(10.dp))
        return
    }
    block.patches.forEach { patch ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        ) {
            Row(Modifier.fillMaxWidth().background(scheme.gutterBackground).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val name = patch.targetPath?.substringAfterLast('/') ?: "current file"
                FileTypeBadge(name)
                Spacer(Modifier.width(6.dp))
                Text(patch.targetPath ?: "current file", style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.weight(1f))
                CountBadge("+${patch.additions}", addColor)
                Spacer(Modifier.width(4.dp))
                CountBadge("−${patch.deletions}", removeColor)
                Spacer(Modifier.width(6.dp))
                PillChip("Apply diff", icon = CodeinIcons.Check, selected = true, onClick = if (block.complete) ({ onApplyDiff(patch) }) else null)
            }
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                patch.hunks.forEach { hunk ->
                    Text(
                        "@@ -${hunk.oldStart.coerceAtLeast(0)},${hunk.oldCount} +${hunk.newStart.coerceAtLeast(0)},${hunk.newCount} @@",
                        style = style,
                        color = scheme.comment,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    hunk.lines.forEach { line ->
                        val (prefix, color, bg) = when (line.kind) {
                            HunkLine.Kind.Add -> Triple("+", addColor, addColor.copy(alpha = 0.12f))
                            HunkLine.Kind.Remove -> Triple("-", removeColor, removeColor.copy(alpha = 0.12f))
                            HunkLine.Kind.Context -> Triple(" ", scheme.foreground, Color.Transparent)
                        }
                        Text(
                            prefix + line.text,
                            style = style,
                            color = color,
                            softWrap = false,
                            modifier = Modifier.background(bg).padding(horizontal = 10.dp).widthIn(min = 600.dp),
                        )
                    }
                }
            }
        }
    }
}
