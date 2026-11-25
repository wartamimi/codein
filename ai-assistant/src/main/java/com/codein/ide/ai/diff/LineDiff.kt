package com.codein.ide.ai.diff

/**
 * Computes a line diff between two texts as [Hunk]s with [context] lines around each change, the
 * same shape [UnifiedDiff.parse] produces. Used to show what changed between a file and an older
 * version of it, and to show an AI patch's effect hunk by hunk.
 */
object LineDiff {

    /** Largest old × new line product diffed exactly; bigger middles are shown as one replace. */
    private const val MAX_CELLS = 4_000_000L

    fun hunks(old: String, new: String, context: Int = 3): List<Hunk> {
        val a = old.lines()
        val b = new.lines()
        val ops = ops(a, b)
        if (ops.none { it.kind != HunkLine.Kind.Context }) return emptyList()
        // Group changes whose context windows touch into one hunk.
        val changeIdx = ops.indices.filter { ops[it].kind != HunkLine.Kind.Context }
        val groups = ArrayList<IntRange>()
        var start = (changeIdx.first() - context).coerceAtLeast(0)
        var end = (changeIdx.first() + context).coerceAtMost(ops.lastIndex)
        for (i in changeIdx.drop(1)) {
            if (i - context <= end + 1) end = (i + context).coerceAtMost(ops.lastIndex)
            else { groups += start..end; start = (i - context).coerceAtLeast(0); end = (i + context).coerceAtMost(ops.lastIndex) }
        }
        groups += start..end
        // Line numbers (1-based) before each op.
        val oldNo = IntArray(ops.size + 1)
        val newNo = IntArray(ops.size + 1)
        var o = 1
        var n = 1
        ops.forEachIndexed { i, op ->
            oldNo[i] = o; newNo[i] = n
            if (op.kind != HunkLine.Kind.Add) o++
            if (op.kind != HunkLine.Kind.Remove) n++
        }
        return groups.map { r ->
            val lines = ops.subList(r.first, r.last + 1)
            Hunk(
                oldStart = oldNo[r.first],
                oldCount = lines.count { it.kind != HunkLine.Kind.Add },
                newStart = newNo[r.first],
                newCount = lines.count { it.kind != HunkLine.Kind.Remove },
                lines = lines,
            )
        }
    }

    /** `git diff`-style text for [hunks] of [path]. */
    fun unified(path: String, old: String, new: String, context: Int = 3): String {
        val hunks = hunks(old, new, context)
        if (hunks.isEmpty()) return ""
        return buildString {
            append("--- a/").append(path).append('\n')
            append("+++ b/").append(path).append('\n')
            hunks.forEach { h ->
                append("@@ -${h.oldStart},${h.oldCount} +${h.newStart},${h.newCount} @@\n")
                h.lines.forEach { l ->
                    append(when (l.kind) { HunkLine.Kind.Add -> '+'; HunkLine.Kind.Remove -> '-'; HunkLine.Kind.Context -> ' ' })
                    append(l.text).append('\n')
                }
            }
        }
    }

    private fun ops(a: List<String>, b: List<String>): List<HunkLine> {
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++
        val midA = a.subList(prefix, a.size - suffix)
        val midB = b.subList(prefix, b.size - suffix)
        val out = ArrayList<HunkLine>(a.size + b.size)
        a.subList(0, prefix).forEach { out += HunkLine(HunkLine.Kind.Context, it) }
        if (midA.size.toLong() * midB.size > MAX_CELLS) {
            midA.forEach { out += HunkLine(HunkLine.Kind.Remove, it) }
            midB.forEach { out += HunkLine(HunkLine.Kind.Add, it) }
        } else {
            out += lcsOps(midA, midB)
        }
        a.subList(a.size - suffix, a.size).forEach { out += HunkLine(HunkLine.Kind.Context, it) }
        return out
    }

    private fun lcsOps(a: List<String>, b: List<String>): List<HunkLine> {
        val n = a.size
        val m = b.size
        // lengths[i][j] = LCS length of a[i..] and b[j..]
        val lengths = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lengths[i][j] = if (a[i] == b[j]) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
        }
        val out = ArrayList<HunkLine>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out += HunkLine(HunkLine.Kind.Context, a[i]); i++; j++ }
                lengths[i + 1][j] >= lengths[i][j + 1] -> { out += HunkLine(HunkLine.Kind.Remove, a[i]); i++ }
                else -> { out += HunkLine(HunkLine.Kind.Add, b[j]); j++ }
            }
        }
        while (i < n) out += HunkLine(HunkLine.Kind.Remove, a[i++])
        while (j < m) out += HunkLine(HunkLine.Kind.Add, b[j++])
        return out
    }
}
