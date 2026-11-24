package com.codein.ide.compiler.ecj

import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.DiagnosticSource
import com.codein.ide.compiler.model.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.eclipse.jdt.core.compiler.CompilationProgress
import org.eclipse.jdt.internal.compiler.ICompilerRequestor
import org.eclipse.jdt.internal.compiler.batch.Main
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import javax.inject.Inject
import javax.inject.Singleton

data class JavaCompileRequest(
    val sourceFiles: List<File>,
    val classpath: List<File>,
    val bootClasspath: File,
    val outputDir: File,
    val languageLevel: String = DEFAULT_LANGUAGE_LEVEL,
    val debugInfo: Boolean = true,
) {
    companion object {
        /**
         * ECJ needs a JDK module image (`jrt-fs`) for compliance levels 9+, which does not exist on
         * Android. Compiling at 1.8 against `android.jar` as the boot classpath is the reliable
         * on-device configuration; D8 then desugars lambdas and default methods.
         */
        const val DEFAULT_LANGUAGE_LEVEL = "1.8"
    }
}

data class JavaCompileResult(val success: Boolean, val diagnostics: List<CompilerDiagnostic>, val classesDir: File)

/**
 * Programmatic Eclipse Compiler for Java. Problems are captured from ECJ's compilation results
 * (exact file/line/column) rather than scraped from text output.
 */
@Singleton
class EcjCompiler @Inject constructor() {

    suspend fun compile(request: JavaCompileRequest, onLog: (String) -> Unit = {}): JavaCompileResult = withContext(Dispatchers.Default) {
        val job = currentCoroutineContext()[Job]
        request.outputDir.mkdirs()
        if (request.sourceFiles.isEmpty()) {
            return@withContext JavaCompileResult(true, emptyList(), request.outputDir)
        }
        val diagnostics = mutableListOf<CompilerDiagnostic>()
        val outWriter = LineForwardingWriter(onLog)
        val errWriter = LineForwardingWriter(onLog)
        val progress = object : CompilationProgress() {
            override fun begin(remainingWork: Int) = Unit
            override fun done() = Unit
            override fun isCanceled(): Boolean = job?.isActive == false
            override fun setTaskName(name: String?) = Unit
            override fun worked(workIncrement: Int, remainingWork: Int) = Unit
        }

        val main = object : Main(PrintWriter(outWriter), PrintWriter(errWriter), false, null, progress) {
            override fun getBatchRequestor(): ICompilerRequestor {
                val delegate = super.getBatchRequestor()
                return ICompilerRequestor { result ->
                    result.allProblems?.forEach { problem ->
                        val fileName = problem.originatingFileName?.let { String(it) } ?: String(result.getFileName())
                        val column = (problem as? DefaultProblem)?.sourceColumnNumber
                        diagnostics += CompilerDiagnostic(
                            source = DiagnosticSource.Ecj,
                            severity = when {
                                problem.isError -> Severity.Error
                                problem.isWarning -> Severity.Warning
                                else -> Severity.Info
                            },
                            message = problem.message,
                            file = fileName,
                            line = problem.sourceLineNumber.takeIf { it > 0 },
                            column = column?.takeIf { it > 0 },
                            endColumn = column?.let { c -> c + (problem.sourceEnd - problem.sourceStart).coerceAtLeast(0) + 1 },
                        )
                    }
                    delegate.acceptResult(result)
                }
            }
        }

        val args = buildList {
            add("-" + request.languageLevel)
            add("-source"); add(request.languageLevel)
            add("-target"); add(request.languageLevel)
            add("-encoding"); add("UTF-8")
            add("-proc:none")
            add("-nowarn")
            add("-warn:+deprecation")
            if (request.debugInfo) add("-g") else add("-g:none")
            add("-bootclasspath"); add(request.bootClasspath.absolutePath)
            val cp = request.classpath.filter { it.exists() }
            if (cp.isNotEmpty()) {
                add("-cp"); add(cp.joinToString(File.pathSeparator) { it.absolutePath })
            }
            add("-d"); add(request.outputDir.absolutePath)
            request.sourceFiles.forEach { add(it.absolutePath) }
        }

        val ok = runCatching { main.compile(args.toTypedArray()) }.getOrElse { error ->
            diagnostics += CompilerDiagnostic(DiagnosticSource.Ecj, Severity.Error, "ECJ crashed: ${error.javaClass.simpleName}: ${error.message}")
            false
        }
        outWriter.flush()
        errWriter.flush()
        val errors = diagnostics.count { it.severity == Severity.Error }
        if (!ok && errors == 0) {
            diagnostics += CompilerDiagnostic(DiagnosticSource.Ecj, Severity.Error, "ECJ reported a failure without problems; see the build log.")
        }
        JavaCompileResult(ok && errors == 0, diagnostics.distinct(), request.outputDir)
    }

    /** Writer that forwards complete lines to a callback. */
    private class LineForwardingWriter(private val onLine: (String) -> Unit) : Writer() {
        private val buffer = StringWriter()
        override fun write(cbuf: CharArray, off: Int, len: Int) {
            for (i in off until off + len) {
                val c = cbuf[i]
                if (c == '\n') flushLine() else buffer.write(c.code)
            }
        }
        private fun flushLine() {
            val line = buffer.toString().trimEnd('\r')
            buffer.buffer.setLength(0)
            if (line.isNotBlank()) onLine(line)
        }
        override fun flush() { if (buffer.buffer.isNotEmpty()) flushLine() }
        override fun close() = flush()
    }
}
