package com.codein.ide.compiler.kotlin

import com.codein.ide.compiler.d8.D8Dexer
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.DiagnosticSource
import com.codein.ide.compiler.model.Severity
import com.codein.ide.compiler.sdk.BuildToolchain
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import javax.inject.Inject
import javax.inject.Singleton

data class KotlinCompileRequest(
    val sourceFiles: List<File>,
    val javaSourceRoots: List<File>,
    val classpath: List<File>,
    val androidJar: File,
    val outputDir: File,
    val moduleName: String,
    val jvmTarget: String = "1.8",
    val composePluginJar: File? = null,
)

data class KotlinCompileResult(val success: Boolean, val diagnostics: List<CompilerDiagnostic>, val outputDir: File)

/** Pluggable Kotlin compiler runner contract. */
interface KotlinCompilerRunner {
    /** Whether a usable compiler is installed. */
    fun isAvailable(): Boolean

    /** Human readable explanation shown when [isAvailable] is false. */
    fun unavailableReason(): String

    suspend fun compile(request: KotlinCompileRequest, onLog: (String) -> Unit): KotlinCompileResult
}

/**
 * Runs a standalone `kotlin-compiler-embeddable` distribution on ART.
 *
 * JVM jars cannot be class-loaded on Android directly, so the jars found in
 * [BuildToolchain.kotlinCompilerDir] (kotlin-compiler-embeddable, kotlin-stdlib, kotlin-reflect,
 * kotlin-script-runtime, kotlin-daemon-embeddable, trove4j, kotlinx-coroutines-core-jvm, annotations)
 * are dexed once with D8 into a private dex archive and loaded with a [DexClassLoader]. The compiler
 * is then invoked reflectively through `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler.exec`.
 */
@Singleton
class KotlinCompilerHook @Inject constructor(
    private val toolchain: BuildToolchain,
    private val dexer: D8Dexer,
) : KotlinCompilerRunner {

    private val loaderMutex = Mutex()
    private var classLoader: ClassLoader? = null
    private var loadedSignature: String? = null

    private fun compilerJars(): List<File> =
        toolchain.kotlinCompilerDir.listFiles { f -> f.isFile && f.extension == "jar" }.orEmpty().sortedBy { it.name }

    override fun isAvailable(): Boolean = compilerJars().any { it.name.startsWith("kotlin-compiler") }

    override fun unavailableReason(): String =
        "Kotlin sources found but no Kotlin compiler is installed. Import kotlin-compiler-embeddable.jar and " +
            "its runtime jars (kotlin-stdlib, kotlin-reflect, kotlin-script-runtime, kotlin-daemon-embeddable, " +
            "trove4j, kotlinx-coroutines-core-jvm) in Settings > Toolchain > Kotlin compiler."

    private suspend fun loader(onLog: (String) -> Unit): ClassLoader = loaderMutex.withLock {
        val jars = compilerJars()
        val signature = jars.joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" }
        classLoader?.takeIf { loadedSignature == signature }?.let { return it }

        val dexDir = File(toolchain.workRoot, "kotlinc-dex").apply { mkdirs() }
        val dexArchive = File(dexDir, "kotlinc-${signature.hashCode().toUInt()}.jar")
        if (!dexArchive.isFile) {
            onLog("Preparing Kotlin compiler for ART (one-time D8 conversion of ${jars.size} jars)")
            val result = dexer.dexJarsToArchive(jars, toolchain.androidJar(), 26, dexArchive)
            if (!result.success) {
                throw IllegalStateException("Failed to dex the Kotlin compiler: " + result.diagnostics.joinToString { it.message })
            }
        }
        dexArchive.setReadOnly()
        val optimized = File(dexDir, "oat").apply { mkdirs() }
        val loader = DexClassLoader(dexArchive.absolutePath, optimized.absolutePath, null, KotlinCompilerHook::class.java.classLoader)
        classLoader = loader
        loadedSignature = signature
        loader
    }

    override suspend fun compile(request: KotlinCompileRequest, onLog: (String) -> Unit): KotlinCompileResult = withContext(Dispatchers.Default) {
        if (!isAvailable()) {
            return@withContext KotlinCompileResult(
                false,
                listOf(CompilerDiagnostic(DiagnosticSource.Kotlin, Severity.Error, unavailableReason())),
                request.outputDir,
            )
        }
        request.outputDir.mkdirs()
        val stdlib = compilerJars().filter { it.name.startsWith("kotlin-stdlib") }
        val args = buildList {
            add("-no-jdk")
            add("-no-reflect")
            add("-no-stdlib")
            add("-jvm-target"); add(request.jvmTarget)
            add("-module-name"); add(request.moduleName)
            add("-d"); add(request.outputDir.absolutePath)
            add("-classpath")
            add((listOf(request.androidJar) + stdlib + request.classpath).filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath })
            request.composePluginJar?.takeIf { it.isFile }?.let { add("-Xplugin=${it.absolutePath}") }
            add("-Xjava-source-roots=" + request.javaSourceRoots.filter { it.isDirectory }.joinToString(",") { it.absolutePath })
            request.sourceFiles.forEach { add(it.absolutePath) }
        }

        val buffer = ByteArrayOutputStream()
        val stream = PrintStream(buffer, true, "UTF-8")
        val exitCode = try {
            val loader = loader(onLog)
            val compilerClass = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            val compiler = compilerClass.getDeclaredConstructor().newInstance()
            val exec = compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)
            val code = exec.invoke(compiler, stream, args.toTypedArray())
            code?.toString() ?: "INTERNAL_ERROR"
        } catch (t: Throwable) {
            val root = generateSequence(t) { it.cause }.last()
            stream.println("error: Kotlin compiler failed to run on this device: ${root.javaClass.name}: ${root.message}")
            "INTERNAL_ERROR"
        }
        stream.flush()
        val output = buffer.toString("UTF-8").lines()
        output.filter { it.isNotBlank() }.forEach(onLog)
        val diagnostics = output.mapNotNull(::parseLine).toMutableList()
        val success = exitCode == "OK" && diagnostics.none { it.severity == Severity.Error }
        if (!success && diagnostics.none { it.severity == Severity.Error }) {
            diagnostics += CompilerDiagnostic(DiagnosticSource.Kotlin, Severity.Error, "kotlinc exited with $exitCode")
        }
        KotlinCompileResult(success, diagnostics, request.outputDir)
    }

    companion object {
        private val LOCATED = Regex("""^([ewi]):\s+(?:file://)?(.+?):(\d+):(\d+)\s+(.*)$""")
        private val BARE = Regex("""^(error|warning|exception|[ew]):\s*(.*)$""")

        fun parseLine(line: String): CompilerDiagnostic? {
            val t = line.trim()
            LOCATED.find(t)?.let { m ->
                return CompilerDiagnostic(
                    source = DiagnosticSource.Kotlin,
                    severity = when (m.groupValues[1]) { "e" -> Severity.Error; "w" -> Severity.Warning; else -> Severity.Info },
                    message = m.groupValues[5],
                    file = m.groupValues[2],
                    line = m.groupValues[3].toIntOrNull(),
                    column = m.groupValues[4].toIntOrNull(),
                )
            }
            BARE.find(t)?.let { m ->
                val sev = when (m.groupValues[1]) { "error", "e", "exception" -> Severity.Error; else -> Severity.Warning }
                return CompilerDiagnostic(DiagnosticSource.Kotlin, sev, m.groupValues[2])
            }
            return null
        }
    }
}
