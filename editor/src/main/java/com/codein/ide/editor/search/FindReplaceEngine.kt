package com.codein.ide.editor.search

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class FindOptions(
    val regex: Boolean = false,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
)

data class FindMatch(val start: Int, val end: Int, val line: Int, val groups: List<String?>)

sealed interface QueryValidation {
    data object Empty : QueryValidation
    data object Valid : QueryValidation
    data class Invalid(val message: String, val index: Int) : QueryValidation
}

/**
 * Pure find/replace engine shared by the in-editor find bar and the global project search.
 * Supports regex (with real-time syntax validation), match case, whole word and `$1`-style
 * capture-group substitutions on replace.
 */
object FindReplaceEngine {

    fun compile(query: String, options: FindOptions): Result<Pattern> = runCatching {
        var source = if (options.regex) query else Pattern.quote(query)
        if (options.wholeWord) source = "\\b(?:$source)\\b"
        var flags = Pattern.MULTILINE
        if (!options.matchCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        Pattern.compile(source, flags)
    }

    fun validate(query: String, options: FindOptions): QueryValidation {
        if (query.isEmpty()) return QueryValidation.Empty
        if (!options.regex) return QueryValidation.Valid
        return try {
            Pattern.compile(query)
            QueryValidation.Valid
        } catch (e: PatternSyntaxException) {
            QueryValidation.Invalid(e.description ?: "Invalid pattern", e.index)
        }
    }

    fun findAll(text: CharSequence, query: String, options: FindOptions, limit: Int = 10_000): List<FindMatch> {
        if (query.isEmpty()) return emptyList()
        val pattern = compile(query, options).getOrNull() ?: return emptyList()
        val matcher = pattern.matcher(text)
        val results = mutableListOf<FindMatch>()
        val lineStarts = lineStarts(text)
        while (matcher.find() && results.size < limit) {
            if (matcher.end() == matcher.start()) {
                // Skip zero-length matches (e.g. `^`) to avoid infinite loops and invisible hits.
                if (matcher.end() >= text.length) break
                continue
            }
            val groups = (0..matcher.groupCount()).map { matcher.group(it) }
            results += FindMatch(matcher.start(), matcher.end(), lineOf(lineStarts, matcher.start()), groups)
        }
        return results
    }

    /** Replaces every match and returns the new text and the number of replacements. */
    fun replaceAll(text: String, query: String, replacement: String, options: FindOptions): Pair<String, Int> {
        if (query.isEmpty()) return text to 0
        val pattern = compile(query, options).getOrNull() ?: return text to 0
        val matcher = pattern.matcher(text)
        val sb = StringBuffer()
        var count = 0
        val rep = if (options.regex) translateReplacement(replacement) else Matcher.quoteReplacement(replacement)
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) continue
            matcher.appendReplacement(sb, rep)
            count++
        }
        matcher.appendTail(sb)
        return sb.toString() to count
    }

    /** Replacement for a single match (handles groups in regex mode). */
    fun replacementFor(text: String, match: FindMatch, query: String, replacement: String, options: FindOptions): String {
        if (!options.regex) return replacement
        val pattern = compile(query, options).getOrNull() ?: return replacement
        val region = text.substring(match.start, match.end)
        val m = pattern.matcher(text)
        m.region(match.start, text.length)
        m.useTransparentBounds(true)
        m.useAnchoringBounds(false)
        return if (m.lookingAt()) {
            val sb = StringBuffer()
            m.appendReplacement(sb, translateReplacement(replacement))
            sb.substring(match.start)
        } else region
    }

    /** Converts VS Code style `$1` / `${name}` to Java's `$1` / `${name}` while keeping `\n`, `\t`. */
    private fun translateReplacement(replacement: String): String =
        replacement.replace("\\n", "\n").replace("\\t", "\t")

    fun lineStarts(text: CharSequence): IntArray {
        val starts = ArrayList<Int>(128)
        starts += 0
        for (i in text.indices) if (text[i] == '\n') starts += i + 1
        return starts.toIntArray()
    }

    /** 0-based line index for a char offset. */
    fun lineOf(lineStarts: IntArray, offset: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }
}
