package com.codein.ide.compiler.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcessResult(val exitCode: Int, val stdout: List<String>, val stderr: List<String>) {
    val isSuccess: Boolean get() = exitCode == 0
    val allOutput: List<String> get() = stdout + stderr
}

/**
 * Runs native tools with cancellation support. Output lines are streamed to [onLine] as they arrive
 * and the process is destroyed if the calling coroutine is cancelled.
 */
object ProcessRunner {

    suspend fun run(
        command: List<String>,
        workingDir: File? = null,
        environment: Map<String, String> = emptyMap(),
        onLine: (isError: Boolean, line: String) -> Unit = { _, _ -> },
    ): ProcessResult = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder(command).apply {
            workingDir?.let { directory(it) }
            environment().putAll(environment)
        }
        val process = builder.start()
        try {
            coroutineScope {
                val out = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader().useLines { seq -> seq.onEach { onLine(false, it) }.toList() }
                }
                val err = async(Dispatchers.IO) {
                    process.errorStream.bufferedReader().useLines { seq -> seq.onEach { onLine(true, it) }.toList() }
                }
                while (isActive && !process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                currentCoroutineContext().ensureActive()
                ProcessResult(process.exitValue(), out.await(), err.await())
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
