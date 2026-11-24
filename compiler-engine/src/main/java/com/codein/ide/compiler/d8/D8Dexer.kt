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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.Properties
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

    private companion object {
        /** Upper bound on concurrent D8 runs; each one holds a jar's classes in memory. */
        const val MAX_PARALLEL_DEX = 4

        /** How many distinct merged library dex sets (one per dependency combination) to keep. */
        const val MAX_CACHED_LIBRARY_SETS = 4
    }

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
        val diagnostics: MutableList<CompilerDiagnostic> = Collections.synchronizedList(mutableListOf())
        val mode = if (release) CompilationMode.RELEASE else CompilationMode.DEBUG
        val modeTag = if (release) "rel" else "dbg"
        cacheDir.mkdirs()
        val jars = libraryJars.filter { it.isFile }
        val hashes = HashCache(File(cacheDir, "hashes.properties"))
        // Desugaring default/static interface methods needs the classpath only below API 24; above
        // that each jar can be dexed on its own, which is much faster and lets jars run in parallel.
        val needsClasspath = minSdk < 24

        // 1. Pre-dex each library jar (in parallel) into a cache keyed by content hash + minSdk + mode.
        val archives = jars.associateWith { jar -> File(cacheDir, "${jar.nameWithoutExtension}-${hashes.sha1(jar)}-api$minSdk-$modeTag.zip") }
        hashes.save()
        val missing = archives.filterValues { !it.isFile }
        if (missing.isNotEmpty()) {
            onLog("Pre-dexing ${missing.size} of ${jars.size} library jar(s) (${archives.size - missing.size} cached)")
            val parallelism = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_PARALLEL_DEX)
            val gate = Semaphore(parallelism)
            val results = coroutineScope {
                missing.map { (jar, cached) ->
                    async {
                        gate.withPermit {
                            ensureActive()
                            val handler = CollectingHandler(diagnostics)
                            val temp = File(cacheDir, "tmp-${jar.nameWithoutExtension}-${System.nanoTime()}.zip")
                            val ok = runD8(handler) {
                                D8Command.builder(handler)
                                    .addProgramFiles(jar.toPath())
                                    .addLibraryFiles(androidJar.toPath())
                                    .apply { if (needsClasspath) addClasspathFiles(jars.filter { it != jar }.map { it.toPath() }) }
                                    .setMinApiLevel(minSdk)
                                    .setMode(mode)
                                    .setIntermediate(true)
                                    .setOutput(temp.toPath(), OutputMode.DexIndexed)
                                    .build()
                            }
                            if (ok) {
                                if (!temp.renameTo(cached)) { temp.copyTo(cached, overwrite = true); temp.delete() }
                            } else {
                                temp.delete()
                            }
                            ok
                        }
                    }
                }.awaitAll()
            }
            if (results.any { !it }) return@withContext DexResult(false, emptyList(), diagnostics.toList())
        }
        val libraryDexArchives = jars.map { archives.getValue(it) }

        // 2. Dex application classes (desugared against libraries on the classpath). Skipped when
        //    the class files are byte-identical to the previous build.
        val classFiles = classesDirs.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() }
            .sortedBy { it.path }
        val appCache = File(cacheDir, "app-" + sha1Of(outputDir.absolutePath)).apply { mkdirs() }
        val appDexArchive = File(appCache, "classes-api$minSdk-$modeTag.zip")
        val appStamp = File(appCache, "classes-api$minSdk-$modeTag.stamp")
        val appKey = contentKey(classFiles, classesDirs)
        if (classFiles.isEmpty()) {
            appDexArchive.delete()
        } else if (appDexArchive.isFile && appStamp.isFile && appStamp.readText() == appKey) {
            onLog("Application classes unchanged, reusing dex")
        } else {
            onLog("Dexing ${classFiles.size} application classes")
            appStamp.delete()
            appDexArchive.delete()
            val handler = CollectingHandler(diagnostics)
            val ok = runD8(handler) {
                D8Command.builder(handler)
                    .addProgramFiles(classFiles.map { it.toPath() })
                    .addLibraryFiles(androidJar.toPath())
                    .addClasspathFiles(jars.map { it.toPath() })
                    .setMinApiLevel(minSdk)
                    .setMode(mode)
                    .setIntermediate(true)
                    .setOutput(appDexArchive.toPath(), OutputMode.DexIndexed)
                    .build()
            }
            if (!ok) return@withContext DexResult(false, emptyList(), diagnostics.toList())
            appStamp.writeText(appKey)
        }

        // 3. Produce the final dex files. Debug builds on API 21+ (native multidex) keep libraries
        //    and app in separate dex files, like AGP's per-scope merging: the merged library dex is
        //    cached, so an edit only re-merges the app's own classes. Release builds merge everything
        //    into the fewest dex files.
        if (libraryDexArchives.isEmpty() && !appDexArchive.isFile) {
            diagnostics += CompilerDiagnostic(DiagnosticSource.D8, Severity.Error, "Nothing to dex: no compiled classes were produced.")
            return@withContext DexResult(false, emptyList(), diagnostics.toList())
        }
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        val separateScopes = !release && minSdk >= 21
        val groups: List<File> = if (separateScopes) {
            buildList {
                if (appDexArchive.isFile) {
                    val appOut = File(appCache, "merged-api$minSdk-$modeTag")
                    val appMergeStamp = File(appOut, ".stamp")
                    if (!(appMergeStamp.isFile && appMergeStamp.readText() == appKey)) {
                        onLog("Merging application dex")
                        if (!merge(listOf(appDexArchive), appOut, minSdk, mode, diagnostics)) {
                            return@withContext DexResult(false, emptyList(), diagnostics.toList())
                        }
                        appMergeStamp.writeText(appKey)
                    }
                    add(appOut)
                }
                if (libraryDexArchives.isNotEmpty()) {
                    val libKey = sha1Of(libraryDexArchives.joinToString("|") { it.name })
                    val libOut = File(cacheDir, "libs-merged-$libKey")
                    if (!File(libOut, ".stamp").isFile) {
                        onLog("Merging ${libraryDexArchives.size} library dex archive(s) (cached for later builds)")
                        if (!merge(libraryDexArchives, libOut, minSdk, mode, diagnostics)) {
                            return@withContext DexResult(false, emptyList(), diagnostics.toList())
                        }
                        File(libOut, ".stamp").writeText(libKey)
                        pruneMergedLibraries(cacheDir, keep = libOut)
                    } else {
                        libOut.setLastModified(System.currentTimeMillis())
                        onLog("Reusing merged dex of ${libraryDexArchives.size} unchanged libraries")
                    }
                    add(libOut)
                }
            }
        } else {
            val allOut = File(cacheDir, "all-merged-tmp-${System.nanoTime()}")
            val mergeInputs = buildList {
                if (appDexArchive.isFile) add(appDexArchive)
                addAll(libraryDexArchives)
            }
            onLog("Merging ${mergeInputs.size} dex archive(s)")
            val ok = merge(mergeInputs, allOut, minSdk, mode, diagnostics)
            if (!ok) {
                allOut.deleteRecursively()
                return@withContext DexResult(false, emptyList(), diagnostics.toList())
            }
            listOf(allOut)
        }

        // Number the dex files of every group consecutively: classes.dex, classes2.dex, ...
        var index = 1
        val dexFiles = mutableListOf<File>()
        for (group in groups) {
            val files = group.listFiles { f -> f.isFile && f.extension == "dex" }.orEmpty()
                .sortedWith(compareBy({ it.name.length }, { it.name }))
            for (dex in files) {
                val target = File(outputDir, if (index == 1) "classes.dex" else "classes$index.dex")
                dex.copyTo(target, overwrite = true)
                dexFiles += target
                index++
            }
            if (group.name.startsWith("all-merged-tmp-")) group.deleteRecursively()
        }
        DexResult(dexFiles.isNotEmpty(), dexFiles, diagnostics.toList())
    }

    private fun merge(inputs: List<File>, outDir: File, minSdk: Int, mode: CompilationMode, diagnostics: MutableList<CompilerDiagnostic>): Boolean {
        outDir.deleteRecursively()
        outDir.mkdirs()
        val handler = CollectingHandler(diagnostics)
        val ok = runD8(handler) {
            D8Command.builder(handler)
                .addProgramFiles(inputs.map { it.toPath() })
                .setMinApiLevel(minSdk)
                .setMode(mode)
                .setOutput(outDir.toPath(), OutputMode.DexIndexed)
                .build()
        }
        return ok && outDir.listFiles { f -> f.extension == "dex" }.orEmpty().isNotEmpty()
    }

    /** Keeps only the few most recently used merged-library dex sets so the cache cannot grow forever. */
    private fun pruneMergedLibraries(cacheDir: File, keep: File) {
        cacheDir.listFiles { f -> f.isDirectory && f.name.startsWith("libs-merged-") && f != keep }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(MAX_CACHED_LIBRARY_SETS - 1)
            .forEach { it.deleteRecursively() }
    }

    private fun sha1Of(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    /** Hash of the relative paths and bytes of [files], used to detect unchanged class output. */
    private fun contentKey(files: List<File>, roots: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-1")
        for (file in files) {
            val root = roots.firstOrNull { file.path.startsWith(it.path) }
            digest.update((root?.let { file.relativeTo(it).path } ?: file.path).toByteArray())
            digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Remembers each jar's SHA-1 keyed by path, size and modification time, so unchanged
     * dependency jars are not re-hashed on every build.
     */
    private inner class HashCache(private val file: File) {
        private val props = Properties().apply { if (file.isFile) runCatching { file.inputStream().use { load(it) } } }
        private var dirty = false

        fun sha1(jar: File): String {
            val key = "${jar.absolutePath}|${jar.length()}|${jar.lastModified()}"
            props.getProperty(key)?.let { return it }
            val hash = this@D8Dexer.sha1(jar)
            synchronized(props) { props.setProperty(key, hash) }
            dirty = true
            return hash
        }

        fun save() {
            if (!dirty) return
            runCatching { file.outputStream().use { props.store(it, null) } }
        }
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
        if (ok) {
            runCatching { mergeResources(output, jars) }.onFailure {
                diagnostics += CompilerDiagnostic(DiagnosticSource.D8, Severity.Error, "Failed to copy jar resources: ${it.message}")
            }
        }
        DexResult(ok && diagnostics.none { it.severity == Severity.Error }, listOf(output), diagnostics)
    }

    /**
     * D8's archive output only contains `classes*.dex`. Tools loaded through a DexClassLoader (the
     * Kotlin compiler in particular) also need their non-class resources: `META-INF/services`,
     * extension XML descriptors and message bundles. This copies them in, first occurrence wins.
     */
    private fun mergeResources(archive: File, jars: List<File>) {
        val merged = File(archive.parentFile, archive.name + ".merge")
        val seen = HashSet<String>()
        java.util.zip.ZipOutputStream(merged.outputStream().buffered()).use { out ->
            java.util.zip.ZipFile(archive).use { dex ->
                for (entry in dex.entries()) {
                    if (entry.isDirectory || !seen.add(entry.name)) continue
                    out.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    dex.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            for (jar in jars) {
                java.util.zip.ZipFile(jar).use { zip ->
                    for (entry in zip.entries()) {
                        val name = entry.name
                        if (entry.isDirectory || name.endsWith(".class") || isSignatureFile(name) || !seen.add(name)) continue
                        out.putNextEntry(java.util.zip.ZipEntry(name))
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }
        }
        if (archive.exists()) archive.delete()
        check(merged.renameTo(archive)) { "Unable to replace ${archive.name}" }
    }

    private fun isSignatureFile(name: String): Boolean {
        val upper = name.uppercase()
        return upper == "META-INF/MANIFEST.MF" ||
            (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")))
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
