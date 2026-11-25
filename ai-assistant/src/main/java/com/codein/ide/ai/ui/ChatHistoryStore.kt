package com.codein.ide.ai.ui

import com.codein.ide.ai.model.ChatRole
import com.codein.ide.ai.model.ContextChip
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps each project's Codein AI conversation in `<project>/.codein/ai-chat.json`, so reopening a
 * project brings its chat back. At most [MAX_MESSAGES] messages are kept; streaming or failed
 * replies are not saved.
 */
object ChatHistoryStore {
    const val MAX_MESSAGES = 120

    @Serializable
    private data class StoredMessage(
        val role: String,
        val text: String,
        val promptText: String? = null,
        val chips: List<String> = emptyList(),
        val usage: String? = null,
    )

    @Serializable
    private data class StoredChat(val version: Int = 1, val messages: List<StoredMessage>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun fileFor(projectRoot: File): File = File(projectRoot, ".codein/ai-chat.json")

    fun load(projectRoot: File, nextId: () -> Long): List<AiMessage> {
        val file = fileFor(projectRoot)
        if (!file.isFile) return emptyList()
        val chat = runCatching { json.decodeFromString(StoredChat.serializer(), file.readText()) }.getOrNull() ?: return emptyList()
        return chat.messages.mapNotNull { m ->
            val role = runCatching { ChatRole.valueOf(m.role) }.getOrNull() ?: return@mapNotNull null
            AiMessage(
                id = nextId(),
                role = role,
                text = m.text,
                promptText = m.promptText ?: m.text,
                attachedChips = m.chips.mapNotNull { c -> ContextChip.entries.firstOrNull { it.name == c } }.toSet(),
                usage = m.usage,
            )
        }
    }

    fun save(projectRoot: File, messages: List<AiMessage>) {
        val kept = messages.filter { !it.streaming && it.error == null && it.text.isNotBlank() }.takeLast(MAX_MESSAGES)
        val file = fileFor(projectRoot)
        if (kept.isEmpty()) { file.delete(); return }
        file.parentFile?.mkdirs()
        File(projectRoot, ".codein/.gitignore").takeIf { !it.exists() }?.writeText("*\n")
        val chat = StoredChat(
            messages = kept.map { m ->
                StoredMessage(
                    role = m.role.name,
                    text = m.text,
                    // The prompt with context blocks can be large; it is only needed for the last turns.
                    promptText = m.promptText.takeIf { it != m.text && it.length < 20_000 },
                    chips = m.attachedChips.map { it.name },
                    usage = m.usage,
                )
            },
        )
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(StoredChat.serializer(), chat))
        if (!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
    }
}
