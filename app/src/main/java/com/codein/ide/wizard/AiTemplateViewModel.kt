package com.codein.ide.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.ai.model.ChatRole
import com.codein.ide.ai.model.ChatTurn
import com.codein.ide.ai.model.StreamEvent
import com.codein.ide.ai.net.LlmClient
import com.codein.ide.ai.settings.AiSettingsRepository
import com.codein.ide.ai.template.AiProjectFormat
import com.codein.ide.ai.template.GeneratedProject
import com.codein.ide.project.ProjectLanguage
import com.codein.ide.project.ProjectRepository
import com.codein.ide.project.ProjectSpec
import com.codein.ide.project.ProjectTemplate
import com.codein.ide.project.ProjectTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the "Template from AI" flow. */
sealed interface AiTemplateState {
    data object Idle : AiTemplateState
    data class Generating(val receivedChars: Int, val providerLabel: String) : AiTemplateState
    data class Ready(val project: GeneratedProject) : AiTemplateState
    data class Failed(val message: String) : AiTemplateState
    data object Creating : AiTemplateState
}

/**
 * Generates a project from a plain-language description with the configured AI provider. The
 * model edits a built-in base template (so the Gradle setup is always one Codein can build), and
 * the user reviews every generated file before the project is created.
 */
@HiltViewModel
class AiTemplateViewModel @Inject constructor(
    private val llm: LlmClient,
    private val aiSettings: AiSettingsRepository,
    private val projects: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AiTemplateState>(AiTemplateState.Idle)
    val state: StateFlow<AiTemplateState> = _state.asStateFlow()

    private var job: Job? = null

    /** The template the AI starts from: Compose, or an Empty Views Activity in the chosen language. */
    fun baseSpec(name: String, packageName: String, language: ProjectLanguage, compose: Boolean, minSdk: Int = 26, targetSdk: Int = 35) = ProjectSpec(
        name = name,
        packageName = packageName,
        language = if (compose) ProjectLanguage.Kotlin else language,
        template = if (compose) ProjectTemplate.ComposeMaterial3 else ProjectTemplate.EmptyViewsActivity,
        minSdk = minSdk,
        targetSdk = targetSdk,
    )

    fun generate(spec: ProjectSpec, description: String) {
        if (description.isBlank()) {
            _state.value = AiTemplateState.Failed("Describe the app you want first.")
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            val base = ProjectTemplates.generate(spec)
            val editable = base.filterKeys { path ->
                path == "app/src/main/AndroidManifest.xml" ||
                    path.startsWith("app/src/main/java/") || path.startsWith("app/src/main/kotlin/") ||
                    (path.startsWith("app/src/main/res/") && (path.contains("/layout/") || path.contains("/values/") || path.contains("/menu/")))
            }
            val declared = Regex("""implementation\("([^"]+)"\)""").findAll(base["app/build.gradle.kts"].orEmpty()).map { it.groupValues[1] }.toList()
            val compose = spec.template == ProjectTemplate.ComposeMaterial3
            val system = AiProjectFormat.systemPrompt(
                appName = spec.name,
                packageName = spec.packageName,
                language = spec.effectiveLanguage.displayName,
                compose = compose,
                minSdk = spec.minSdk,
                baseFiles = editable,
                dependencies = declared,
            )
            val config = aiSettings.activeConfig().let { it.copy(maxOutputTokens = maxOf(it.maxOutputTokens, ProjectOutputTokens)) }
            val text = StringBuilder()
            _state.value = AiTemplateState.Generating(0, config.label)
            var failure: String? = null
            llm.stream(config, system, listOf(ChatTurn(ChatRole.User, "Build this app:\n\n" + description.trim()))).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        text.append(event.text)
                        _state.value = AiTemplateState.Generating(text.length, config.label)
                    }
                    is StreamEvent.Failed -> failure = event.message
                    is StreamEvent.Completed -> if (event.stopReason in TruncatedStopReasons) {
                        failure = "The answer was cut off at the output limit (${config.maxOutputTokens} tokens). Try a smaller app or a model with a larger output limit."
                    }
                }
            }
            val parsed = AiProjectFormat.parse(text.toString())
            _state.value = when {
                failure != null -> AiTemplateState.Failed(failure!!)
                parsed.files.isEmpty() -> AiTemplateState.Failed(
                    "The AI did not return any usable files." + (parsed.rejected.takeIf { it.isNotEmpty() }?.joinToString("\n", "\n") ?: ""),
                )
                else -> AiTemplateState.Ready(parsed)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = AiTemplateState.Idle
    }

    /** Creates the project and reports its folder through [onCreated]. */
    fun create(spec: ProjectSpec, onCreated: (String) -> Unit) {
        val ready = _state.value as? AiTemplateState.Ready ?: return
        _state.value = AiTemplateState.Creating
        viewModelScope.launch {
            projects.create(spec, ready.project.files, ready.project.dependencies).fold(
                onSuccess = { onCreated(it.absolutePath) },
                onFailure = { _state.value = AiTemplateState.Failed(it.message ?: "Could not create the project") },
            )
        }
    }

    private companion object {
        const val ProjectOutputTokens = 16000
        val TruncatedStopReasons = setOf("max_tokens", "length", "MAX_TOKENS")
    }
}
