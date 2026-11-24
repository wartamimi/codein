package com.codein.ide.ai.context

import com.codein.ide.ai.model.ContextChip
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.Severity
import java.io.File

/** Everything the workspace knows that the assistant may attach to a prompt. */
data class AiContextSnapshot(
    val projectName: String = "",
    val projectRoot: File? = null,
    val currentFilePath: String? = null,
    val currentFileText: String? = null,
    val selectionText: String = "",
    val cursorLine: Int = 0,
    val diagnostics: List<CompilerDiagnostic> = emptyList(),
    val gradleFiles: Map<String, String> = emptyMap(),
    val fileTree: List<String> = emptyList(),
) {
    val hasBuildErrors: Boolean get() = diagnostics.any { it.severity == Severity.Error }
}

/** The final prompt payload. */
data class BuiltPrompt(val system: String, val userMessage: String, val attachedChips: Set<ContextChip>)

/**
 * Assembles the system prompt and context blocks, bounded by character budgets so that a huge
 * source file can't blow the model's context or the user's token bill.
 */
object AiContextBuilder {

    private const val FILE_BUDGET = 60_000
    private const val GRADLE_BUDGET = 12_000
    private const val TREE_BUDGET = 6_000
    private const val ERROR_WINDOW = 6

    val systemPrompt: String = """
        You are Codein AI, an expert Android engineer embedded in the Codein on-device IDE.
        The project builds with an on-device toolchain (AAPT2, ECJ Java 8 or kotlinc, D8, apksig),
        not with Gradle itself, so prefer fixes that do not depend on Gradle plugins or annotation processors.

        When you change an existing file, reply with a unified diff in a ```diff fenced block:
        include `--- a/<path>` and `+++ b/<path>` headers with the project-relative path, `@@` hunk headers,
        and at least two unchanged context lines around every change. Never abbreviate code with comments
        such as "rest unchanged". When you write new code that is not a patch, use a fenced block with the
        language tag. Keep explanations short and specific to the attached context.
    """.trimIndent()

    fun build(question: String, snapshot: AiContextSnapshot, chips: Set<ContextChip>): BuiltPrompt {
        val attached = mutableSetOf<ContextChip>()
        val sb = StringBuilder()
        if (snapshot.projectName.isNotBlank()) sb.append("Project: ").append(snapshot.projectName).append("\n\n")

        if (ContextChip.FileTree in chips && snapshot.fileTree.isNotEmpty()) {
            attached += ContextChip.FileTree
            sb.append("<file_tree>\n").append(budget(snapshot.fileTree.joinToString("\n"), TREE_BUDGET)).append("\n</file_tree>\n\n")
        }
        val path = snapshot.currentFilePath
        if (ContextChip.CurrentFile in chips && path != null && snapshot.currentFileText != null) {
            attached += ContextChip.CurrentFile
            sb.append("<current_file path=\"").append(path).append("\" cursor_line=\"").append(snapshot.cursorLine + 1).append("\">\n")
            sb.append(budgetAroundLine(snapshot.currentFileText, snapshot.cursorLine, FILE_BUDGET))
            sb.append("\n</current_file>\n\n")
        }
        if (ContextChip.Selection in chips && snapshot.selectionText.isNotBlank()) {
            attached += ContextChip.Selection
            sb.append("<selection").append(path?.let { " path=\"$it\"" }.orEmpty()).append(">\n")
            sb.append(budget(snapshot.selectionText, FILE_BUDGET / 2)).append("\n</selection>\n\n")
        }
        if (ContextChip.BuildError in chips && snapshot.diagnostics.isNotEmpty()) {
            attached += ContextChip.BuildError
            sb.append("<build_diagnostics>\n").append(renderDiagnostics(snapshot)).append("</build_diagnostics>\n\n")
        }
        if (ContextChip.Gradle in chips && snapshot.gradleFiles.isNotEmpty()) {
            attached += ContextChip.Gradle
            var remaining = GRADLE_BUDGET
            snapshot.gradleFiles.forEach { (name, text) ->
                if (remaining <= 0) return@forEach
                val chunk = budget(text, remaining)
                remaining -= chunk.length
                sb.append("<gradle_file path=\"").append(name).append("\">\n").append(chunk).append("\n</gradle_file>\n\n")
            }
        }
        sb.append(question.trim())
        return BuiltPrompt(systemPrompt, sb.toString(), attached)
    }

    /** The canned request used by the "AI Fix" key and the build console's "Explain & Fix" button. */
    fun explainAndFixRequest(snapshot: AiContextSnapshot): String {
        val first = snapshot.diagnostics.firstOrNull { it.severity == Severity.Error }
        val where = first?.let { d -> d.file?.let { File(it).name + (d.line?.let { l -> ":$l" } ?: "") } } ?: "the build"
        return "The build failed at $where. Explain the root cause in two or three sentences, then give the minimal fix " +
            "as a unified diff for each affected file."
    }

    private fun renderDiagnostics(snapshot: AiContextSnapshot): String {
        val sb = StringBuilder()
        val root = snapshot.projectRoot
        snapshot.diagnostics.sortedBy { it.severity.ordinal }.take(25).forEach { d ->
            sb.append(d.render()).append('\n')
            val file = d.file?.let { File(it) }?.let { if (it.isAbsolute || root == null) it else File(root, it.path) }
            val line = d.line
            if (file != null && line != null && file.isFile && file.length() < 2_000_000) {
                val lines = runCatching { file.readLines() }.getOrNull() ?: return@forEach
                val from = (line - 1 - ERROR_WINDOW).coerceAtLeast(0)
                val to = (line - 1 + ERROR_WINDOW).coerceAtMost(lines.lastIndex)
                val rel = root?.let { r -> runCatching { file.relativeTo(r).invariantSeparatorsPath }.getOrNull() } ?: file.name
                sb.append("  context from ").append(rel).append(":\n")
                for (k in from..to) {
                    sb.append(if (k == line - 1) "  > " else "    ").append(k + 1).append(" | ").append(lines[k]).append('\n')
                }
            } else if (!d.snippet.isNullOrBlank()) {
                sb.append("  ").append(d.snippet).append('\n')
            }
        }
        if (snapshot.diagnostics.size > 25) sb.append("… ").append(snapshot.diagnostics.size - 25).append(" more diagnostics omitted\n")
        return sb.toString()
    }

    private fun budget(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max) + "\n… [truncated ${text.length - max} characters]"

    /** Keeps the region around the cursor when a file exceeds the budget. */
    private fun budgetAroundLine(text: String, cursorLine: Int, max: Int): String {
        if (text.length <= max) return text
        val lines = text.split('\n')
        var from = cursorLine.coerceIn(0, lines.lastIndex)
        var to = from
        var size = lines[from].length
        while (size < max && (from > 0 || to < lines.lastIndex)) {
            if (from > 0) { from--; size += lines[from].length + 1 }
            if (to < lines.lastIndex && size < max) { to++; size += lines[to].length + 1 }
        }
        val head = if (from > 0) "… [lines 1-$from omitted]\n" else ""
        val tail = if (to < lines.lastIndex) "\n… [lines ${to + 2}-${lines.size} omitted]" else ""
        return head + lines.subList(from, to + 1).joinToString("\n") + tail
    }
}
