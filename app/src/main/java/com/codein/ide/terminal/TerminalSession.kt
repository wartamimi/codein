package com.codein.ide.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/** One line of terminal output. */
data class TerminalLine(val text: String, val kind: Kind) {
    enum class Kind { Output, Error, Command, System }
}

/**
 * A line-based shell session: `/system/bin/sh` from the Android system image, started in the
 * project folder. There is no pseudo-terminal (Android apps cannot open one without native code),
 * so full-screen programs such as `vi` or `top` do not work; ordinary commands (`ls`, `cat`,
 * `grep`, `toybox …`, `getprop`, `ps`) do. Codein's bundled `aapt2` is on the PATH.
 */
class TerminalSession(
    private val workingDir: File,
    private val extraPath: List<File>,
    private val scope: CoroutineScope,
) {
    private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private val readers = mutableListOf<Job>()

    fun start() {
        if (process != null) return
        val builder = ProcessBuilder("/system/bin/sh").directory(workingDir)
        val env = builder.environment()
        env["HOME"] = workingDir.absolutePath
        env["PWD"] = workingDir.absolutePath
        env["TERM"] = "dumb"
        env["PATH"] = (extraPath.map { it.absolutePath } + (env["PATH"] ?: "/system/bin:/system/xbin")).joinToString(":")
        val started = runCatching { builder.start() }.getOrElse {
            append(TerminalLine("Could not start /system/bin/sh: ${it.message}", TerminalLine.Kind.Error))
            return
        }
        process = started
        writer = OutputStreamWriter(started.outputStream, StandardCharsets.UTF_8)
        _running.value = true
        append(TerminalLine("Android shell in ${workingDir.absolutePath}. No full-screen programs (no PTY). Type 'exit' to end.", TerminalLine.Kind.System))
        readers += pump(started.inputStream, TerminalLine.Kind.Output)
        readers += pump(started.errorStream, TerminalLine.Kind.Error)
        scope.launch(Dispatchers.IO) {
            val code = runCatching { started.waitFor() }.getOrDefault(-1)
            _running.value = false
            process = null
            writer = null
            append(TerminalLine("Shell exited with code $code", TerminalLine.Kind.System))
        }
    }

    fun send(command: String) {
        val w = writer
        if (w == null) {
            start()
            if (writer == null) return
        }
        append(TerminalLine("$ $command", TerminalLine.Kind.Command))
        scope.launch(Dispatchers.IO) {
            runCatching {
                writer?.apply {
                    write(command)
                    write("\n")
                    flush()
                }
            }.onFailure { append(TerminalLine("Write failed: ${it.message}", TerminalLine.Kind.Error)) }
        }
    }

    /** Sends Ctrl+C equivalent: kills the shell's child processes, keeping the shell. */
    fun interrupt() {
        val p = process ?: return
        runCatching {
            val pid = p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p)
            Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "pkill -INT -P $pid")).waitFor()
        }
        append(TerminalLine("^C", TerminalLine.Kind.System))
    }

    fun clear() = _lines.update { emptyList() }

    fun stop() {
        process?.destroy()
        readers.forEach { it.cancel() }
        readers.clear()
        process = null
        writer = null
        _running.value = false
    }

    private fun pump(stream: InputStream, kind: TerminalLine.Kind): Job = scope.launch(Dispatchers.IO) {
        stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (isActive) {
                val line = reader.readLine() ?: break
                append(TerminalLine(line, kind))
            }
        }
    }

    private fun append(line: TerminalLine) = _lines.update { (it + line).takeLast(MAX_LINES) }

    private companion object {
        const val MAX_LINES = 5000
    }
}
