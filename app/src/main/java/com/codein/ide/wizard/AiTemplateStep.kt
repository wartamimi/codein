package com.codein.ide.wizard

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.preview.LayoutPreview
import com.codein.ide.preview.isLayoutFile

private val Examples = listOf(
    "A tip calculator with bill amount, tip percentage slider and split between people.",
    "A to-do list where I can add, check off and delete tasks.",
    "A counter app with plus, minus and reset buttons and a big number in the middle.",
    "A unit converter for length, weight and temperature.",
)

/** Template-picker card for "Template from AI". */
@Composable
internal fun AiTemplateCard(selected: Boolean, onClick: () -> Unit) {
    CodeinCard(Modifier.fillMaxWidth(), selected = selected, onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CodeinIcons.Sparkle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(tr("Template from AI"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(tr("Describe the app you want. Your AI provider writes the screens and code on top of a Codein base project, and you review every file before it is created."),
                    style = MaterialTheme.typography.bodySmall,
                    color = CodeinTheme.colors.textMuted,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            PillChip(tr("Compose · Views"), selected = selected)
        }
    }
}

/** Last wizard step in AI mode: description, generation progress and a review of the generated files. */
@Composable
internal fun AiTemplateStep(
    description: String,
    onDescriptionChange: (String) -> Unit,
    state: AiTemplateState,
    summary: String,
    onRegenerateFile: (path: String, instruction: String) -> Unit = { _, _ -> },
) {
    Text(tr("Describe your app"), style = MaterialTheme.typography.titleMedium)
    Text(summary, style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text(tr("What should the app do?")) },
        placeholder = { Text(tr("Screens, buttons, what happens when you tap them…")) },
        minLines = 4,
        enabled = state !is AiTemplateState.Generating && state !is AiTemplateState.Creating,
        modifier = Modifier.fillMaxWidth(),
    )
    if (description.isBlank()) {
        Text(tr("Examples"), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Examples.forEach { example -> PillChip(example.substringBefore(" with").substringBefore(" where").substringBefore(" for "), selected = false, onClick = { onDescriptionChange(example) }) }
        }
    }
    when (state) {
        AiTemplateState.Idle -> Text(
            "Uses the AI provider selected in Settings → AI providers. Only app sources, resources and the manifest are generated; the Gradle setup comes from Codein's own template so the project builds on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = CodeinTheme.colors.textMuted,
        )
        is AiTemplateState.Generating -> CodeinCard(Modifier.fillMaxWidth()) {
            Text("Generating with ${state.providerLabel}…" + if (state.part > 1) " (part ${state.part})" else "", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(Spacing.sm))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.size(Spacing.xs))
            Text(
                state.currentFile?.let { "Writing $it" } ?: "Planning the app",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${state.finishedFiles} block(s) done · ${state.receivedChars} characters",
                style = MaterialTheme.typography.bodySmall,
                color = CodeinTheme.colors.textMuted,
            )
        }
        is AiTemplateState.Failed -> CodeinCard(Modifier.fillMaxWidth()) {
            Text(tr("Could not generate the project"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
        AiTemplateState.Creating -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is AiTemplateState.Ready -> GeneratedFiles(state, onRegenerateFile)
    }
}

@Composable
private fun GeneratedFiles(state: AiTemplateState.Ready, onRegenerateFile: (String, String) -> Unit) {
    val project = state.project
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showPreview by rememberSaveable { mutableStateOf(true) }
    var instruction by rememberSaveable { mutableStateOf("") }
    state.regenerateError?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    CodeinCard(Modifier.fillMaxWidth()) {
        Text(tr("Review before creating"), style = MaterialTheme.typography.labelLarge)
        if (project.summary.isNotBlank()) Text(project.summary, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${project.files.size} file(s)" + if (project.dependencies.isNotEmpty()) " · ${project.dependencies.size} extra dependenc${if (project.dependencies.size == 1) "y" else "ies"}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = CodeinTheme.colors.textMuted,
        )
    }
    project.files.forEach { (path, content) ->
        val open = expanded == path
        CodeinCard(Modifier.fillMaxWidth(), onClick = { expanded = if (open) null else path }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (open) CodeinIcons.ChevronDown else CodeinIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(path.removePrefix("app/src/main/"), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${content.lines().size} lines", style = MaterialTheme.typography.labelSmall, color = CodeinTheme.colors.textMuted)
            }
            if (state.regenerating == path) {
                Spacer(Modifier.size(Spacing.xs))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (open) {
                Spacer(Modifier.size(Spacing.sm))
                if (isLayoutFile(path)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillChip(tr("Preview"), selected = showPreview, onClick = { showPreview = true })
                        PillChip(tr("XML"), selected = !showPreview, onClick = { showPreview = false })
                    }
                    Spacer(Modifier.size(Spacing.xs))
                }
                if (isLayoutFile(path) && showPreview) {
                    LayoutPreview(content, projectDir = null, modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(8.dp)))
                } else Box(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(8.dp)).background(CodeinTheme.colors.base)
                        .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(Spacing.sm),
                ) {
                    Text(content, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.size(Spacing.sm))
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text(tr("Change for this file (optional)")) },
                    singleLine = true,
                    enabled = state.regenerating == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(Spacing.xs))
                PillChip(
                    if (state.regenerating == path) "Regenerating…" else "Regenerate this file",
                    icon = CodeinIcons.Sparkle,
                    selected = true,
                    onClick = { if (state.regenerating == null) { onRegenerateFile(path, instruction); instruction = "" } },
                )
            }
        }
    }
    if (project.dependencies.isNotEmpty()) {
        CodeinCard(Modifier.fillMaxWidth()) {
            Text(tr("Extra dependencies"), style = MaterialTheme.typography.labelLarge)
            project.dependencies.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
    }
    if (project.rejected.isNotEmpty()) {
        CodeinCard(Modifier.fillMaxWidth()) {
            Text(tr("Skipped"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            project.rejected.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
