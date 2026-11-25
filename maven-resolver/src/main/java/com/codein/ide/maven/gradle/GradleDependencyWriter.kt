package com.codein.ide.maven.gradle

import com.codein.ide.maven.model.MavenCoordinate
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Edits a module's `build.gradle(.kts)` `dependencies { }` block in place. */
@Singleton
class GradleDependencyWriter @Inject constructor() {

    sealed interface WriteResult {
        data class Added(val line: String) : WriteResult
        data class Updated(val from: String, val to: String) : WriteResult
        data object AlreadyPresent : WriteResult
    }

    fun addDependency(buildFile: File, coordinate: MavenCoordinate, configuration: String = "implementation"): WriteResult {
        val text = buildFile.readText()
        val isKts = buildFile.name.endsWith(".kts")
        val (updated, result) = addDependencyToText(text, coordinate, configuration, isKts)
        if (result !is WriteResult.AlreadyPresent) buildFile.writeText(updated)
        return result
    }

    fun addDependencyToText(text: String, coordinate: MavenCoordinate, configuration: String, isKts: Boolean): Pair<String, WriteResult> {
        val ga = Regex.escape("${coordinate.group}:${coordinate.artifact}")
        val existing = Regex("""(["'])$ga:([^"']+)\1""").find(text)
        if (existing != null) {
            val oldVersion = existing.groupValues[2]
            if (oldVersion == coordinate.version) return text to WriteResult.AlreadyPresent
            val replaced = text.replaceRange(existing.range, "${existing.groupValues[1]}${coordinate.group}:${coordinate.artifact}:${coordinate.version}${existing.groupValues[1]}")
            return replaced to WriteResult.Updated(oldVersion, coordinate.version)
        }
        val line = if (isKts) "    $configuration(\"$coordinate\")" else "    $configuration '$coordinate'"
        val block = Regex("""(?m)^dependencies\s*\{""").find(text)
        val updated = if (block != null) {
            val close = matchingBrace(text, block.range.last)
            if (close < 0) text + "\n" + "dependencies {\n$line\n}\n"
            else {
                val before = text.substring(0, close).trimEnd('\n', ' ')
                before + "\n" + line + "\n" + text.substring(close)
            }
        } else {
            text.trimEnd() + "\n\ndependencies {\n$line\n}\n"
        }
        return updated to WriteResult.Added(line.trim())
    }

    fun removeDependency(buildFile: File, group: String, artifact: String): Boolean {
        val text = buildFile.readText()
        val pattern = Regex("""(?m)^[ \t]*\w+\s*\(?\s*["']${Regex.escape("$group:$artifact")}:[^"']+["']\s*\)?[ \t]*\r?\n?""")
        val updated = pattern.replace(text, "")
        if (updated == text) return false
        buildFile.writeText(updated)
        return true
    }

    private fun matchingBrace(text: String, open: Int): Int {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }
}
