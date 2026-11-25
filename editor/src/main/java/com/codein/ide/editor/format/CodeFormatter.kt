package com.codein.ide.editor.format

/**
 * "Format document" for the languages Codein edits. It fixes layout, never meaning:
 * - Kotlin, Java, Groovy, JSON: re-indents every line by bracket depth (strings, chars and comments
 *   are skipped when counting), indents continuation lines that start with `.`, `?.`, `&&`, `||`,
 *   `?:` or an operator, removes trailing spaces and squeezes runs of blank lines to one.
 * - XML: re-indents by element depth and puts every attribute of a multi-attribute start tag on
 *   its own line, aligned one level deeper, like Android Studio's default layout style.
 */
object CodeFormatter {

    enum class Language { Kotlin, Java, Groovy, Json, Xml }

    fun languageFor(fileName: String): Language? = when (fileName.substringAfterLast('.').lowercase()) {
        "kt", "kts" -> Language.Kotlin
        "java" -> Language.Java
        "gradle" -> Language.Groovy
        "json" -> Language.Json
        "xml" -> Language.Xml
        else -> null
    }

    fun format(text: String, language: Language, indent: Int = 4): String = when (language) {
        Language.Xml -> formatXml(text, indent)
        else -> formatBraces(text, indent, continuationIndent = language != Language.Json)
    }

    // ------------------------------------------------------------------ brace languages

    private val continuationStarts = listOf(".", "?.", "&&", "||", "?:", "+ ", "- ", "* ", "/ ", ": ")

