package com.codein.ide.ai.diff

/** One line of a hunk. */
data class HunkLine(val kind: Kind, val text: String) {
    enum class Kind { Context, Add, Remove }
}

/** A `@@ -a,b +c,d @@` hunk. [oldStart] is 1-based, or -1 when the header carried no numbers. */
data class Hunk(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int, val lines: List<HunkLine>) {
    val oldLines: List<String> get() = lines.filter { it.kind != HunkLine.Kind.Add }.map { it.text }
    val newLines: List<String> get() = lines.filter { it.kind != HunkLine.Kind.Remove }.map { it.text }
    val additions: Int get() = lines.count { it.kind == HunkLine.Kind.Add }
    val deletions: Int get() = lines.count { it.kind == HunkLine.Kind.Remove }
}

/** All hunks targeting one file. Paths have `a/`/`b/` prefixes stripped; null when absent. */
data class FilePatch(val oldPath: String?, val newPath: String?, val hunks: List<Hunk>) {
    val targetPath: String? get() = newPath?.takeIf { it != "/dev/null" } ?: oldPath
    val additions: Int get() = hunks.sumOf { it.additions }
    val deletions: Int get() = hunks.sumOf { it.deletions }
}

sealed interface PatchResult {
    data class Applied(val text: String, val hunksApplied: Int, val fuzzyHunks: Int) : PatchResult
    data class Failed(val hunkIndex: Int, val reason: String) : PatchResult
}

/**
 * Parser and applier for unified diffs as produced by `git diff` and (loosely) by LLMs. Hunk
 * placement is content-based: the header line number is only a hint, which makes AI-generated
 * patches with slightly wrong line numbers still apply. Whitespace-insensitive matching is used
 * as a fallback and reported via [PatchResult.Applied.fuzzyHunks].
 */
object UnifiedDiff {

    private val hunkHeader = Regex("""^@@(?:\s*-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s*@@.*|(?:\s*@@)?\s*)$""")

    fun looksLikeDiff(text: String): Boolean = text.lineSequence().any { hunkHeader.matches(it) }

