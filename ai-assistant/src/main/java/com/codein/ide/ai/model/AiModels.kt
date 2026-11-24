package com.codein.ide.ai.model

/** The LLM backends Codein can stream from. */
enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
) {
    Anthropic(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-opus-5-5",
        suggestedModels = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
    ),
    OpenAiCompatible(
        displayName = "OpenAI-compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        suggestedModels = listOf("gpt-4o", "gpt-4o-mini"),
    ),
    Gemini(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-2.0-flash",
        suggestedModels = listOf("gemini-2.0-flash", "gemini-1.5-pro"),
    ),
    ;

    companion object {
        fun byName(name: String?): AiProvider = entries.firstOrNull { it.name == name } ?: Anthropic
    }
}

/** Fully resolved configuration for one request. */
data class ProviderConfig(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
) {
    val isUsable: Boolean get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 16000
    }
}

enum class ChatRole { User, Assistant }

/** One turn of the conversation as sent to the provider. */
data class ChatTurn(val role: ChatRole, val text: String)

/** Incremental output from a streaming completion. */
sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class Completed(val stopReason: String?, val inputTokens: Int?, val outputTokens: Int?) : StreamEvent
    data class Failed(val message: String, val httpCode: Int? = null) : StreamEvent
}

/** Context attachments the user can toggle in the AI dock. */
enum class ContextChip(val label: String) {
    CurrentFile("@CurrentFile"),
    Selection("@Selection"),
    BuildError("@BuildErrors"),
    Gradle("@Gradle"),
    FileTree("@FileTree"),
}
