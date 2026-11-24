package com.codein.ide.editor.outline

/** Kind of a declaration found in a source file. */
enum class SymbolKind(val label: String) { Class("class"), Interface("interface"), Object("object"), Enum("enum"), Function("fun"), Property("val"), Element("tag") }

/** A declaration with its 1-based line and nesting depth (number of enclosing blocks). */
data class OutlineSymbol(val name: String, val kind: SymbolKind, val line: Int, val depth: Int, val detail: String = "")

/**
 * Lightweight, language-aware outline for Kotlin, Java and XML files. It powers "Go to symbol"
 * (`#` in the command palette) as a fast way to navigate long files.
 *
 * The scanner strips comments and string literals before matching so braces inside them do not
 * affect nesting, then matches declarations line by line.
 */
object SymbolOutline {

    private val kotlinType = Regex("""^\s*(?:(?:public|private|internal|protected|abstract|open|sealed|data|enum|annotation|inner|value|inline|expect|actual|fun)\s+)*(class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    private val kotlinCompanion = Regex("""^\s*(?:(?:private|internal|public)\s+)?companion\s+object(?:\s+([A-Za-z_][A-Za-z0-9_]*))?""")
    private val kotlinFun = Regex("""^\s*(?:(?:public|private|internal|protected|override|open|abstract|suspend|inline|operator|infix|tailrec|external|actual|expect|final)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][A-Za-z0-9_.<>?, ]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
    private val kotlinProperty = Regex("""^\s*(?:(?:public|private|internal|protected|override|open|const|lateinit|abstract)\s+)*(val|var)\s+(?:[A-Za-z_][A-Za-z0-9_.<>?]*\.)?([A-Za-z_][A-Za-z0-9_]*)""")
    private val javaType = Regex("""^\s*(?:(?:public|private|protected|static|final|abstract|sealed|non-sealed|strictfp)\s+)*(class|interface|enum|record|@interface)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    private val javaMethod = Regex("""^\s*(?:(?:public|private|protected|static|final|abstract|synchronized|native|default|strictfp)\s+)*(?:<[^>]*>\s*)?[A-Za-z_][A-Za-z0-9_.<>\[\], ?]*\s+([A-Za-z_][A-Za-z0-9_]*)\s*\([^;]*$""")
    private val xmlTag = Regex("""<([A-Za-z_][A-Za-z0-9_.:\-]*)\b[^>]*?(?:android:id\s*=\s*"@\+?id/([A-Za-z0-9_]+)"|android:name\s*=\s*"([^"]+)"|name\s*=\s*"([^"]+)")""")
    private val javaKeywords = setOf("if", "for", "while", "switch", "catch", "return", "new", "else", "throw", "synchronized", "try", "do")

    fun outline(fileName: String, text: String): List<OutlineSymbol> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> scanBraced(text, ::matchKotlin)
            "java" -> scanBraced(text, ::matchJava)
            "xml" -> scanXml(text)
            else -> emptyList()
        }
    }

    private fun matchKotlin(line: String): Pair<String, SymbolKind>? {
        kotlinCompanion.find(line)?.let { return (it.groupValues[1].ifEmpty { "Companion" }) to SymbolKind.Object }
        kotlinType.find(line)?.let { m ->
            val kind = when {
                m.groupValues[1] == "interface" -> SymbolKind.Interface
                m.groupValues[1] == "object" -> SymbolKind.Object
                Regex("""\benum\s+class\b""").containsMatchIn(line) -> SymbolKind.Enum
                else -> SymbolKind.Class
            }
            return m.groupValues[2] to kind
        }
        kotlinFun.find(line)?.let { return it.groupValues[1] to SymbolKind.Function }
        kotlinProperty.find(line)?.let { return it.groupValues[2] to SymbolKind.Property }
        return null
    }

    private fun matchJava(line: String): Pair<String, SymbolKind>? {
        javaType.find(line)?.let { m ->
            val kind = when (m.groupValues[1]) {
                "interface", "@interface" -> SymbolKind.Interface
                "enum" -> SymbolKind.Enum
                else -> SymbolKind.Class
            }
            return m.groupValues[2] to kind
        }
        javaMethod.find(line)?.let { m ->
            val name = m.groupValues[1]
            if (name !in javaKeywords && !line.trimStart().startsWith("return ") && !line.contains('=')) return name to SymbolKind.Function
        }
        return null
    }

    /** Scans brace-delimited languages, tracking depth on comment- and string-free code. */
    private fun scanBraced(text: String, matcher: (String) -> Pair<String, SymbolKind>?): List<OutlineSymbol> {
        val result = mutableListOf<OutlineSymbol>()
        var depth = 0
        var inBlockComment = false
        // Depth of the innermost function body; declarations inside it (locals) are skipped.
        val functionDepths = ArrayDeque<Int>()
        text.lines().forEachIndexed { index, raw ->
            val (code, stillInComment) = stripCommentsAndStrings(raw, inBlockComment)
            inBlockComment = stillInComment
            val insideFunction = functionDepths.isNotEmpty() && depth > functionDepths.last()
            if (!insideFunction && code.isNotBlank()) {
                matcher(code)?.let { (name, kind) ->
                    result += OutlineSymbol(name, kind, index + 1, depth, code.trim().take(120))
                    if (kind == SymbolKind.Function) functionDepths.addLast(depth)
                }
            }
            for (c in code) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth = (depth - 1).coerceAtLeast(0)
                        while (functionDepths.isNotEmpty() && depth <= functionDepths.last()) functionDepths.removeLast()
                    }
                }
            }
            // A single-expression function (`fun x() = ...`) has no body block.
            if (functionDepths.isNotEmpty() && functionDepths.last() == depth && !code.contains('{') && result.lastOrNull()?.line == index + 1) {
                functionDepths.removeLast()
            }
        }
        return result
    }

    private fun scanXml(text: String): List<OutlineSymbol> {
        val result = mutableListOf<OutlineSymbol>()
        var depth = 0
        text.lines().forEachIndexed { index, line ->
            xmlTag.findAll(line).forEach { m ->
                val tag = m.groupValues[1].substringAfterLast('.')
                val name = m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] }
                result += OutlineSymbol(name, SymbolKind.Element, index + 1, depth, tag)
            }
            depth += Regex("""<[A-Za-z]""").findAll(line).count() - Regex("""</|/>""").findAll(line).count()
            depth = depth.coerceAtLeast(0)
        }
        return result
    }

    /** Returns the line with comments and string/char literal contents removed. */
    internal fun stripCommentsAndStrings(line: String, startsInBlockComment: Boolean): Pair<String, Boolean> {
        val out = StringBuilder(line.length)
        var inBlock = startsInBlockComment
        var i = 0
        while (i < line.length) {
            val c = line[i]
            val next = line.getOrNull(i + 1)
            when {
                inBlock -> {
                    if (c == '*' && next == '/') { inBlock = false; i++ }
                }
                c == '/' && next == '/' -> break
                c == '/' && next == '*' -> { inBlock = true; i++ }
                c == '"' || c == '\'' -> {
                    val quote = c
                    out.append(quote)
                    i++
                    while (i < line.length && line[i] != quote) {
                        if (line[i] == '\\') i++
                        i++
                    }
                    out.append(quote)
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString() to inBlock
    }
}
