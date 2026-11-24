package com.codein.ide.logcat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.PatternSyntaxException
import javax.inject.Inject

data class LogcatState(
    val entries: List<LogEntry> = emptyList(),
    val minPriority: LogPriority = LogPriority.Verbose,
    val filter: String = "",
    val filterError: String? = null,
    val lockedPid: Int? = null,
    val knownPids: List<Int> = emptyList(),
    val paused: Boolean = false,
    val running: Boolean = false,
    val autoScroll: Boolean = true,
    /** True when READ_LOGS is granted, so other apps' logs (e.g. an app you just installed) are visible. */
    val canReadAllLogs: Boolean = false,
) {
    private val regex: Regex? = if (filter.isBlank()) null else runCatching { Regex(filter, RegexOption.IGNORE_CASE) }.getOrNull()

    /** Entries after priority, PID-lock and regex filtering (tag or message). */
    val visible: List<LogEntry> = entries.filter { e ->
        e.priority.ordinal >= minPriority.ordinal &&
            (lockedPid == null || e.pid == lockedPid) &&
            (regex == null || regex.containsMatchIn(e.tag) || regex.containsMatchIn(e.message))
    }
}

sealed interface LogcatIntent {
    data class MinPriority(val priority: LogPriority) : LogcatIntent
    data class Filter(val text: String) : LogcatIntent
    data class LockPid(val pid: Int?) : LogcatIntent
    data object TogglePause : LogcatIntent
    data object Clear : LogcatIntent
    data class AutoScroll(val enabled: Boolean) : LogcatIntent
}

@HiltViewModel
class LogcatViewModel @Inject constructor(
    private val reader: LogcatReader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(LogcatState())
    val state: StateFlow<LogcatState> = _state.asStateFlow()
    private var job: Job? = null
    private val pending = ArrayList<LogEntry>()

    fun start() {
        if (job?.isActive == true) return
        val granted = context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(running = true, canReadAllLogs = granted) }
        job = viewModelScope.launch {
            var lastFlush = 0L
            reader.stream().collect { entry ->
                if (_state.value.paused) return@collect
                pending += entry
                val now = System.currentTimeMillis()
                if (now - lastFlush > FLUSH_MS || pending.size > 200) {
                    lastFlush = now
                    flush()
                }
            }
            flush()
            _state.update { it.copy(running = false) }
        }
    }

    /** The adb command that grants READ_LOGS to this installation of Codein. */
    val grantCommand: String get() = "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    private fun flush() {
        if (pending.isEmpty()) return
        val batch = ArrayList(pending)
        pending.clear()
        _state.update { s ->
            val merged = (s.entries + batch).takeLast(MAX_ENTRIES)
            s.copy(entries = merged, knownPids = (s.knownPids + batch.map { it.pid }).distinct().takeLast(32))
        }
    }

    fun onIntent(intent: LogcatIntent) {
        when (intent) {
            is LogcatIntent.MinPriority -> _state.update { it.copy(minPriority = intent.priority) }
            is LogcatIntent.Filter -> {
                val error = if (intent.text.isBlank()) null else try {
                    Regex(intent.text); null
                } catch (e: PatternSyntaxException) {
                    e.description
                }
                _state.update { it.copy(filter = intent.text, filterError = error) }
            }
            is LogcatIntent.LockPid -> _state.update { it.copy(lockedPid = intent.pid) }
            LogcatIntent.TogglePause -> _state.update { it.copy(paused = !it.paused) }
            LogcatIntent.Clear -> {
                viewModelScope.launch { reader.clear() }
                _state.update { it.copy(entries = emptyList()) }
            }
            is LogcatIntent.AutoScroll -> _state.update { it.copy(autoScroll = intent.enabled) }
        }
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val MAX_ENTRIES = 5000
        const val FLUSH_MS = 150L
    }
}
