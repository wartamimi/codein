package com.codein.ide.compiler.ecj

import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.Severity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks Java sources while the user types, without a build.
 *
 * The last build records its classpath, boot classpath and source roots (including generated
 * `R.java`) in `build/codein-live-check.txt`. A check compiles every Java file of those roots with
 * ECJ, using the editor's unsaved text for the files being edited, and returns only the problems
 * of those files. Before the first build there is no classpath, so nothing is reported.
 */
@Singleton
class LiveJavaChecker @Inject constructor(private val ecj: EcjCompiler) {

    data class Setup(val bootClasspath: File, val classpath: List<File>, val sourceRoots: List<File>)

    suspend fun check(moduleDir: File, edited: Map<File, String>): Map<File, List<CompilerDiagnostic>>? {
        val setup = readSetup(moduleDir) ?: return null
        if (!setup.bootClasspath.isFile) return null
        val work = File(moduleDir, "build/codein-live").apply { deleteRecursively(); mkdirs() }
        val overlays = File(work, "src")
        val out = File(work, "classes")
        val editedCanonical = edited.mapKeys { it.key.canonicalFile }
        val tempToOriginal = HashMap<String, File>()
        val sources = setup.sourceRoots.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        }.map { file ->
            val text = editedCanonical[file.canonicalFile] ?: return@map file
            // ECJ reads sources from disk: unsaved text goes to a copy with the same file name.
            val copy = File(File(overlays, tempToOriginal.size.toString()), file.name).apply { parentFile.mkdirs(); writeText(text) }
            tempToOriginal[copy.canonicalPath] = file
            copy
        }
        if (sources.isEmpty()) return emptyMap()
        val result = ecj.compile(JavaCompileRequest(sources, setup.classpath, setup.bootClasspath, out, debugInfo = false))
        work.deleteRecursively()
        val wanted = editedCanonical.keys
        return result.diagnostics
            .filter { it.severity != Severity.Info }
            .mapNotNull { d ->
                val path = d.file ?: return@mapNotNull null
                val original = tempToOriginal[File(path).canonicalPath] ?: File(path).canonicalFile
                if (original.canonicalFile !in wanted) null else original.canonicalFile to d.copy(file = original.absolutePath)
            }
            .groupBy({ it.first }, { it.second })
            .let { found -> wanted.associateWith { found[it].orEmpty() } }
    }

    companion object {
        private const val FILE = "build/codein-live-check.txt"

        fun writeSetup(moduleDir: File, setup: Setup) {
            val file = File(moduleDir, FILE).apply { parentFile.mkdirs() }
            file.writeText(buildString {
                append("boot\t").append(setup.bootClasspath.absolutePath).append('\n')
                setup.classpath.forEach { append("cp\t").append(it.absolutePath).append('\n') }
                setup.sourceRoots.forEach { append("src\t").append(it.absolutePath).append('\n') }
            })
        }

        fun readSetup(moduleDir: File): Setup? {
            val file = File(moduleDir, FILE)
            if (!file.isFile) return null
            var boot: File? = null
            val cp = mutableListOf<File>()
            val src = mutableListOf<File>()
            file.readLines().forEach { line ->
                val kind = line.substringBefore('\t')
                val path = File(line.substringAfter('\t'))
                when (kind) {
                    "boot" -> boot = path
                    "cp" -> cp += path
                    "src" -> src += path
                }
            }
            return boot?.let { Setup(it, cp, src) }
        }
    }
}
