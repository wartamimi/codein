package com.codein.ide.compiler.aapt2

import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.DiagnosticSource
import com.codein.ide.compiler.model.Severity
import com.codein.ide.compiler.process.ProcessRunner
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class Aapt2Result(val success: Boolean, val diagnostics: List<CompilerDiagnostic>, val output: List<String>)

/** Options for `aapt2 link`. */
data class Aapt2LinkOptions(
    val manifest: File,
    val androidJar: File,
    val compiledResources: List<File>,
    val outputApk: File,
    val javaOutputDir: File,
    val textSymbolsFile: File,
    val proguardRulesFile: File?,
    val minSdk: Int,
    val targetSdk: Int,
    val versionCode: Int,
    val versionName: String,
    /** Package of the generated R class (the module namespace). */
    val packageName: String,
    /** Package written into the linked manifest (the application id, including any suffix). */
    val manifestPackage: String? = null,
    val extraPackages: List<String> = emptyList(),
    val assetsDirs: List<File> = emptyList(),
    val protoFormat: Boolean = false,
    val debuggable: Boolean = false,
)

/**
 * Drives the `aapt2` binary that ships as `libaapt2.so` in the APK's native library directory.
 * Every invocation is parsed into structured [CompilerDiagnostic]s.
 */
@Singleton
class Aapt2Runner @Inject constructor() {

    /** Compiles an entire `res/` directory into a zip of `.flat` files. */
    suspend fun compileDirectory(
        aapt2: File,
        resDir: File,
        outputZip: File,
        onLine: (String) -> Unit = {},
    ): Aapt2Result {
        outputZip.parentFile?.mkdirs()
        if (outputZip.exists()) outputZip.delete()
        val command = listOf(aapt2.absolutePath, "compile", "--dir", resDir.absolutePath, "-o", outputZip.absolutePath, "-v")
        return execute(command, onLine)
    }

    /** Compiles a single resource file into [outputDir]; used for incremental rebuilds. */
    suspend fun compileFile(aapt2: File, resFile: File, outputDir: File, onLine: (String) -> Unit = {}): Aapt2Result {
        outputDir.mkdirs()
        val command = listOf(aapt2.absolutePath, "compile", resFile.absolutePath, "-o", outputDir.absolutePath)
        return execute(command, onLine)
    }

    suspend fun link(aapt2: File, options: Aapt2LinkOptions, onLine: (String) -> Unit = {}): Aapt2Result {
        options.outputApk.parentFile?.mkdirs()
        options.javaOutputDir.mkdirs()
        options.textSymbolsFile.parentFile?.mkdirs()
        val command = buildList {
            add(aapt2.absolutePath)
            add("link")
            add("-o"); add(options.outputApk.absolutePath)
            add("-I"); add(options.androidJar.absolutePath)
            add("--manifest"); add(options.manifest.absolutePath)
            add("--java"); add(options.javaOutputDir.absolutePath)
            add("--output-text-symbols"); add(options.textSymbolsFile.absolutePath)
            add("--min-sdk-version"); add(options.minSdk.toString())
            add("--target-sdk-version"); add(options.targetSdk.toString())
            add("--version-code"); add(options.versionCode.toString())
            add("--version-name"); add(options.versionName)
            add("--rename-manifest-package"); add(options.manifestPackage ?: options.packageName)
            add("--custom-package"); add(options.packageName)
            add("--auto-add-overlay")
            add("--no-version-vectors")
            if (options.debuggable) add("--debug-mode")
            if (options.protoFormat) add("--proto-format")
            if (options.extraPackages.isNotEmpty()) {
                add("--extra-packages"); add(options.extraPackages.distinct().joinToString(":"))
            }
            options.proguardRulesFile?.let { add("--proguard"); add(it.absolutePath) }
            options.assetsDirs.filter { it.isDirectory }.forEach { add("-A"); add(it.absolutePath) }
            options.compiledResources.filter { it.exists() }.forEach { add("-R"); add(it.absolutePath) }
            add("-v")
        }
        return execute(command, onLine)
    }

    /** Converts a binary-format APK's resources to proto format (used for App Bundles). */
    suspend fun convertToProto(aapt2: File, inputApk: File, outputApk: File, onLine: (String) -> Unit = {}): Aapt2Result {
        val command = listOf(aapt2.absolutePath, "convert", "--output-format", "proto", "-o", outputApk.absolutePath, inputApk.absolutePath)
        return execute(command, onLine)
    }

    suspend fun version(aapt2: File): String? =
        runCatching { ProcessRunner.run(listOf(aapt2.absolutePath, "version")).allOutput.firstOrNull() }.getOrNull()

    private suspend fun execute(command: List<String>, onLine: (String) -> Unit): Aapt2Result {
        val result = ProcessRunner.run(command) { _, line -> onLine(line) }
        val diagnostics = result.allOutput.mapNotNull(::parseLine)
        val success = result.isSuccess && diagnostics.none { it.severity == Severity.Error }
        val finalDiagnostics = if (!result.isSuccess && diagnostics.none { it.severity == Severity.Error }) {
            diagnostics + CompilerDiagnostic(
                source = DiagnosticSource.Aapt2,
                severity = Severity.Error,
                message = "aapt2 ${command.getOrNull(1)} exited with code ${result.exitCode}: " +
                    result.allOutput.takeLast(5).joinToString(" | "),
            )
        } else diagnostics
        return Aapt2Result(success, finalDiagnostics, result.allOutput)
    }

    companion object {
        private val LOCATED = Regex("""^(.+?):(\d+)(?::(\d+))?:\s*(error|warn|warning|note):\s*(.*)$""", RegexOption.IGNORE_CASE)
        private val BARE = Regex("""^(?:(.+?):\s*)?(error|warning|warn):\s*(.*)$""", RegexOption.IGNORE_CASE)

        /** Parses one line of aapt2 output; returns null for informational/verbose lines. */
        fun parseLine(line: String): CompilerDiagnostic? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            LOCATED.find(trimmed)?.let { m ->
                return CompilerDiagnostic(
                    source = DiagnosticSource.Aapt2,
                    severity = severityOf(m.groupValues[4]),
                    message = m.groupValues[5],
                    file = m.groupValues[1],
                    line = m.groupValues[2].toIntOrNull(),
                    column = m.groupValues[3].toIntOrNull(),
                )
            }
            BARE.find(trimmed)?.let { m ->
                val file = m.groupValues[1].takeIf { it.isNotBlank() && (it.contains('/') || it.contains('.')) }
                return CompilerDiagnostic(
                    source = DiagnosticSource.Aapt2,
                    severity = severityOf(m.groupValues[2]),
                    message = m.groupValues[3],
                    file = file,
                )
            }
            return null
        }

        private fun severityOf(word: String): Severity = when (word.lowercase()) {
            "error" -> Severity.Error
            "warn", "warning" -> Severity.Warning
            else -> Severity.Info
        }
    }
}
