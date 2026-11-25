package com.codein.ide.ai.template

/** A project produced by the model: files to write over the base template and extra dependencies. */
data class GeneratedProject(
    /** Relative path (always under `app/src/main/`) to full file content. */
    val files: Map<String, String>,
    /** Gradle dependency notations such as `androidx.recyclerview:recyclerview:1.3.2`. */
    val dependencies: List<String>,
    /** Short explanation from the model, shown to the user. */
    val summary: String,
    /** Blocks that were dropped (unsafe path, bad dependency), with the reason. */
    val rejected: List<String>,
)

/**
 * Prompt and response format for "Template from AI".
 *
 * Codein first generates a known-good base project (Gradle files, theme, launcher icons) from its
 * built-in templates, so the result always has a build setup Codein can compile. The model only
 * writes app sources, resources and the manifest, plus an optional list of extra dependencies.
 *
 * The response uses plain-text markers instead of JSON so that source code never has to be escaped:
 *
 * ```
 * <<<SUMMARY>>>
 * One or two sentences.
 * <<<END>>>
 * <<<FILE app/src/main/kotlin/com/example/app/MainActivity.kt>>>
 * ...full file...
 * <<<END>>>
 * <<<DEPENDENCIES>>>
 * androidx.recyclerview:recyclerview:1.3.2
 * <<<END>>>
 * ```
 */
object AiProjectFormat {

    private const val END = "<<<END>>>"
    private val fileHeader = Regex("""^<<<FILE\s+(.+?)>>>\s*$""")
    private val dependency = Regex("""^[A-Za-z0-9_.\-]+:[A-Za-z0-9_.\-]+:[A-Za-z0-9_.\-+]+$""")
    private val allowedRoots = listOf("app/src/main/java/", "app/src/main/kotlin/", "app/src/main/res/")
    private const val MANIFEST = "app/src/main/AndroidManifest.xml"
    private const val MAX_FILES = 40
    private const val MAX_FILE_CHARS = 200_000

    /**
     * Builds the system prompt. [baseFiles] are the files of the base template the model should
     * edit (sources, layouts, values, manifest); [dependencies] are the ones already declared.
     */
    fun systemPrompt(
        appName: String,
        packageName: String,
        language: String,
        compose: Boolean,
        minSdk: Int,
        baseFiles: Map<String, String>,
        dependencies: List<String>,
    ): String = buildString {
        appendLine("You generate Android app projects for Codein, an IDE that compiles projects on the phone itself.")
        appendLine()
        appendLine("Project: \"$appName\", package $packageName, language $language, UI toolkit ${if (compose) "Jetpack Compose (Material 3)" else "Android Views (AppCompat + Material Components)"}, minSdk $minSdk.")
        appendLine("Codein already created the Gradle build, launcher icons and theme. You write the app itself.")
        appendLine()
        appendLine("Rules:")
        appendLine("- Only write files under app/src/main/java/, app/src/main/kotlin/, app/src/main/res/ or app/src/main/AndroidManifest.xml.")
        appendLine("- Keep the package $packageName for all classes (sub-packages are fine) and keep MainActivity as the launcher activity.")
        if (language == "Kotlin") appendLine("- Kotlin sources go under app/src/main/kotlin/.") else appendLine("- Java sources go under app/src/main/java/ and must compile at the Java 8 language level (lambdas are fine, no records, no var).")
        appendLine("- Every file must be complete and compile as written. No placeholders, no TODOs, no \"...\".")
        appendLine("- Reference only resources you define or that exist in the base files below.")
        appendLine("- Prefer the dependencies already declared. Add others only when needed, as group:artifact:version from Google Maven or Maven Central, with real, current versions.")
        appendLine("- No network access permissions or code unless the description asks for it. No annotation processors (kapt/KSP) and no Gradle plugins: they are not available.")
        appendLine("- Keep the app small: at most $MAX_FILES files.")
        appendLine()
        appendLine("Answer with exactly these blocks and nothing outside them:")
        appendLine("<<<SUMMARY>>>")
        appendLine("one or two sentences describing what you built")
        appendLine(END)
        appendLine("<<<FILE relative/path/from/project/root>>>")
        appendLine("full file content")
        appendLine(END)
        appendLine("(repeat FILE blocks as needed)")
        appendLine("<<<DEPENDENCIES>>>")
        appendLine("one group:artifact:version per line, or empty")
        appendLine(END)
        appendLine()
        appendLine("Dependencies already declared:")
        dependencies.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Base files you may replace (other files stay as they are):")
        baseFiles.forEach { (path, content) ->
            appendLine("<<<FILE $path>>>")
            appendLine(content.trimEnd())
            appendLine(END)
        }
    }

