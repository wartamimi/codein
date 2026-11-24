package com.codein.ide.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.ai.context.AiContextBuilder
import com.codein.ide.ai.context.AiContextSnapshot
import com.codein.ide.ai.model.ChatRole
import com.codein.ide.ai.model.ChatTurn
import com.codein.ide.ai.model.ContextChip
import com.codein.ide.ai.model.StreamEvent
import com.codein.ide.ai.net.LlmClient
import com.codein.ide.ai.settings.AiSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class AiMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    /** For user messages: the full prompt (with context blocks) that was sent to the model. */
    val promptText: String = text,
    val attachedChips: Set<ContextChip> = emptySet(),
    val streaming: Boolean = false,
    val error: String? = null,
    val usage: String? = null,
)

data class AiAssistantState(
    val messages: List<AiMessage> = emptyList(),
    val input: String = "",
    val chips: Set<ContextChip> = setOf(ContextChip.CurrentFile, ContextChip.BuildError),
    val streaming: Boolean = false,
    val providerLabel: String = "",
    val modelLabel: String = "",
    val configured: Boolean = false,
    val currentFileName: String? = null,
    val hasSelection: Boolean = false,
    val hasBuildError: Boolean = false,
    val hasGradle: Boolean = false,
)

sealed interface AiIntent {
    data class Input(val text: String) : AiIntent
    data object Send : AiIntent
    data object Stop : AiIntent
    data class ToggleChip(val chip: ContextChip) : AiIntent
    data object Clear : AiIntent
    data object ExplainAndFix : AiIntent
    data object Retry : AiIntent
}

/**
 * MVI view model for the Codein AI dock. The workspace pushes the latest editor/build context via
 * [updateContext]; edits proposed by the model are applied by the host through the panel callbacks.
 */
@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val settings: AiSettingsRepository,
    private val client: LlmClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AiAssistantState())
    val state: StateFlow<AiAssistantState> = _state.asStateFlow()

    private var snapshot = AiContextSnapshot()
    private var streamJob: Job? = null
    private val ids = AtomicLong(1)

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                val active = s.active
                _state.update {
                    it.copy(
                        providerLabel = s.activeProvider.displayName,
                        modelLabel = active?.model.orEmpty(),
                        configured = active?.hasApiKey == true,
                    )
                }
            }
        }
    }

    fun updateContext(newSnapshot: AiContextSnapshot) {
        snapshot = newSnapshot
        _state.update {
            it.copy(
                currentFileName = newSnapshot.currentFilePath?.let { p -> File(p).name },
                hasSelection = newSnapshot.selectionText.isNotBlank(),
                hasBuildError = newSnapshot.hasBuildErrors,
                hasGradle = newSnapshot.gradleFiles.isNotEmpty(),
            )
        }
    }

    fun onIntent(intent: AiIntent) {
        when (intent) {
            is AiIntent.Input -> _state.update { it.copy(input = intent.text) }
            AiIntent.Send -> {
                val question = _state.value.input.trim()
                if (question.isNotEmpty()) {
                    _state.update { it.copy(input = "") }
                    ask(question, _state.value.chips)
                }
            }
            AiIntent.Stop -> stop()
            is AiIntent.ToggleChip -> _state.update {
                it.copy(chips = if (intent.chip in it.chips) it.chips - intent.chip else it.chips + intent.chip)
            }
            AiIntent.Clear -> {
                stop()
                _state.update { it.copy(messages = emptyList()) }
            }
            AiIntent.ExplainAndFix -> explainAndFix()
            AiIntent.Retry -> retry()
        }
    }

    /** Entry point for the "AI Fix" extra key and the build console's error cards. */
    fun explainAndFix() {
        val chips = _state.value.chips + ContextChip.BuildError + ContextChip.CurrentFile
        ask(AiContextBuilder.explainAndFixRequest(snapshot), chips)
    }

    /** Asks about the current selection ("Explain selection" from the editor). */
    fun explainSelection() {
        if (snapshot.selectionText.isBlank()) return
        ask("Explain what the selected code does and point out any bugs.", _state.value.chips + ContextChip.Selection)
    }

    private fun retry() {
        val messages = _state.value.messages
        val lastUser = messages.lastOrNull { it.role == ChatRole.User } ?: return
        val index = messages.indexOf(lastUser)
        _state.update { it.copy(messages = messages.subList(0, index)) }
        ask(lastUser.text, _state.value.chips)
    }

    private fun stop() {
        streamJob?.cancel()
        streamJob = null
        _state.update { s ->
            s.copy(streaming = false, messages = s.messages.map { if (it.streaming) it.copy(streaming = false, usage = "stopped") else it })
        }
    }

    private fun ask(question: String, chips: Set<ContextChip>) {
        streamJob?.cancel()
        val prompt = AiContextBuilder.build(question, snapshot, chips)
        val userMessage = AiMessage(ids.getAndIncrement(), ChatRole.User, question, prompt.userMessage, prompt.attachedChips)
        val assistantId = ids.getAndIncrement()
        val history = _state.value.messages
            .filter { it.error == null && it.text.isNotBlank() }
            .takeLast(MAX_HISTORY)
            .map { ChatTurn(it.role, it.text) }
        _state.update {
            it.copy(
                streaming = true,
                messages = it.messages.map { m -> if (m.streaming) m.copy(streaming = false) else m } +
                    userMessage + AiMessage(assistantId, ChatRole.Assistant, "", streaming = true),
            )
        }
        streamJob = viewModelScope.launch {
            val config = settings.activeConfig()
            val turns = history + ChatTurn(ChatRole.User, prompt.userMessage)
            val buffer = StringBuilder()
            var lastPush = 0L
            client.stream(config, prompt.system, turns).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        buffer.append(event.text)
                        val now = System.currentTimeMillis()
                        if (now - lastPush >= UI_THROTTLE_MS) {
                            lastPush = now
                            val text = buffer.toString()
                            updateMessage(assistantId) { it.copy(text = text) }
                        }
                    }
                    is StreamEvent.Completed -> {
                        val usage = listOfNotNull(
                            event.inputTokens?.let { "$it in" },
                            event.outputTokens?.let { "$it out" },
                            event.stopReason?.takeIf { it == "max_tokens" || it == "length" || it == "MAX_TOKENS" }?.let { "truncated" },
                        ).joinToString(" · ")
                        updateMessage(assistantId) { it.copy(text = buffer.toString(), streaming = false, usage = usage.ifEmpty { null }) }
                    }
                    is StreamEvent.Failed -> updateMessage(assistantId) {
                        it.copy(text = buffer.toString(), streaming = false, error = event.message)
                    }
                }
            }
            updateMessage(assistantId) { if (it.streaming) it.copy(text = buffer.toString(), streaming = false) else it }
            _state.update { it.copy(streaming = false) }
        }
    }

    private fun updateMessage(id: Long, transform: (AiMessage) -> AiMessage) {
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) transform(it) else it }) }
    }

    private companion object {
        const val MAX_HISTORY = 8
        const val UI_THROTTLE_MS = 50L
    }
}
