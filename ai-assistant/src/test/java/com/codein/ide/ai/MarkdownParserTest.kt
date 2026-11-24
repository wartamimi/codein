package com.codein.ide.ai

import com.codein.ide.ai.markdown.MarkdownParser
import com.codein.ide.ai.markdown.MdBlock
import com.codein.ide.ai.markdown.MdInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun parsesBlocksAndStreamingFence() {
        val md = "# Fix\nThe **cause** is `x`.\n\n- one\n- two\n\n```kotlin\nval a = 1\n```\n\n```diff\n@@ -1 +1 @@\n-a\n+b\n```\n\n```java\nclass A {"
        val blocks = MarkdownParser.parse(md)
        assertEquals(MdBlock.Heading(1, "Fix"), blocks[0])
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertEquals(2, blocks.count { it is MdBlock.ListItem })
        val code = blocks.filterIsInstance<MdBlock.Code>()
        assertEquals("kotlin", code[0].language)
        assertTrue(code[0].complete)
        assertFalse(code[1].complete)
        val diff = blocks.filterIsInstance<MdBlock.Diff>().single()
        assertEquals(1, diff.patches.single().hunks.size)
    }

    @Test
    fun parsesInline() {
        val spans = MarkdownParser.inline("Use **bold**, `code` and [docs](https://x.y) in snake_case_name")
        assertTrue(spans.contains(MdInline.Bold("bold")))
        assertTrue(spans.contains(MdInline.InlineCode("code")))
        assertTrue(spans.contains(MdInline.Link("docs", "https://x.y")))
        assertTrue(spans.last() is MdInline.Text && (spans.last() as MdInline.Text).text.endsWith("snake_case_name"))
    }
}