    /** Parses a model response. Never throws; unusable blocks end up in [GeneratedProject.rejected]. */
    fun parse(response: String): GeneratedProject {
        val files = LinkedHashMap<String, String>()
        val dependencies = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        var summary = ""
        val lines = response.replace("\r\n", "\n").lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val header = fileHeader.matchEntire(line)
            when {
                line == "<<<SUMMARY>>>" -> {
                    val (body, next) = readBlock(lines, i + 1)
                    summary = body.trim()
                    i = next
                }
                line == "<<<DEPENDENCIES>>>" -> {
                    val (body, next) = readBlock(lines, i + 1)
                    body.lines().map { it.trim().removePrefix("-").trim().removePrefix("implementation").trim().trim('(', ')', '"', '\'') }
                        .filter { it.isNotEmpty() }
                        .forEach { d -> if (dependency.matches(d)) dependencies += d else rejected += "Dependency \"$d\": not a group:artifact:version" }
                    i = next
                }
                header != null -> {
                    val path = header.groupValues[1].trim().trim('`', '"').removePrefix("./")
                    val (body, next) = readBlock(lines, i + 1)
                    val reason = pathProblem(path) ?: when {
                        body.length > MAX_FILE_CHARS -> "file is too large"
                        files.size >= MAX_FILES -> "more than $MAX_FILES files"
                        body.isBlank() -> "file is empty"
                        else -> null
                    }
                    if (reason == null) files[path] = stripFence(body).trimEnd() + "\n" else rejected += "$path: $reason"
                    i = next
                }
                else -> i++
            }
        }
        return GeneratedProject(files, dependencies.distinct(), summary, rejected)
    }

    /** Returns why [path] may not be written, or null when it is allowed. */
    fun pathProblem(path: String): String? {
        if (path.isEmpty()) return "empty path"
        if (path.startsWith("/") || path.contains('\\') || path.contains(':')) return "path must be relative"
        if (path.split('/').any { it == ".." || it == "." || it.isEmpty() }) return "path may not contain '..' or empty segments"
        if (path == MANIFEST) return null
        if (allowedRoots.none { path.startsWith(it) }) return "only app/src/main/java, kotlin, res and the manifest can be written"
        return null
    }

    private fun readBlock(lines: List<String>, start: Int): Pair<String, Int> {
        val body = StringBuilder()
        var i = start
        while (i < lines.size && lines[i].trim() != END) {
            // A new block header without END: close the current block there.
            val t = lines[i].trim()
            if (t == "<<<SUMMARY>>>" || t == "<<<DEPENDENCIES>>>" || fileHeader.matches(t)) return body.toString() to i
            body.append(lines[i]).append('\n')
            i++
        }
        return body.toString() to (i + 1)
    }

    /** Removes a Markdown code fence wrapped around a whole file, which models sometimes add. */
    private fun stripFence(body: String): String {
        val trimmed = body.trim('\n')
        val lines = trimmed.lines()
        if (lines.size >= 2 && lines.first().trim().startsWith("```") && lines.last().trim() == "```") {
            return lines.subList(1, lines.size - 1).joinToString("\n")
        }
        return trimmed
    }
}
