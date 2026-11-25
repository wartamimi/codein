package com.codein.ide.ai.net

import com.codein.ide.ai.model.ApiFormat
import com.codein.ide.ai.model.ChatRole
import com.codein.ide.ai.model.ChatTurn
import com.codein.ide.ai.model.ProviderConfig
import com.codein.ide.ai.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Streams chat completions from Anthropic (Messages API), any OpenAI-compatible Chat Completions
 * endpoint, or Google Gemini (`streamGenerateContent?alt=sse`) over raw HTTP + SSE.
 */
@Singleton
class LlmClient @Inject constructor() {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun stream(config: ProviderConfig, system: String, turns: List<ChatTurn>): Flow<StreamEvent> = flow {
        if (!config.isUsable) {
            val missing = when {
                !config.hasCredentials -> "No API key configured for ${config.label}."
                config.baseUrl.isBlank() -> "No base URL set for ${config.label}."
                else -> "No model selected for ${config.label}."
            }
            emit(StreamEvent.Failed("$missing Open Settings → AI providers."))
            return@flow
        }
        val request = when (config.format) {
            ApiFormat.Anthropic -> anthropicRequest(config, system, turns)
            ApiFormat.OpenAi -> openAiRequest(config, system, turns)
            ApiFormat.Gemini -> geminiRequest(config, system, turns)
        }
        val call = http.newCall(request)
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                emit(StreamEvent.Failed("Network error: ${e.message ?: e.javaClass.simpleName}"))
                return@flow
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    emit(StreamEvent.Failed(errorMessage(resp), resp.code))
                    return@flow
                }
                val body = resp.body ?: run {
                    emit(StreamEvent.Failed("Empty response body", resp.code))
                    return@flow
                }
                val reader = SseReader(body.source())
                when (config.format) {
                    ApiFormat.Anthropic -> consumeAnthropic(reader)
                    ApiFormat.OpenAi -> consumeOpenAi(reader)
                    ApiFormat.Gemini -> consumeGemini(reader)
                }
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            emit(StreamEvent.Failed("Stream interrupted: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- Anthropic

    private fun anthropicRequest(config: ProviderConfig, system: String, turns: List<ChatTurn>): Request {
        val useFallbacks = ANTHROPIC_FALLBACK_MODELS.any { config.model.startsWith(it) }
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", config.maxOutputTokens)
            put("stream", true)
            if (system.isNotBlank()) put("system", system)
            if (useFallbacks) put("fallbacks", "default")
            putJsonArray("messages") {
                mergedTurns(turns).forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.role == ChatRole.User) "user" else "assistant")
                        put("content", turn.text)
                    }
                }
            }
        }
        val builder = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/v1/messages")
            .auth(config)
            .header("content-type", "application/json")
            .header("accept", "text/event-stream")
        if (useFallbacks) builder.header("anthropic-beta", ANTHROPIC_FALLBACK_BETA)
        return builder.post(body.toString().toRequestBody(JSON_MEDIA)).build()
    }

    private suspend fun FlowCollector<StreamEvent>.consumeAnthropic(reader: SseReader) {
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        var stopReason: String? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val event = reader.next() ?: break
            val obj = parse(event.data) ?: continue
            when (obj.string("type") ?: event.event) {
                "message_start" -> inputTokens = obj.obj("message")?.obj("usage")?.int("input_tokens")
                "content_block_delta" -> {
                    val delta = obj.obj("delta")
                    if (delta?.string("type") == "text_delta") delta.string("text")?.let { emit(StreamEvent.TextDelta(it)) }
                }
                "message_delta" -> {
                    obj.obj("delta")?.string("stop_reason")?.let { stopReason = it }
                    obj.obj("usage")?.int("output_tokens")?.let { outputTokens = it }
                }
                "error" -> {
                    emit(StreamEvent.Failed(obj.obj("error")?.string("message") ?: "Anthropic stream error"))
                    return
                }
                "message_stop" -> break
            }
        }
        if (stopReason == "refusal") {
            emit(StreamEvent.Failed("The model declined this request (stop_reason: refusal)."))
            return
        }
        emit(StreamEvent.Completed(stopReason, inputTokens, outputTokens))
    }

    // ---------------------------------------------------------------- OpenAI-compatible

    private fun openAiRequest(config: ProviderConfig, system: String, turns: List<ChatTurn>): Request {
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", true)
            put("max_tokens", config.maxOutputTokens)
            putJsonObject("stream_options") { put("include_usage", true) }
            putJsonArray("messages") {
                if (system.isNotBlank()) addJsonObject { put("role", "system"); put("content", system) }
                mergedTurns(turns).forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.role == ChatRole.User) "user" else "assistant")
                        put("content", turn.text)
                    }
                }
            }
        }
        return Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .auth(config)
            .header("content-type", "application/json")
            .header("accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    private suspend fun FlowCollector<StreamEvent>.consumeOpenAi(reader: SseReader) {
        var finish: String? = null
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val event = reader.next() ?: break
            if (event.data.trim() == "[DONE]") break
            val obj = parse(event.data) ?: continue
            obj.obj("error")?.let {
                emit(StreamEvent.Failed(it.string("message") ?: "Provider stream error"))
                return
            }
            obj.obj("usage")?.let { usage ->
                inputTokens = usage.int("prompt_tokens") ?: inputTokens
                outputTokens = usage.int("completion_tokens") ?: outputTokens
            }
            val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: continue
            choice.obj("delta")?.string("content")?.let { if (it.isNotEmpty()) emit(StreamEvent.TextDelta(it)) }
            choice.string("finish_reason")?.let { finish = it }
        }
        emit(StreamEvent.Completed(finish, inputTokens, outputTokens))
    }

    // ---------------------------------------------------------------- Gemini

    private fun geminiRequest(config: ProviderConfig, system: String, turns: List<ChatTurn>): Request {
        val body = buildJsonObject {
            if (system.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", system) } }
                }
            }
            putJsonArray("contents") {
                mergedTurns(turns).forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.role == ChatRole.User) "user" else "model")
                        putJsonArray("parts") { addJsonObject { put("text", turn.text) } }
                    }
                }
            }
            putJsonObject("generationConfig") { put("maxOutputTokens", config.maxOutputTokens) }
        }
        val model = URLEncoder.encode(config.model, "UTF-8")
        return Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/v1beta/models/$model:streamGenerateContent?alt=sse")
            .auth(config)
            .header("content-type", "application/json")
            .header("accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    private suspend fun FlowCollector<StreamEvent>.consumeGemini(reader: SseReader) {
        var finish: String? = null
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val event = reader.next() ?: break
            val obj = parse(event.data) ?: continue
            obj.obj("error")?.let {
                emit(StreamEvent.Failed(it.string("message") ?: "Gemini stream error"))
                return
            }
            obj.obj("usageMetadata")?.let { usage ->
                inputTokens = usage.int("promptTokenCount") ?: inputTokens
                outputTokens = usage.int("candidatesTokenCount") ?: outputTokens
            }
            val candidate = (obj["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: continue
            val parts = candidate.obj("content")?.get("parts") as? JsonArray
            parts?.forEach { part -> (part as? JsonObject)?.string("text")?.let { if (it.isNotEmpty()) emit(StreamEvent.TextDelta(it)) } }
            candidate.string("finishReason")?.let { finish = it }
        }
        if (finish == "SAFETY") {
            emit(StreamEvent.Failed("Gemini blocked the response (finishReason: SAFETY)."))
            return
        }
        emit(StreamEvent.Completed(finish, inputTokens, outputTokens))
    }

    // ---------------------------------------------------------------- model list

    /**
     * Fetches the model ids the endpoint offers: `GET /v1/models` (Anthropic), `GET /models`
     * (OpenAI-compatible, including Ollama, LM Studio and OpenRouter) or `GET /v1beta/models`
     * (Gemini, filtered to models that support generateContent). Follows pagination.
     *
     * @throws ModelListException with a user-facing message when the request fails.
     */
    suspend fun listModels(config: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) throw ModelListException("Enter a base URL first.")
        if (!config.hasCredentials) throw ModelListException("Enter an API key first.")
        val base = config.baseUrl.trimEnd('/')
        val ids = LinkedHashSet<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val url = when (config.format) {
                ApiFormat.Anthropic -> "$base/v1/models?limit=1000" + (cursor?.let { "&after_id=" + enc(it) } ?: "")
                ApiFormat.OpenAi -> "$base/models"
                ApiFormat.Gemini -> "$base/v1beta/models?pageSize=1000" + (cursor?.let { "&pageToken=" + enc(it) } ?: "")
            }
            val page = getJson(Request.Builder().url(url).auth(config).header("accept", "application/json").get().build())
            cursor = when (config.format) {
                ApiFormat.Anthropic -> {
                    val data = page["data"] as? JsonArray
                    data?.forEach { m -> (m as? JsonObject)?.string("id")?.let(ids::add) }
                    if ((page["has_more"] as? JsonPrimitive)?.booleanOrNull == true) page.string("last_id") else null
                }
                ApiFormat.OpenAi -> {
                    // OpenAI and most proxies use {data:[{id}]}; a few local servers return {models:[{name}]}.
                    val data = (page["data"] ?: page["models"]) as? JsonArray
                    data?.forEach { m ->
                        val o = m as? JsonObject
                        (o?.string("id") ?: o?.string("name") ?: (m as? JsonPrimitive)?.contentOrNull)?.let(ids::add)
                    }
                    null
                }
                ApiFormat.Gemini -> {
                    (page["models"] as? JsonArray)?.forEach { m ->
                        val o = m as? JsonObject ?: return@forEach
                        val methods = (o["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        if (methods == null || "streamGenerateContent" in methods || "generateContent" in methods) {
                            o.string("name")?.removePrefix("models/")?.let(ids::add)
                        }
                    }
                    page.string("nextPageToken")?.takeIf { it.isNotBlank() }
                }
            }
            pages++
        } while (cursor != null && pages < MAX_MODEL_PAGES)
        if (ids.isEmpty()) throw ModelListException("The endpoint returned no models.")
        ids.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private suspend fun getJson(request: Request): JsonObject {
        val call = http.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw ModelListException(errorMessage(resp))
                    val raw = resp.body?.string().orEmpty()
                    val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                    val parsed = when (obj) {
                        is JsonObject -> obj
                        // Some servers answer with a bare array of model objects.
                        is JsonArray -> JsonObject(mapOf("data" to obj))
                        else -> throw ModelListException("Unexpected response from ${request.url.host}: ${raw.take(160)}")
                    }
                    cont.resume(parsed)
                }
            } catch (e: ModelListException) {
                cont.resumeWithException(e)
            } catch (e: IOException) {
                cont.resumeWithException(ModelListException("Network error: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Adds the credential header the protocol expects; keyless local servers get none. */
    private fun Request.Builder.auth(config: ProviderConfig): Request.Builder = apply {
        if (config.format == ApiFormat.Anthropic) header("anthropic-version", ANTHROPIC_VERSION)
        if (config.apiKey.isBlank()) return@apply
        when (config.format) {
            ApiFormat.Anthropic -> header("x-api-key", config.apiKey)
            ApiFormat.OpenAi -> header("Authorization", "Bearer ${config.apiKey}")
            ApiFormat.Gemini -> header("x-goog-api-key", config.apiKey)
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Providers require strictly alternating roles starting with a user turn; merge adjacent ones. */
    private fun mergedTurns(turns: List<ChatTurn>): List<ChatTurn> {
        val out = ArrayList<ChatTurn>()
        turns.filter { it.text.isNotBlank() }.forEach { turn ->
            val last = out.lastOrNull()
            if (last != null && last.role == turn.role) out[out.lastIndex] = ChatTurn(turn.role, last.text + "\n\n" + turn.text)
            else out += turn
        }
        while (out.firstOrNull()?.role == ChatRole.Assistant) out.removeAt(0)
        return out
    }

    private fun errorMessage(response: Response): String {
        val raw = runCatching { response.body?.string() }.getOrNull().orEmpty()
        val parsed = parse(raw)
        val detail = parsed?.obj("error")?.string("message")
            ?: (parsed?.get("error") as? JsonPrimitive)?.contentOrNull
            ?: parsed?.string("message")
            ?: raw.take(300)
        val hint = when (response.code) {
            401, 403 -> " Check the API key."
            404 -> " Check the model name and base URL."
            429 -> " Rate limited; try again shortly."
            else -> ""
        }
        return "HTTP ${response.code}: $detail$hint"
    }

    private fun parse(data: String): JsonObject? = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    companion object {
        const val ANTHROPIC_VERSION = "2026-10-21"
        const val ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"
        /** Models that accept `fallbacks: "default"` for refusal rescue. */
        private val ANTHROPIC_FALLBACK_MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-1", "claude-fable-5-2")
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val MAX_MODEL_PAGES = 10
    }
}

/** A model-list request failed; [message] is shown to the user as-is. */
class ModelListException(message: String) : Exception(message)
