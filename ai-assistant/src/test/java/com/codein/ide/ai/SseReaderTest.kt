package com.codein.ide.ai

import com.codein.ide.ai.net.SseReader
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseReaderTest {
    @Test
    fun readsNamedMultilineAndCommentEvents() {
        val stream = ": keep-alive\n" +
            "event: message_start\n" +
            "data: {\"a\":1}\n\n" +
            "data: line1\n" +
            "data: line2\n\n" +
            "data:[DONE]"
        val reader = SseReader(Buffer().writeUtf8(stream))
        val first = reader.next()!!
        assertEquals("message_start", first.event)
        assertEquals("{\"a\":1}", first.data)
        val second = reader.next()!!
        assertNull(second.event)
        assertEquals("line1\nline2", second.data)
        assertEquals("[DONE]", reader.next()!!.data)
        assertNull(reader.next())
    }
}
