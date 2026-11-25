package com.codein.ide.wizard

import com.codein.ide.core.ui.theme.CodeinShape
import com.codein.ide.core.ui.i18n.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.home.HomeEvent
import com.codein.ide.home.HomeViewModel
import com.codein.ide.project.ProjectLanguage
import com.codein.ide.project.ProjectSpec
import com.codein.ide.project.ProjectTemplate

private val MinSdkChoices = listOf(21, 23, 24, 26, 28, 29, 30, 31, 33, 34)
private val TargetSdkChoices = listOf(33, 34, 35)

/** Three-step project creation wizard: template, details, SDK levels. */
@Composable
fun NewProjectWizard(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    aiViewModel: AiTemplateViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var aiMode by rememberSaveable { mutableStateOf(false) }
    var aiCompose by rememberSaveable { mutableStateOf(true) }
    var aiDescription by rememberSaveable { mutableStateOf("") }
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    var template by rememberSaveable { mutableStateOf(ProjectTemplate.ComposeMaterial3) }
    var name by rememberSaveable { mutableStateOf("My Application") }
    var packageName by rememberSaveable { mutableStateOf("com.example.myapplication") }
    var packageEdited by rememberSaveable { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(ProjectLanguage.Kotlin) }
    var minSdk by rememberSaveable { mutableIntStateOf(26) }
    var targetSdk by rememberSaveable { mutableIntStateOf(35) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is HomeEvent.OpenProject -> onCreated(e.path)
                is HomeEvent.Message -> error = e.text
            }
        }
    }

    val nameError = ProjectSpec.validateName(name)
    val packageError = ProjectSpec.validatePackage(packageName)
    val effectiveLanguage = when {
        aiMode -> if (aiCompose) ProjectLanguage.Kotlin else language
        template.supportsJava -> language
        else -> ProjectLanguage.Kotlin
    }
    val aiSpec = aiViewModel.baseSpec(name.trim(), packageName.trim(), language, aiCompose)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolbarIconButton(CodeinIcons.ArrowLeft, tr("Back")) { if (step == 0) onBack() else step-- }
            Text(tr("New project"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
            StepDots(step)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = step, label = "wizard") { current ->
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (current) {
                        0 -> {
                            Text(tr("Choose a template"), style = MaterialTheme.typography.titleMedium)
                            ProjectTemplate.entries.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    pair.forEach { t ->
                                        TemplateCard(t, selected = !aiMode && t == template, modifier = Modifier.weight(1f)) { template = t; aiMode = false }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            AiTemplateCard(selected = aiMode) { aiMode = true }
                        }
                        1 -> {
                            Text(tr("Project details"), style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    if (!packageEdited) packageName = ProjectSpec.suggestPackage(it)
                                },
                                label = { Text(tr("Name")) },
                                isError = nameError != null,
                                supportingText = { nameError?.let { Text(it) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = packageName,
                                onValueChange = { packageName = it.trim(); packageEdited = true },
                                label = { Text(tr("Package name")) },
                                isError = packageError != null,
                                supportingText = { packageError?.let { Text(it) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (aiMode) {
                                Text(tr("UI toolkit"), style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PillChip(tr("Jetpack Compose"), selected = aiCompose, onClick = { aiCompose = true })
                                    PillChip(tr("Views (XML)"), selected = !aiCompose, onClick = { aiCompose = false })
                                }
                            }
                            Text(tr("Language"), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProjectLanguage.entries.forEach { l ->
                                    val enabled = if (aiMode) !aiCompose || l == ProjectLanguage.Kotlin else template.supportsJava || l == ProjectLanguage.Kotlin
                                    PillChip(l.displayName, selected = effectiveLanguage == l, onClick = if (enabled) ({ language = l }) else null)
                                }
                            }
                            Text(
                                if (effectiveLanguage == ProjectLanguage.Kotlin) {
                                    "The first Kotlin build downloads the Kotlin compiler (about 60 MB, once) and prepares it, which takes a few minutes."
                                } else {
                                    "Java sources compile with the bundled Eclipse compiler (Java 8 language level)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = CodeinTheme.colors.textMuted,
                            )
                        }
                        else -> if (aiMode) {
                            AiTemplateStep(
                                description = aiDescription,
                                onDescriptionChange = { aiDescription = it },
                                state = aiState,
                                summary = "${if (aiCompose) "Compose" else "Views"} · ${effectiveLanguage.displayName} · $name ($packageName)",
                                onRegenerateFile = { path, instruction -> aiViewModel.regenerateFile(aiSpec, aiDescription, path, instruction) },
                            )
                        } else {
                            Text(tr("SDK levels"), style = MaterialTheme.typography.titleMedium)
                            Text("Minimum SDK · API $minSdk", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MinSdkChoices.forEach { api -> PillChip("$api", selected = api == minSdk, onClick = { minSdk = api; if (targetSdk < api) targetSdk = TargetSdkChoices.last() }) }
                            }
                            Text("Target & compile SDK · API $targetSdk", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TargetSdkChoices.forEach { api -> PillChip("$api", selected = api == targetSdk, onClick = if (api >= minSdk) ({ targetSdk = api }) else null) }
                            }
                            CodeinCard(Modifier.fillMaxWidth()) {
                                Text(tr("Summary"), style = MaterialTheme.typography.labelLarge)
                                Text("${template.displayName} · ${effectiveLanguage.displayName}", style = MaterialTheme.typography.bodyMedium)
                                Text("$name ($packageName)", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
                                Text("minSdk $minSdk · targetSdk $targetSdk · compileSdk $targetSdk", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) { Text(tr("Back")) }
                Spacer(Modifier.width(8.dp))
            }
            if (step < 2) {
                Button(onClick = { step++ }, enabled = step == 0 || (nameError == null && packageError == null)) { Text(tr("Next")) }
            } else if (aiMode) {
                when (aiState) {
                    is AiTemplateState.Generating -> OutlinedButton(onClick = aiViewModel::cancel) { Text(tr("Stop")) }
                    is AiTemplateState.Ready -> {
                        OutlinedButton(onClick = { aiViewModel.generate(aiSpec, aiDescription) }) { Text(tr("Regenerate")) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { aiViewModel.create(aiSpec, onCreated) }, enabled = nameError == null && packageError == null) { Text(tr("Create project")) }
                    }
                    AiTemplateState.Creating -> Button(onClick = {}, enabled = false) { Text(tr("Creating…")) }
                    else -> Button(
                        onClick = { aiViewModel.generate(aiSpec, aiDescription) },
                        enabled = aiDescription.isNotBlank() && nameError == null && packageError == null,
                    ) { Text(tr("Generate")) }
                }
            } else {
                Button(
                    onClick = {
                        error = null
                        viewModel.create(ProjectSpec(name.trim(), packageName.trim(), effectiveLanguage, template, minSdk, targetSdk))
                    },
                    enabled = busy == null && nameError == null && packageError == null,
                ) { Text(if (busy != null) "Creating…" else "Create project") }
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier.size(width = if (i == step) 18.dp else 8.dp, height = 8.dp).clip(CircleShape)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** A visual template card: a miniature phone preview of the generated UI above the name. */
@Composable
private fun TemplateCard(template: ProjectTemplate, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    CodeinCard(modifier, selected = selected, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(CodeinShape.Surface)
                .background(CodeinTheme.colors.base),
            contentAlignment = Alignment.Center,
        ) {
            // Phone silhouette with a schematic of the template's first screen.
            Column(
                Modifier
                    .width(56.dp)
                    .height(96.dp)
                    .clip(CodeinShape.Surface)
                    .background(CodeinTheme.colors.tier2)
                    .padding(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(Modifier.fillMaxWidth().height(Spacing.sm).clip(CodeinShape.Surface).background(accent.copy(alpha = 0.7f)))
                when (template) {
                    ProjectTemplate.ComposeMaterial3 -> {
                        repeat(3) { Box(Modifier.fillMaxWidth().height(14.dp).clip(CodeinShape.Surface).background(CodeinTheme.colors.tier3)) }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.align(Alignment.End).size(14.dp).clip(CodeinShape.Surface).background(accent))
                    }
                    ProjectTemplate.EmptyViewsActivity -> {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                        Spacer(Modifier.weight(1f))
                    }
                    ProjectTemplate.BottomNavigation -> {
                        Box(Modifier.fillMaxWidth().weight(1f).clip(CodeinShape.Surface).background(CodeinTheme.colors.tier3))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            repeat(3) { i -> Box(Modifier.size(Spacing.sm).clip(CircleShape).background(if (i == 0) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(template.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Spacer(Modifier.height(Spacing.xs))
        Text(template.description, style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.sm))
        PillChip(if (template.supportsJava) "Kotlin · Java" else "Kotlin", selected = selected)
    }
}
