package com.codein.ide.ai.markdown

import com.codein.ide.ai.diff.FilePatch
import com.codein.ide.ai.diff.UnifiedDiff

/** Block-level markdown structure used to render assistant replies. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String, val complete: Boolean) : MdBlock
    data class Diff(val raw: String, val patches: List<FilePatch>, val complete: Boolean) : MdBlock
    data object Rule : MdBlock
}

/** Inline spans for paragraphs: bold, italic, inline code and links. */
sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Bold(val text: String) : MdInline
    data class Italic(val text: String) : MdInline
    data class InlineCode(val text: String) : MdInline
    data class Link(val text: String, val url: String) : MdInline
}

/**
 * A small, streaming-tolerant markdown parser. An unterminated code fence (as seen while tokens
 * are still arriving) becomes a [MdBlock.Code] with `complete = false`, so the UI can show a live
 * code card instead of raw backticks.
 */
object MarkdownParser {

    private val fence = Regex("""^\s*(```|~~~)\s*([\w+#.-]*)\s*.*$""")
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val bullet = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")
    private val rule = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.setLength(0)
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fenceMatch = fence.matchEntire(line)
            when {
                fenceMatch != null -> {
                    flushParagraph()
                    val marker = fenceMatch.groupValues[1]
                    val language = fenceMatch.groupValues[2].lowercase()
                    val body = StringBuilder()
                    var j = i + 1
                    var closed = false
                    while (j < lines.size) {
                        if (lines[j].trim().startsWith(marker) && lines[j].trim().trimStart(marker[0]).isBlank()) { closed = true; break }
                        if (body.isNotEmpty()) body.append('\n')
                        body.append(lines[j])
                        j++
                    }
                    val code = body.toString()
                    blocks += if (language == "diff" || language == "patch" || (language.isEmpty() && UnifiedDiff.looksLikeDiff(code))) {
                        MdBlock.Diff(code, if (closed) UnifiedDiff.parse(code) else emptyList(), closed)
                    } else {
                        MdBlock.Code(language, code, closed)
                    }
                    i = if (closed) j + 1 else lines.size
                    continue
                }
                line.isBlank() -> flushParagraph()
                rule.matches(line) -> { flushParagraph(); blocks += MdBlock.Rule }
                heading.matches(line) -> {
                    flushParagraph()
                    val m = heading.matchEntire(line)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim().trimEnd('#').trim())
                }
                bullet.matches(line) -> {
                    flushParagraph()
                    val m = bullet.matchEntire(line)!!
                    val marker = m.groupValues[2].let { if (it[0].isDigit()) it else "•" }
                    blocks += MdBlock.ListItem(marker, m.groupValues[3], m.groupValues[1].length / 2)
                }
                line.trimStart().startsWith(">") -> {
                    flushParagraph()
                    blocks += MdBlock.Quote(line.trimStart().removePrefix(">").trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    /** Splits a paragraph into inline spans. Unclosed markers are kept as plain text. */
    fun inline(text: String): List<MdInline> {
        val out = mutableListOf<MdInline>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { out += MdInline.Text(plain.toString()); plain.setLength(0) } }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) { flush(); out += MdInline.InlineCode(text.substring(i + 1, end)); i = end + 1; continue }
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val marker = text.substring(i, i + 2)
                    val end = text.indexOf(marker, i + 2)
                    if (end > i + 2) { flush(); out += MdInline.Bold(text.substring(i + 2, end)); i = end + 2; continue }
                }
                (c == '*' || c == '_') && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                    val end = text.indexOf(c, i + 1)
                    val boundaryOk = c == '*' || i == 0 || !text[i - 1].isLetterOrDigit()
                    if (end > i + 1 && boundaryOk) { flush(); out += MdInline.Italic(text.substring(i + 1, end)); i = end + 1; continue }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    if (close > i && text.getOrNull(close + 1) == '(') {
                        val urlEnd = text.indexOf(')', close + 2)
                        if (urlEnd > close) {
                            flush()
                            out += MdInline.Link(text.substring(i + 1, close), text.substring(close + 2, urlEnd))
                            i = urlEnd + 1
                            continue
                        }
                    }
                }
            }
            plain.append(c)
            i++
        }
        flush()
        return out
    }
}
