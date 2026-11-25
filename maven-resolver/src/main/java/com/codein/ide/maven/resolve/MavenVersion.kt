package com.codein.ide.maven.resolve

/**
 * Maven/Gradle-style version comparison: numeric segments compare numerically, qualifiers are
 * ordered `alpha < beta < milestone < rc < snapshot < "" (release) < sp`, and unknown qualifiers
 * compare lexically.
 */
object MavenVersion : Comparator<String> {

    private val qualifierRank = mapOf(
        "dev" to -1,
        "alpha" to 0, "a" to 0,
        "beta" to 1, "b" to 1,
        "milestone" to 2, "m" to 2,
        "rc" to 3, "cr" to 3,
        "snapshot" to 4,
        "" to 5, "ga" to 5, "final" to 5, "release" to 5,
        "sp" to 6,
    )

    private fun tokens(version: String): List<Any> {
        val result = mutableListOf<Any>()
        val current = StringBuilder()
        var digits: Boolean? = null
        fun flush() {
            if (current.isNotEmpty()) {
                val text = current.toString()
                result += if (digits == true) text.toBigInteger() else text.lowercase()
                current.setLength(0)
            }
        }
        for (c in version) {
            when {
                c == '.' || c == '-' || c == '_' || c == '+' -> { flush(); digits = null }
                c.isDigit() -> { if (digits == false) flush(); digits = true; current.append(c) }
                else -> { if (digits == true) flush(); digits = false; current.append(c) }
            }
        }
        flush()
        return result
    }

    override fun compare(a: String, b: String): Int {
        val ta = tokens(a)
        val tb = tokens(b)
        val n = maxOf(ta.size, tb.size)
        for (i in 0 until n) {
            val x = ta.getOrNull(i)
            val y = tb.getOrNull(i)
            val cmp = compareToken(x, y)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun compareToken(x: Any?, y: Any?): Int = when {
        x == null && y == null -> 0
        x == null -> -compareToken(y, null)
        y == null -> when (x) {
            is java.math.BigInteger -> if (x.signum() == 0) 0 else 1
            else -> (qualifierRank[x as String] ?: 5).compareTo(5).let { if (it == 0) 0 else it }
        }
        x is java.math.BigInteger && y is java.math.BigInteger -> x.compareTo(y)
        x is java.math.BigInteger -> 1
        y is java.math.BigInteger -> -1
        else -> {
            val rx = qualifierRank[x as String]
            val ry = qualifierRank[y as String]
            when {
                rx != null && ry != null -> rx.compareTo(ry)
                rx != null -> -1
                ry != null -> 1
                else -> x.compareTo(y)
            }
        }
    }

    fun isStable(version: String): Boolean {
        val lower = version.lowercase()
        return listOf("alpha", "beta", "rc", "snapshot", "dev", "-m", "milestone", "eap", "preview").none { lower.contains(it) }
    }

    /**
     * Picks a concrete version for a requested spec. Supports exact versions, `+` / `x.y.+`
     * prefixes, `latest.release`, and Maven ranges like `[1.0,2.0)`.
     */
    fun select(spec: String, available: List<String>): String? {
        val sorted = available.sortedWith(this)
        val s = spec.trim()
        return when {
            s.isEmpty() || s == "+" || s == "latest.release" || s == "latest.integration" ->
                sorted.lastOrNull { isStable(it) } ?: sorted.lastOrNull()
            s.endsWith("+") -> {
                val prefix = s.removeSuffix("+")
                sorted.lastOrNull { it.startsWith(prefix) && isStable(it) } ?: sorted.lastOrNull { it.startsWith(prefix) }
            }
            s.startsWith("[") || s.startsWith("(") -> {
                val range = parseRange(s) ?: return null
                sorted.lastOrNull { range(it) && isStable(it) } ?: sorted.lastOrNull { range(it) }
            }
            else -> s
        }
    }

    fun isDynamic(spec: String): Boolean {
        val s = spec.trim()
        return s.isEmpty() || s.endsWith("+") || s.startsWith("[") || s.startsWith("(") || s.startsWith("latest.")
    }

    private fun parseRange(spec: String): ((String) -> Boolean)? {
        val first = spec.split("],[", "),(", "],(", "),[").first()
        val lowerInclusive = first.startsWith("[")
        val upperInclusive = first.endsWith("]")
        val inner = first.trim('[', ']', '(', ')')
        val parts = inner.split(',')
        if (parts.size == 1) {
            val exact = parts[0].trim()
            return { it == exact }
        }
        val lower = parts[0].trim().ifEmpty { null }
        val upper = parts[1].trim().ifEmpty { null }
        return { v ->
            val okLower = lower == null || compare(v, lower).let { if (lowerInclusive) it >= 0 else it > 0 }
            val okUpper = upper == null || compare(v, upper).let { if (upperInclusive) it <= 0 else it < 0 }
            okLower && okUpper
        }
    }

    /** For a fixed range like `[1.2.3]` or a soft requirement, returns the preferred version text. */
    fun preferred(spec: String): String {
        val s = spec.trim()
        if (s.startsWith("[") && s.endsWith("]") && !s.contains(',')) return s.trim('[', ']')
        return s
    }
}
