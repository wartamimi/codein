package com.codein.ide.compiler.model

import java.io.File

/** Which tool produced a diagnostic. */
enum class DiagnosticSource { Gradle, Maven, Aapt2, Ecj, Kotlin, D8, Packager, Signer, Bundletool }

enum class Severity { Error, Warning, Info }

/** A structured compiler/build diagnostic that the UI can jump to (file, line, column). */
data class CompilerDiagnostic(
    val source: DiagnosticSource,
    val severity: Severity,
    val message: String,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val endColumn: Int? = null,
    val snippet: String? = null,
) {
    val location: String
        get() = buildString {
            if (file != null) {
                append(File(file).name)
                if (line != null) {
                    append(':').append(line)
                    if (column != null) append(':').append(column)
                }
            }
        }

    /** Compact single-line rendering used for logs and AI context injection. */
    fun render(): String = buildString {
        append(severity.name.lowercase()).append(" [").append(source.name).append("] ")
        if (file != null) {
            append(file)
            if (line != null) append(':').append(line)
            if (column != null) append(':').append(column)
            append(": ")
        }
        append(message)
    }
}

/** The three build variants offered in the Top Studio Bar. */
enum class BuildVariant(val displayName: String, val isRelease: Boolean, val producesBundle: Boolean) {
    DebugApk("Debug APK", isRelease = false, producesBundle = false),
    ReleaseApk("Release APK", isRelease = true, producesBundle = false),
    PlayStoreAab("Play Store AAB", isRelease = true, producesBundle = true),
}

/** The five visual pipeline steps shown in the Build Pipeline tab. */
enum class BuildStep(val index: Int, val title: String) {
    MavenResolve(1, "Maven Resolve"),
    Aapt2Resources(2, "AAPT2 Resources"),
    Compile(3, "ECJ/Kotlin Compile"),
    Dexing(4, "D8 Dexing"),
    SignAndPackage(5, "Sign & Package"),
}

enum class StepStatus { Pending, Running, Succeeded, Failed, Skipped, Cancelled }

enum class LogLevel { Verbose, Info, Warning, Error }

/** Events streamed by the [com.codein.ide.compiler.BuildOrchestrator]. */
sealed interface BuildEvent {
    val timestamp: Long

    data class Started(val variant: BuildVariant, val projectName: String, override val timestamp: Long = System.currentTimeMillis()) : BuildEvent
    data class StepStarted(val step: BuildStep, override val timestamp: Long = System.currentTimeMillis()) : BuildEvent
    data class StepFinished(
        val step: BuildStep,
        val status: StepStatus,
        val durationMillis: Long,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : BuildEvent
    data class Log(val step: BuildStep?, val level: LogLevel, val message: String, override val timestamp: Long = System.currentTimeMillis()) : BuildEvent
    data class Diagnostic(val step: BuildStep, val diagnostic: CompilerDiagnostic, override val timestamp: Long = System.currentTimeMillis()) : BuildEvent
    data class Progress(val fraction: Float, override val timestamp: Long = System.currentTimeMillis()) : BuildEvent
    data class Finished(val result: BuildResult, override val timestamp: Long = System.currentTimeMillis()) : BuildEvent
}

sealed interface BuildResult {
    val durationMillis: Long

    data class Success(val output: File, val variant: BuildVariant, override val durationMillis: Long) : BuildResult
    data class Failure(val failedStep: BuildStep, val diagnostics: List<CompilerDiagnostic>, override val durationMillis: Long) : BuildResult
    data class Cancelled(override val durationMillis: Long) : BuildResult
}

/** Thrown from a pipeline step to abort the build with structured diagnostics. */
class BuildStepException(
    val step: BuildStep,
    val diagnostics: List<CompilerDiagnostic>,
    message: String = diagnostics.firstOrNull()?.message ?: "Build step ${step.title} failed",
    cause: Throwable? = null,
) : Exception(message, cause)
