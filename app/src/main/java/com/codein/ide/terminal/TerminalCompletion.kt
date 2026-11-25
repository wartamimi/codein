package com.codein.ide.terminal

import java.io.File

/** Tab completion of file and folder names for the line-based terminal. */
object TerminalCompletion {

    /**
     * Completes the last word of [input] as a path relative to [cwd]. Returns the new input and the
     * candidates that matched, so the panel can list them when the word stays ambiguous.
     */
    fun complete(input: String, cwd: File): Result {
        val start = input.lastIndexOf(' ') + 1
        val word = input.substring(start)
        val slash = word.lastIndexOf('/')
        val dirPart = if (slash >= 0) word.substring(0, slash + 1) else ""
        val namePart = word.substring(slash + 1)
        val dir = when {
            dirPart.startsWith("/") -> File(dirPart)
            dirPart.startsWith("~/") -> File(cwd, dirPart.removePrefix("~/"))
            else -> File(cwd, dirPart)
        }
        val matches = dir.listFiles().orEmpty()
            .filter { it.name.startsWith(namePart) && (namePart.startsWith(".") || !it.name.startsWith(".")) }
            .sortedBy { it.name }
        if (matches.isEmpty()) return Result(input, emptyList())
        val names = matches.map { if (it.isDirectory) it.name + "/" else it.name }
        val completedName = if (matches.size == 1) {
            names.single() + if (matches.single().isDirectory) "" else " "
        } else {
            commonPrefix(names)
        }
        return Result(input.substring(0, start) + dirPart + completedName, if (matches.size > 1) names else emptyList())
    }

    /** Follows a `cd` command so completion keeps looking in the shell's current folder. */
    fun afterCommand(command: String, cwd: File, home: File): File {
        val parts = command.trim().split(Regex("\\s+"))
        if (parts.firstOrNull() != "cd" || parts.size > 2) return cwd
        val target = parts.getOrNull(1) ?: return home
        val next = when {
            target == "~" -> home
            target.startsWith("~/") -> File(home, target.removePrefix("~/"))
            target.startsWith("/") -> File(target)
            else -> File(cwd, target)
        }.normalize()
        return if (next.isDirectory) next else cwd
    }

    private fun commonPrefix(values: List<String>): String {
        var prefix = values.first()
        values.drop(1).forEach { v -> while (!v.startsWith(prefix)) prefix = prefix.dropLast(1) }
        return prefix
    }

    data class Result(val text: String, val candidates: List<String>)
}
