package com.codein.ide.ai.ui

import com.codein.ide.ai.model.ChatRole
import com.codein.ide.ai.model.ContextChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

class ChatHistoryStoreTest {

    @Test
    fun savesFinishedMessagesAndRestoresThem() {
        val root = Files.createTempDirectory("chat").toFile()
        val messages = listOf(
            AiMessage(1, ChatRole.User, "Why does it crash?", promptText = "context…\nWhy does it crash?", attachedChips = setOf(ContextChip.CurrentFile)),
            AiMessage(2, ChatRole.Assistant, "Because of a null view.", usage = "10 in · 20 out"),
            AiMessage(3, ChatRole.Assistant, "partial", streaming = true),
            AiMessage(4, ChatRole.Assistant, "", error = "Network"),
        )
        ChatHistoryStore.save(root, messages)
        val ids = AtomicLong(100)
        val restored = ChatHistoryStore.load(root) { ids.getAndIncrement() }
        assertEquals(listOf("Why does it crash?", "Because of a null view."), restored.map { it.text })
        assertEquals("context…\nWhy does it crash?", restored[0].promptText)
        assertEquals(setOf(ContextChip.CurrentFile), restored[0].attachedChips)
        assertEquals(listOf(100L, 101L), restored.map { it.id })

        ChatHistoryStore.save(root, emptyList())
        assertFalse(ChatHistoryStore.fileFor(root).exists())
    }
}
