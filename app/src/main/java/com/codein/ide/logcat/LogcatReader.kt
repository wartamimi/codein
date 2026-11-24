package com.codein.ide.logcat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

enum class LogPriority(val letter: Char) {
    Verbose('V'), Debug('D'), Info('I'), Warn('W'), Error('E'), Assert('A');

    companion object {
        fun of(c: Char): LogPriority = entries.firstOrNull { it.letter == c } ?: Verbose
    }
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val priority: LogPriority,
    val tag: String,
    val message: String,
)

/** Parser for `logcat -v threadtime` lines: `MM-DD HH:MM:SS.mmm  PID  TID L TAG     : message`. */
object LogcatParser {
    private val threadtime = Regex("""^(\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEAF])\s+(.*?)\s*:\s?(.*)$""")

    fun parse(id: Long, line: String): LogEntry? {
        val m = threadtime.matchEntire(line) ?: return null
        val g = m.groupValues
        val letter = if (g[4] == "F") 'A' else g[4][0]
        return LogEntry(id, g[1], g[2].toInt(), g[3].toInt(), LogPriority.of(letter), g[5], g[6])
    }
}

/**
 * Streams the device log by running the platform `logcat` binary. Without the READ_LOGS
 * permission (grantable only through `adb shell pm grant com.codein.ide android.permission.READ_LOGS`)
 * Android limits the output to this app's own processes.
 */
@Singleton
class LogcatReader @Inject constructor() {

    fun stream(pid: Int? = null, tail: Int = 500): Flow<LogEntry> = flow {
        val command = mutableListOf("logcat", "-v", "threadtime", "-T", tail.toString())
        if (pid != null) command += "--pid=$pid"
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        try {
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                var id = 0L
                var last: LogEntry? = null
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (line.startsWith("--------- beginning of")) continue
                    val entry = LogcatParser.parse(id, line)
                    if (entry != null) {
                        id++
                        last = entry
                        emit(entry)
                    } else if (last != null && line.isNotBlank()) {
                        // Continuation line of a multi-line message (stack traces).
                        val continuation = last.copy(id = id++, message = line)
                        emit(continuation)
                    }
                }
            }
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    /** Clears the log buffer (`logcat -c`); only affects entries this app is allowed to see. */
    suspend fun clear() {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { ProcessBuilder("logcat", "-c").start().waitFor() }
        }
    }
}
