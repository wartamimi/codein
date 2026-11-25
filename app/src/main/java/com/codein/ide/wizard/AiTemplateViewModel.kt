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
    data class Generating(
        val receivedChars: Int,
        val providerLabel: String,
        /** The file the model is writing now, from the last `<<<FILE path>>>` header. */
        val currentFile: String? = null,
        val finishedFiles: Int = 0,
        val part: Int = 1,
    ) : AiTemplateState
    data class Ready(
        val project: GeneratedProject,
        /** Path of a file being rewritten on its own ("Regenerate this file"), if any. */
        val regenerating: String? = null,
        val regenerateError: String? = null,
    ) : AiTemplateState
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
            val request = ChatTurn(ChatRole.User, "Build this app:\n\n" + description.trim())
            // Large apps do not fit one answer: when the output limit is hit, keep the finished
            // blocks and ask the model to continue after them, a few times at most.
            var round = 0
            while (true) {
                var truncated = false
                val turns = if (round == 0) listOf(request) else listOf(
                    request,
                    ChatTurn(ChatRole.Assistant, text.toString()),
                    ChatTurn(ChatRole.User, AiProjectFormat.CONTINUE_PROMPT),
                )
                val roundStart = text.length
                llm.stream(config, system, turns).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            text.append(event.text)
                            _state.value = progressOf(text, config.label, round + 1)
                        }
                        is StreamEvent.Failed -> failure = event.message
                        is StreamEvent.Completed -> truncated = event.stopReason in TruncatedStopReasons
                    }
                }
                if (failure != null || !truncated) break
                val kept = AiProjectFormat.completePrefix(text.toString())
                if (kept.length <= roundStart || round >= MaxContinuations) {
                    failure = "The answer was cut off at the output limit (${config.maxOutputTokens} tokens) " +
                        (if (round == 0) "before a single file was finished" else "after ${round + 1} parts") +
                        ". Describe a smaller app, or raise the output limit in Settings → AI providers."
                    break
                }
                text.setLength(kept.length)
                text.append('\n')
                round++
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

    private fun progressOf(text: CharSequence, label: String, part: Int): AiTemplateState.Generating {
        val header = text.lastIndexOf("<<<FILE ")
        val current = if (header < 0) null else text.substring(header + 8, text.indexOf(">>>", header).takeIf { it > 0 } ?: text.length).trim()
        val finished = Regex("<<<END>>>").findAll(text).count()
        return AiTemplateState.Generating(text.length, label, current?.removePrefix("app/src/main/"), finished, part)
    }

    /**
     * Rewrites one generated file on its own, with an optional instruction ("make the button
     * bigger"), keeping every other file as it is.
     */
    fun regenerateFile(spec: ProjectSpec, description: String, path: String, instruction: String) {
        val ready = _state.value as? AiTemplateState.Ready ?: return
        if (ready.regenerating != null) return
        _state.value = ready.copy(regenerating = path, regenerateError = null)
        job = viewModelScope.launch {
            val config = aiSettings.activeConfig().let { it.copy(maxOutputTokens = maxOf(it.maxOutputTokens, ProjectOutputTokens)) }
            val system = AiProjectFormat.systemPrompt(
                appName = spec.name,
                packageName = spec.packageName,
                language = spec.effectiveLanguage.displayName,
                compose = spec.template == ProjectTemplate.ComposeMaterial3,
                minSdk = spec.minSdk,
                baseFiles = ready.project.files,
                dependencies = ready.project.dependencies,
            )
            val ask = buildString {
                append("The app: ").append(description.trim()).append("\n\n")
                append("The files above are the current project. Rewrite only ").append(path)
                if (instruction.isNotBlank()) append(", with this change: ").append(instruction.trim())
                append(". Keep it consistent with the other files. Answer with exactly one <<<FILE ").append(path).append(">>> block.")
            }
            val text = StringBuilder()
            var failure: String? = null
            llm.stream(config, system, listOf(ChatTurn(ChatRole.User, ask))).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> text.append(event.text)
                    is StreamEvent.Failed -> failure = event.message
                    is StreamEvent.Completed -> if (event.stopReason in TruncatedStopReasons) failure = "The file was cut off at the output limit."
                }
            }
            val content = AiProjectFormat.parse(text.toString()).files[path]
            val current = _state.value as? AiTemplateState.Ready ?: return@launch
            _state.value = when {
                failure != null -> current.copy(regenerating = null, regenerateError = failure)
                content == null -> current.copy(regenerating = null, regenerateError = "The AI did not return $path.")
                else -> current.copy(project = current.project.copy(files = current.project.files + (path to content)), regenerating = null)
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
        const val MaxContinuations = 4
        val TruncatedStopReasons = setOf("max_tokens", "length", "MAX_TOKENS")
    }
}
