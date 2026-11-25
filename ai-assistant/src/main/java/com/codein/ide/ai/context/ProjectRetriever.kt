package com.codein.ide.ai.context

import java.io.File

/**
 * Finds the project files most related to a question, so the assistant can answer questions like
 * "where is the login button created?" without the user attaching files by hand.
 *
 * Words of the question (identifiers split at camelCase and underscores) are matched against file
 * names and contents of the app sources, resources and manifest; the best few files are returned
 * as excerpts around their matches.
 */
object ProjectRetriever {

    private const val MAX_FILES = 4
    private const val EXCERPT_BUDGET = 6_000
    private const val MAX_SCANNED = 1_500
    private val sourceExtensions = setOf("kt", "java", "xml", "kts", "gradle", "toml", "json")
    private val stopWords = setOf(
        "the", "and", "for", "where", "what", "how", "why", "this", "that", "with", "from", "into", "does", "make", "add",
        "can", "you", "are", "app", "file", "code", "yang", "dan", "ini", "itu", "di", "ke", "dari", "bagaimana", "mana",
        "kenapa", "apa", "untuk", "dengan", "buat", "tolong", "bisa", "ada", "tidak",
    )

    fun keywords(question: String): Set<String> =
        Regex("[A-Za-z_][A-Za-z0-9_]*").findAll(question).flatMap { m ->
            val word = m.value
            val parts = word.split('_').flatMap { it.split(Regex("(?<=[a-z0-9])(?=[A-Z])")) }
            (parts + word)
        }.map { it.lowercase() }.filter { it.length >= 3 && it !in stopWords }.toSet()

    fun relevantFiles(root: File, question: String, exclude: String? = null): List<Pair<String, String>> {
        val words = keywords(question)
        if (words.isEmpty()) return emptyList()
        val scored = root.walkTopDown()
            .onEnter { it.name !in setOf("build", ".git", ".gradle", ".idea", ".codein", ".kotlin") }
            .filter { it.isFile && it.extension in sourceExtensions && it.length() < 400_000 }
            .take(MAX_SCANNED)
            .mapNotNull { file ->
                val rel = file.relativeTo(root).invariantSeparatorsPath
                if (rel == exclude) return@mapNotNull null
                val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
                val lower = text.lowercase()
                val name = file.name.lowercase()
                var score = 0
                for (w in words) {
                    if (name.contains(w)) score += 8
                    score += Regex(Regex.escape(w)).findAll(lower).take(20).count()
                }
                if (score == 0) null else Triple(rel, text, score)
            }
            .sortedByDescending { it.third }
            .take(MAX_FILES)
            .toList()
        return scored.map { (rel, text, _) -> rel to excerpt(text, words) }
    }

    /** The lines around the first matches, or the whole file when it is small. */
    private fun excerpt(text: String, words: Set<String>): String {
        if (text.length <= EXCERPT_BUDGET) return text
        val lines = text.lines()
        val hits = lines.indices.filter { i -> words.any { lines[i].lowercase().contains(it) } }
        val keep = sortedSetOf<Int>()
        for (h in hits) {
            for (i in (h - 6).coerceAtLeast(0)..(h + 6).coerceAtMost(lines.lastIndex)) keep += i
            if (keep.sumOf { lines[it].length + 1 } > EXCERPT_BUDGET) break
        }
        val sb = StringBuilder()
        var previous = -2
        for (i in keep) {
            if (i != previous + 1) sb.append("… line ").append(i + 1).append(":\n")
            sb.append(lines[i]).append('\n')
            previous = i
            if (sb.length > EXCERPT_BUDGET) break
        }
        return sb.toString()
    }
}
