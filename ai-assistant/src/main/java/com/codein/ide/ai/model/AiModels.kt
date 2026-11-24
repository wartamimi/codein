package com.codein.ide.ai.model

/** Wire protocol spoken by a provider's endpoint. */
enum class ApiFormat(val displayName: String, val endpointHint: String) {
    Anthropic("Anthropic Messages", "<base>/v1/messages · models from <base>/v1/models"),
    OpenAi("OpenAI-compatible", "<base>/chat/completions · models from <base>/models"),
    Gemini("Google Gemini", "<base>/v1beta/models/<model>:streamGenerateContent"),
    ;

    companion object {
        fun byName(name: String?): ApiFormat = entries.firstOrNull { it.name == name } ?: OpenAi
    }
}

/** The LLM backends Codein can stream from. */
enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    /** Protocol of the built-in providers. [Custom] lets the user pick it instead. */
    val format: ApiFormat,
    /** Local servers (Ollama, LM Studio, llama.cpp) behind [Custom] usually need no key. */
    val requiresKey: Boolean = true,
) {
    Anthropic(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-opus-5-5",
        suggestedModels = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        format = ApiFormat.Anthropic,
    ),
    OpenAiCompatible(
        displayName = "OpenAI-compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        suggestedModels = listOf("gpt-4o", "gpt-4o-mini"),
        format = ApiFormat.OpenAi,
    ),
    Gemini(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-2.0-flash",
        suggestedModels = listOf("gemini-2.0-flash", "gemini-1.5-pro"),
        format = ApiFormat.Gemini,
    ),

    /**
     * Any endpoint the user defines: a name, a base URL, an optional key and the protocol it speaks
     * (OpenRouter, Groq, DeepSeek, Together, Azure-style proxies, Ollama, LM Studio, vLLM…).
     */
    Custom(
        displayName = "Custom",
        defaultBaseUrl = "",
        defaultModel = "",
        suggestedModels = emptyList(),
        format = ApiFormat.OpenAi,
        requiresKey = false,
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
    val format: ApiFormat = provider.format,
    /** Name shown in messages; the user's own name for a [AiProvider.Custom] endpoint. */
    val label: String = provider.displayName,
) {
    val hasCredentials: Boolean get() = apiKey.isNotBlank() || !provider.requiresKey
    val isUsable: Boolean get() = hasCredentials && model.isNotBlank() && baseUrl.isNotBlank()

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
