package com.codein.ide.compiler.d8

import com.android.tools.r8.CompilationFailedException
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import com.android.tools.r8.position.TextPosition
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.DiagnosticSource
import com.codein.ide.compiler.model.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DexResult(val success: Boolean, val dexFiles: List<File>, val diagnostics: List<CompilerDiagnostic>)

/**
 * Converts JVM bytecode to DEX with Google's D8 programmatic API, including Java 8+ language
 * desugaring. Library jars are pre-dexed once and cached by content hash, then merged with the
 * application's classes into (multi)dex output.
 */
@Singleton
class D8Dexer @Inject constructor() {

    /** Dexes the app's compiled classes and merges them with cached pre-dexed libraries. */
    suspend fun dexApplication(
        classesDirs: List<File>,
        libraryJars: List<File>,
        androidJar: File,
        minSdk: Int,
        release: Boolean,
        cacheDir: File,
        outputDir: File,
        onLog: (String) -> Unit = {},
    ): DexResult = withContext(Dispatchers.Default) {
        val diagnostics = mutableListOf<CompilerDiagnostic>()
        val handler = CollectingHandler(diagnostics)
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        // 1. Pre-dex each library jar into a cache keyed by content hash + minSdk + mode.
        val libraryDexArchives = mutableListOf<File>()
        for (jar in libraryJars.filter { it.isFile }) {
            val key = sha1(jar) + "-api$minSdk-" + (if (release) "rel" else "dbg")
            val cached = File(cacheDir, "${jar.nameWithoutExtension}-$key.zip")
            if (!cached.isFile) {
                onLog("Pre-dexing ${jar.name}")
                val temp = File(cacheDir, "tmp-${System.nanoTime()}.zip")
                cacheDir.mkdirs()
                val ok = runD8(handler) {
                    D8Command.builder(handler)
                        .addProgramFiles(jar.toPath())
                        .addLibraryFiles(androidJar.toPath())
                        .addClasspathFiles(libraryJars.filter { it != jar && it.isFile }.map { it.toPath() })
                        .setMinApiLevel(minSdk)
                        .setMode(if (release) CompilationMode.RELEASE else CompilationMode.DEBUG)
                        .setIntermediate(true)
                        .setOutput(temp.toPath(), OutputMode.DexIndexed)
                        .build()
                }
                if (!ok) return@withContext DexResult(false, emptyList(), diagnostics)
                temp.renameTo(cached)
            }
            libraryDexArchives += cached
        }

        // 2. Dex application classes (desugared against libraries on the classpath).
        val classFiles = classesDirs.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() }
        val appDexArchive = File(cacheDir, "app-classes.zip")
        appDexArchive.delete()
        if (classFiles.isNotEmpty()) {
            onLog("Dexing ${classFiles.size} application classes")
            val ok = runD8(handler) {
                D8Command.builder(handler)
                    .addProgramFiles(classFiles.map { it.toPath() })
                    .addLibraryFiles(androidJar.toPath())
                    .addClasspathFiles(libraryJars.filter { it.isFile }.map { it.toPath() })
                    .setMinApiLevel(minSdk)
                    .setMode(if (release) CompilationMode.RELEASE else CompilationMode.DEBUG)
                    .setIntermediate(true)
                    .setOutput(appDexArchive.toPath(), OutputMode.DexIndexed)
                    .build()
            }
            if (!ok) return@withContext DexResult(false, emptyList(), diagnostics)
        }

        // 3. Merge everything into final multidex output.
        val mergeInputs = buildList {
            if (appDexArchive.isFile) add(appDexArchive)
            addAll(libraryDexArchives)
        }
        if (mergeInputs.isEmpty()) {
            diagnostics += CompilerDiagnostic(DiagnosticSource.D8, Severity.Error, "Nothing to dex: no compiled classes were produced.")
            return@withContext DexResult(false, emptyList(), diagnostics)
        }
        onLog("Merging ${mergeInputs.size} dex archive(s)")
        val ok = runD8(handler) {
            D8Command.builder(handler)
                .addProgramFiles(mergeInputs.map { it.toPath() })
                .setMinApiLevel(minSdk)
                .setMode(if (release) CompilationMode.RELEASE else CompilationMode.DEBUG)
                .setOutput(outputDir.toPath(), OutputMode.DexIndexed)
                .build()
        }
        val dexFiles = outputDir.listFiles { f -> f.extension == "dex" }.orEmpty().sortedBy { it.name }
        DexResult(ok && dexFiles.isNotEmpty(), dexFiles, diagnostics)
    }

    /** Dexes arbitrary jars into a single dex jar (used to load kotlinc on ART). */
    suspend fun dexJarsToArchive(jars: List<File>, androidJar: File?, minSdk: Int, output: File): DexResult = withContext(Dispatchers.Default) {
        val diagnostics = mutableListOf<CompilerDiagnostic>()
        val handler = CollectingHandler(diagnostics)
        output.parentFile?.mkdirs()
        val ok = runD8(handler) {
            D8Command.builder(handler)
                .addProgramFiles(jars.map { it.toPath() })
                .apply { if (androidJar != null) addLibraryFiles(androidJar.toPath()) }
                .setMinApiLevel(minSdk)
                .setMode(CompilationMode.RELEASE)
                .setOutput(output.toPath(), OutputMode.DexIndexed)
                .build()
        }
        DexResult(ok, listOf(output), diagnostics)
    }

    private inline fun runD8(handler: CollectingHandler, command: () -> D8Command): Boolean = try {
        D8.run(command())
        !handler.hasErrors
    } catch (e: CompilationFailedException) {
        if (!handler.hasErrors) {
            handler.diagnostics += CompilerDiagnostic(DiagnosticSource.D8, Severity.Error, e.message ?: "D8 compilation failed")
        }
        false
    }

    private class CollectingHandler(val diagnostics: MutableList<CompilerDiagnostic>) : DiagnosticsHandler {
        var hasErrors = false
            private set

        override fun error(diagnostic: Diagnostic) {
            hasErrors = true
            diagnostics += convert(diagnostic, Severity.Error)
        }

        override fun warning(diagnostic: Diagnostic) {
            diagnostics += convert(diagnostic, Severity.Warning)
        }

        override fun info(diagnostic: Diagnostic) {
            diagnostics += convert(diagnostic, Severity.Info)
        }

        private fun convert(d: Diagnostic, severity: Severity): CompilerDiagnostic {
            val position = d.position as? TextPosition
            val origin = d.origin?.toString()?.takeIf { it.isNotBlank() && it != "Unknown" }
            return CompilerDiagnostic(
                source = DiagnosticSource.D8,
                severity = severity,
                message = d.diagnosticMessage,
                file = origin,
                line = position?.line?.takeIf { it > 0 },
                column = position?.column?.takeIf { it > 0 },
            )
        }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}