    private fun formatBraces(text: String, indent: Int, continuationIndent: Boolean): String {
        val pad = " ".repeat(indent)
        val out = StringBuilder()
        var depth = 0
        var inBlockComment = false
        var inRawString = false
        var blankRun = 0
        val lines = text.replace("\r\n", "\n").split('\n')
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (inRawString) {
                // Text inside a Kotlin """ string is content: keep it exactly as written.
                out.append(rawLine.trimEnd())
                val scan = scan(rawLine, inBlockComment = false, inRawString = true)
                inRawString = scan.inRawString
                depth = (depth + scan.delta).coerceAtLeast(0)
                if (index < lines.lastIndex) out.append('\n')
                return@forEachIndexed
            }
            if (line.isEmpty()) {
                blankRun++
                if (blankRun <= 1 && out.isNotEmpty()) out.append('\n')
                return@forEachIndexed
            }
            blankRun = 0
            val scan = scan(line, inBlockComment, inRawString = false)
            // Closing brackets at the start of the line dedent the line itself.
            val level = when {
                inBlockComment -> depth
                else -> (depth - scan.leadingClosers).coerceAtLeast(0)
            }
            val continuation = continuationIndent && !inBlockComment && scan.leadingClosers == 0 &&
                continuationStarts.any { line.startsWith(it) } && !line.startsWith("...")
            val extra = if (continuation) 1 else 0
            val commentAlign = if (inBlockComment && line.startsWith("*")) " " else ""
            out.append(pad.repeat(level + extra)).append(commentAlign).append(line)
            if (index < lines.lastIndex) out.append('\n')
            depth = (depth + scan.delta).coerceAtLeast(0)
            inBlockComment = scan.inBlockComment
            inRawString = scan.inRawString
        }
        var result = out.toString()
        while (result.endsWith("\n\n")) result = result.dropLast(1)
        if (text.endsWith("\n") && !result.endsWith("\n")) result += "\n"
        return result
    }

    private data class Scan(val delta: Int, val leadingClosers: Int, val inBlockComment: Boolean, val inRawString: Boolean)

    /** Bracket balance of one line, skipping strings, chars and comments. */
    private fun scan(line: String, inBlockComment: Boolean, inRawString: Boolean): Scan {
        var delta = 0
        var leading = 0
        var seenCode = false
        var block = inBlockComment
        var raw = inRawString
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                raw -> {
                    if (line.startsWith("\"\"\"", i)) { raw = false; i += 2 }
                }
                block -> {
                    if (line.startsWith("*/", i)) { block = false; i++ }
                }
                line.startsWith("//", i) -> return Scan(delta, leading, false, false)
                line.startsWith("/*", i) -> { block = true; i++ }
                line.startsWith("\"\"\"", i) -> { raw = true; i += 2; seenCode = true }
                c == '"' || c == '\'' -> {
                    seenCode = true
                    i++
                    while (i < line.length && line[i] != c) { if (line[i] == '\\') i++; i++ }
                }
                c == '{' || c == '(' || c == '[' -> { delta++; seenCode = true }
                c == '}' || c == ')' || c == ']' -> {
                    delta--
                    if (!seenCode) leading++
                }
                !c.isWhitespace() -> seenCode = true
            }
            i++
        }
        return Scan(delta, leading, block, raw)
    }

    // ------------------------------------------------------------------ XML

    private fun formatXml(text: String, indent: Int): String {
        val pad = " ".repeat(indent)
        val tokens = xmlTokens(text)
        val out = StringBuilder()
        var depth = 0
        var blankPending = false
        for (t in tokens) {
            when (t) {
                is XmlToken.Blank -> blankPending = out.isNotEmpty()
                is XmlToken.Text -> {
                    if (blankPending) { out.append('\n'); blankPending = false }
                    out.append(pad.repeat(depth)).append(t.text).append('\n')
                }
                is XmlToken.Tag -> {
                    if (blankPending) { out.append('\n'); blankPending = false }
                    val raw = t.raw
                    val closing = raw.startsWith("</")
                    val special = raw.startsWith("<?") || raw.startsWith("<!")
                    val selfClosing = raw.endsWith("/>")
                    if (closing) depth = (depth - 1).coerceAtLeast(0)
                    out.append(pad.repeat(depth)).append(if (special || closing) raw else layoutStartTag(raw, pad.repeat(depth + 1))).append('\n')
                    if (!closing && !special && !selfClosing) depth++
                }
            }
        }
        // Join "<tag>text</tag>" written on one line back together.
        val joined = Regex("""(?m)^(\s*)(<[^/!?][^>]*[^/]>|<[A-Za-z][\w:.\-]*>)\n\s*([^<\n][^\n]*)\n\s*(</[^>]+>)$""")
            .replace(out.toString()) { m -> if ('\n' in m.groupValues[2]) m.value else m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4] }
        var result = joined.trimEnd('\n') + "\n"
        if (!text.endsWith("\n")) result = result.trimEnd('\n')
        return result
    }

    private sealed interface XmlToken {
        data class Tag(val raw: String) : XmlToken
        data class Text(val text: String) : XmlToken
        data object Blank : XmlToken
    }

    private fun xmlTokens(text: String): List<XmlToken> {
        val out = ArrayList<XmlToken>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '<') {
                val end = when {
                    text.startsWith("<!--", i) -> text.indexOf("-->", i).let { if (it < 0) text.length else it + 3 }
                    text.startsWith("<![CDATA[", i) -> text.indexOf("]]>", i).let { if (it < 0) text.length else it + 3 }
                    else -> tagEnd(text, i)
                }
                out += XmlToken.Tag(normalizeTag(text.substring(i, end)))
                i = end
            } else {
                val next = text.indexOf('<', i).let { if (it < 0) text.length else it }
                val chunk = text.substring(i, next)
                if (chunk.isBlank()) {
                    if (chunk.count { it == '\n' } >= 2) out += XmlToken.Blank
                } else {
                    chunk.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { out += XmlToken.Text(it) }
                }
                i = next
            }
        }
        return out
    }

    /** End of a tag starting at [start], honoring quoted attribute values that contain `>`. */
    private fun tagEnd(text: String, start: Int): Int {
        var i = start + 1
        var quote: Char? = null
        while (i < text.length) {
            val c = text[i]
            if (quote != null) { if (c == quote) quote = null }
            else if (c == '"' || c == '\'') quote = c
            else if (c == '>') return i + 1
            i++
        }
        return text.length
    }

    private fun normalizeTag(raw: String): String {
        if (raw.startsWith("<!--") || raw.startsWith("<![CDATA[")) return raw.trim()
        return raw.lines().joinToString(" ") { it.trim() }.replace(Regex("\\s+(/?>)$"), "$1").trim()
    }

    private val attribute = Regex("""([\w:.\-]+)\s*=\s*("[^"]*"|'[^']*')""")

    private fun layoutStartTag(tag: String, attrPad: String): String {
        val selfClosing = tag.endsWith("/>")
        val inner = tag.removePrefix("<").removeSuffix(">").removeSuffix("/").trim()
        val name = inner.substringBefore(' ').substringBefore('\n')
        val attrs = attribute.findAll(inner.removePrefix(name)).map { "${it.groupValues[1]}=${it.groupValues[2]}" }.toList()
        val end = if (selfClosing) " />" else ">"
        return when (attrs.size) {
            0 -> "<$name$end"
            1 -> "<$name ${attrs[0]}$end"
            else -> buildString {
                append('<').append(name)
                attrs.forEach { append('\n').append(attrPad).append(it) }
                append(end)
            }
        }
    }
}