    fun parse(text: String): List<FilePatch> {
        val patches = mutableListOf<FilePatch>()
        var oldPath: String? = null
        var newPath: String? = null
        var hunks = mutableListOf<Hunk>()
        var current: MutableList<HunkLine>? = null
        var header: IntArray? = null

        fun closeHunk() {
            val lines = current ?: return
            val h = header ?: intArrayOf(-1, 0, -1, 0)
            if (lines.isNotEmpty()) hunks += Hunk(h[0], h[1], h[2], h[3], lines.toList())
            current = null
            header = null
        }

        fun closeFile() {
            closeHunk()
            if (hunks.isNotEmpty()) patches += FilePatch(oldPath, newPath, hunks.toList())
            hunks = mutableListOf()
            oldPath = null
            newPath = null
        }

        val all = text.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < all.size) {
            val line = all[i]
            when {
                line.startsWith("diff --git ") -> closeFile()
                line.startsWith("--- ") && all.getOrNull(i + 1)?.startsWith("+++ ") == true -> {
                    if (hunks.isNotEmpty() || current != null) closeFile()
                    oldPath = stripPath(line.removePrefix("--- "))
                    newPath = stripPath(all[i + 1].removePrefix("+++ "))
                    i++
                }
                hunkHeader.matches(line) -> {
                    closeHunk()
                    val m = hunkHeader.matchEntire(line)!!
                    val g = m.groupValues
                    header = if (g[1].isEmpty()) intArrayOf(-1, 0, -1, 0) else intArrayOf(
                        g[1].toInt(),
                        g[2].ifEmpty { "1" }.toInt(),
                        g[3].toInt(),
                        g[4].ifEmpty { "1" }.toInt(),
                    )
                    current = mutableListOf()
                }
                current != null -> {
                    val target = current!!
                    when {
                        line.startsWith("\\") -> Unit // "\ No newline at end of file"
                        line.startsWith("+") -> target += HunkLine(HunkLine.Kind.Add, line.substring(1))
                        line.startsWith("-") -> target += HunkLine(HunkLine.Kind.Remove, line.substring(1))
                        line.startsWith(" ") -> target += HunkLine(HunkLine.Kind.Context, line.substring(1))
                        line.isEmpty() -> {
                            // Blank context lines frequently lose their leading space; keep them
                            // unless this is the trailing newline of the whole diff.
                            if (i < all.lastIndex) target += HunkLine(HunkLine.Kind.Context, "")
                        }
                        else -> closeHunk()
                    }
                }
            }
            i++
        }
        closeFile()
        return patches.map { patch -> patch.copy(hunks = patch.hunks.map(::trimTrailingBlankContext)) }
    }

    private fun trimTrailingBlankContext(h: Hunk): Hunk {
        val lines = h.lines.toMutableList()
        while (lines.size > 1 && lines.last().kind == HunkLine.Kind.Context && lines.last().text.isEmpty() &&
            h.oldCount > 0 && lines.count { it.kind != HunkLine.Kind.Add } > h.oldCount
        ) lines.removeAt(lines.lastIndex)
        return h.copy(lines = lines)
    }

    private fun stripPath(raw: String): String? {
        val path = raw.substringBefore('\t').trim()
        if (path == "/dev/null") return path
        return when {
            path.startsWith("a/") || path.startsWith("b/") -> path.substring(2)
            else -> path
        }.ifEmpty { null }
    }

    /** Applies every hunk of [patch] to [original]. */
    fun apply(original: String, patch: FilePatch): PatchResult {
        val endsWithNewline = original.endsWith("\n")
        val lines = original.replace("\r\n", "\n").let { if (endsWithNewline) it.dropLast(1) else it }
            .let { if (original.isEmpty()) mutableListOf<String>() else it.split('\n').toMutableList() }
        var offset = 0
        var searchFrom = 0
        var fuzzy = 0
        patch.hunks.forEachIndexed { index, hunk ->
            val oldBlock = hunk.oldLines
            val newBlock = hunk.newLines
            if (oldBlock.isEmpty()) {
                // Pure insertion: place at the hinted line (or the end of the file).
                val at = if (hunk.oldStart < 0) lines.size else (hunk.oldStart + offset).coerceIn(0, lines.size)
                lines.addAll(at, newBlock)
                offset += newBlock.size
                searchFrom = at + newBlock.size
                return@forEachIndexed
            }
            val hint = if (hunk.oldStart > 0) hunk.oldStart - 1 + offset else searchFrom
            var hunkFuzzy = false
            var position = locate(lines, oldBlock, hint, searchFrom) { a, b -> a == b }
            if (position < 0) {
                position = locate(lines, oldBlock, hint, searchFrom) { a, b -> a.trim() == b.trim() }
                hunkFuzzy = position >= 0
            }
            if (position < 0) {
                position = locate(lines, oldBlock, hint, 0) { a, b -> a.trim() == b.trim() }
                hunkFuzzy = position >= 0
            }
            if (hunkFuzzy) fuzzy++
            if (position < 0) {
                val first = oldBlock.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                return PatchResult.Failed(index, "Hunk ${index + 1} does not match the file (expected context starting with \"${first.take(60)}\").")
            }
            val adjusted = if (hunkFuzzy) preserveIndentation(lines.subList(position, position + oldBlock.size), hunk) else newBlock
            repeat(oldBlock.size) { lines.removeAt(position) }
            lines.addAll(position, adjusted)
            offset += adjusted.size - oldBlock.size
            searchFrom = position + adjusted.size
        }
        val text = lines.joinToString("\n") + if (endsWithNewline || original.isEmpty()) "\n" else ""
        return PatchResult.Applied(text, patch.hunks.size, fuzzy)
    }

    /** When matched whitespace-insensitively, keep the file's original text for context lines. */
    private fun preserveIndentation(actualOld: List<String>, hunk: Hunk): List<String> {
        val out = ArrayList<String>()
        var oldIndex = 0
        for (line in hunk.lines) {
            when (line.kind) {
                HunkLine.Kind.Context -> { out += actualOld.getOrElse(oldIndex) { line.text }; oldIndex++ }
                HunkLine.Kind.Remove -> oldIndex++
                HunkLine.Kind.Add -> out += line.text
            }
        }
        return out
    }

    /** Finds [block] in [lines], preferring the match closest to [hint] and not before [minStart]. */
    private fun locate(lines: List<String>, block: List<String>, hint: Int, minStart: Int, eq: (String, String) -> Boolean): Int {
        val last = lines.size - block.size
        if (last < 0) return -1
        fun matchesAt(start: Int): Boolean {
            for (k in block.indices) if (!eq(lines[start + k], block[k])) return false
            return true
        }
        val lo = minStart.coerceIn(0, last.coerceAtLeast(0))
        val center = hint.coerceIn(lo, last)
        var delta = 0
        while (center - delta >= lo || center + delta <= last) {
            val down = center + delta
            if (down in lo..last && matchesAt(down)) return down
            val up = center - delta
            if (delta > 0 && up in lo..last && matchesAt(up)) return up
            delta++
        }
        return -1
    }

    /** Builds a unified diff between two texts (LCS-based) for previews and Git-free comparisons. */
    fun diff(oldText: String, newText: String, path: String, context: Int = 3): String {
        val a = if (oldText.isEmpty()) emptyList() else oldText.replace("\r\n", "\n").removeSuffix("\n").split('\n')
        val b = if (newText.isEmpty()) emptyList() else newText.replace("\r\n", "\n").removeSuffix("\n").split('\n')
        val n = a.size
        val m = b.size
        if (n.toLong() * m.toLong() > 4_000_000L) {
            return "--- a/$path\n+++ b/$path\n@@ -1,$n +1,$m @@\n" + a.joinToString("\n") { "-$it" } + "\n" + b.joinToString("\n") { "+$it" } + "\n"
        }
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        data class Op(val kind: HunkLine.Kind, val text: String, val oldLine: Int, val newLine: Int)
        val ops = ArrayList<Op>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { ops += Op(HunkLine.Kind.Context, a[i], i, j); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { ops += Op(HunkLine.Kind.Remove, a[i], i, j); i++ }
                else -> { ops += Op(HunkLine.Kind.Add, b[j], i, j); j++ }
            }
        }
        while (i < n) { ops += Op(HunkLine.Kind.Remove, a[i], i, j); i++ }
        while (j < m) { ops += Op(HunkLine.Kind.Add, b[j], i, j); j++ }
        val changed = ops.indices.filter { ops[it].kind != HunkLine.Kind.Context }
        if (changed.isEmpty()) return ""
        val sb = StringBuilder("--- a/$path\n+++ b/$path\n")
        var start = 0
        while (start < changed.size) {
            var end = start
            while (end + 1 < changed.size && changed[end + 1] - changed[end] <= context * 2) end++
            val from = (changed[start] - context).coerceAtLeast(0)
            val to = (changed[end] + context).coerceAtMost(ops.lastIndex)
            val slice = ops.subList(from, to + 1)
            val oldCount = slice.count { it.kind != HunkLine.Kind.Add }
            val newCount = slice.count { it.kind != HunkLine.Kind.Remove }
            val oldStart = slice.first().oldLine + if (oldCount == 0) 0 else 1
            val newStart = slice.first().newLine + if (newCount == 0) 0 else 1
            sb.append("@@ -").append(oldStart).append(',').append(oldCount).append(" +").append(newStart).append(',').append(newCount).append(" @@\n")
            slice.forEach { op ->
                sb.append(when (op.kind) { HunkLine.Kind.Context -> ' '; HunkLine.Kind.Add -> '+'; HunkLine.Kind.Remove -> '-' }).append(op.text).append('\n')
            }
            start = end + 1
        }
        return sb.toString()
    }
}
