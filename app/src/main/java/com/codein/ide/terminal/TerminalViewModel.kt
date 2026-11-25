package com.codein.ide.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/** Owns the workspace's shell session; it survives configuration changes and closing the dock. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val session = MutableStateFlow<TerminalSession?>(null)
    private val history = mutableListOf<String>()

    val lines: StateFlow<List<TerminalLine>> = session.flatMapLatest { it?.lines ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val running: StateFlow<Boolean> = session.flatMapLatest { it?.running ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var workingDir: String? = null
    private var cwd: File? = null

    /** Starts the shell in [projectDir], or restarts it there when it ran in another folder. */
    fun open(projectDir: File) {
        val current = session.value
        if (current != null && workingDir == projectDir.absolutePath) {
            current.start()
            return
        }
        current?.stop()
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val created = TerminalSession(projectDir, listOf(toolsDir(nativeDir)), viewModelScope)
        workingDir = projectDir.absolutePath
        cwd = projectDir
        session.value = created
        created.start()
    }

    fun send(command: String) {
        val trimmed = command.trimEnd()
        if (trimmed.isEmpty()) return
        history.remove(trimmed)
        history += trimmed
        session.value?.send(trimmed)
        val home = workingDir?.let(::File) ?: return
        cwd = TerminalCompletion.afterCommand(trimmed, cwd ?: home, home)
    }

    /** Completes the last word of [input] against the shell's current folder. */
    fun complete(input: String): TerminalCompletion.Result {
        val dir = cwd ?: return TerminalCompletion.Result(input, emptyList())
        return TerminalCompletion.complete(input, dir)
    }

    /** Shows the candidates of an ambiguous Tab completion in the output. */
    fun showCandidates(candidates: List<String>) {
        session.value?.note(candidates.joinToString("  "))
    }

    fun historyAt(offsetFromEnd: Int): String? = history.getOrNull(history.size - offsetFromEnd)

    fun interrupt() = session.value?.interrupt()
    fun clear() = session.value?.clear()
    fun restart() {
        val dir = workingDir ?: return
        session.value?.stop()
        session.value = null
        workingDir = null
        open(File(dir))
    }

    override fun onCleared() {
        session.value?.stop()
    }

    /**
     * Exposes Codein's bundled tools under their real names. `libaapt2.so` lives in the read-only
     * nativeLibraryDir, so a symlink named `aapt2` in app storage points at it.
     */
    private fun toolsDir(nativeDir: File): File {
        val bin = File(context.filesDir, "terminal-bin").apply { mkdirs() }
        val aapt2 = File(nativeDir, "libaapt2.so")
        val link = File(bin, "aapt2")
        if (aapt2.isFile && !link.exists()) {
            runCatching { android.system.Os.symlink(aapt2.absolutePath, link.absolutePath) }
        }
        return bin
    }
}
