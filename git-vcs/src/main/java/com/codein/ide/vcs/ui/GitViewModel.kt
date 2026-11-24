package com.codein.ide.vcs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.vcs.git.GitBranchInfo
import com.codein.ide.vcs.git.GitCommitInfo
import com.codein.ide.vcs.git.GitCredentials
import com.codein.ide.vcs.git.GitCredentialsRepository
import com.codein.ide.vcs.git.GitService
import com.codein.ide.vcs.git.GitStatusSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class GitPanelState(
    val projectDir: File? = null,
    val status: GitStatusSnapshot = GitStatusSnapshot(isRepository = false),
    val branches: List<GitBranchInfo> = emptyList(),
    val log: List<GitCommitInfo> = emptyList(),
    val commitMessage: String = "",
    val selectedDiffPath: String? = null,
    val diffText: String = "",
    val busy: String? = null,
    val credentials: GitCredentials = GitCredentials("", "", "", ""),
)

sealed interface GitIntent {
    data object Refresh : GitIntent
    data object Init : GitIntent
    data class CommitMessage(val text: String) : GitIntent
    data class Commit(val stageAll: Boolean) : GitIntent
    data class ToggleStage(val path: String) : GitIntent
    data object StageAll : GitIntent
    data class ShowDiff(val path: String?) : GitIntent
    data class Discard(val path: String) : GitIntent
    data class Checkout(val branch: String) : GitIntent
    data class CreateBranch(val name: String) : GitIntent
    data class SetRemote(val url: String) : GitIntent
    data object Push : GitIntent
    data object Pull : GitIntent
    data class SaveCredentials(val username: String, val token: String?, val authorName: String, val authorEmail: String) : GitIntent
}

@HiltViewModel
class GitViewModel @Inject constructor(
    private val git: GitService,
    private val credentialsRepository: GitCredentialsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GitPanelState())
    val state: StateFlow<GitPanelState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { credentialsRepository.credentials.collect { c -> _state.update { it.copy(credentials = c) } } }
    }

    fun setProject(dir: File) {
        if (_state.value.projectDir == dir) return
        _state.update { GitPanelState(projectDir = dir, credentials = it.credentials) }
        refresh()
    }

    fun onIntent(intent: GitIntent) {
        val dir = _state.value.projectDir ?: return
        when (intent) {
            GitIntent.Refresh -> refresh()
            GitIntent.Init -> run("Initializing") { git.init(dir); "Initialized Git repository" }
            is GitIntent.CommitMessage -> _state.update { it.copy(commitMessage = intent.text) }
            is GitIntent.Commit -> run("Committing") {
                val creds = credentialsRepository.current()
                val info = git.commit(dir, _state.value.commitMessage, creds.ident(), intent.stageAll)
                _state.update { it.copy(commitMessage = "") }
                "Committed ${info.shortId}: ${info.message}"
            }
            is GitIntent.ToggleStage -> run(null) {
                if (intent.path in _state.value.status.staged) git.unstage(dir, listOf(intent.path)) else git.stage(dir, listOf(intent.path))
                null
            }
            GitIntent.StageAll -> run(null) { git.stageAll(dir); null }
            is GitIntent.ShowDiff -> {
                val path = intent.path
                if (path == null) _state.update { it.copy(selectedDiffPath = null, diffText = "") }
                else viewModelScope.launch {
                    val text = runCatching { git.diff(dir, path) }.getOrElse { it.message.orEmpty() }
                    _state.update { it.copy(selectedDiffPath = path, diffText = text) }
                }
            }
            is GitIntent.Discard -> run("Discarding") { git.discard(dir, intent.path); "Discarded changes to ${intent.path}" }
            is GitIntent.Checkout -> run("Switching branch") { git.checkout(dir, intent.branch, create = false); "Checked out ${intent.branch}" }
            is GitIntent.CreateBranch -> run("Creating branch") { git.checkout(dir, intent.name.trim(), create = true); "Created and checked out ${intent.name.trim()}" }
            is GitIntent.SetRemote -> run("Saving remote") { git.setRemote(dir, intent.url.trim()); "Remote origin set" }
            GitIntent.Push -> run("Pushing") { git.push(dir, credentialsRepository.current()) }
            GitIntent.Pull -> run("Pulling") { git.pull(dir, credentialsRepository.current()) }
            is GitIntent.SaveCredentials -> viewModelScope.launch {
                credentialsRepository.save(intent.username, intent.token, intent.authorName, intent.authorEmail)
                _messages.emit("Git credentials saved")
            }
        }
    }

    fun refresh() {
        val dir = _state.value.projectDir ?: return
        viewModelScope.launch {
            val status = runCatching { git.status(dir) }.getOrElse {
                _messages.emit(it.message.orEmpty())
                GitStatusSnapshot(isRepository = git.isRepository(dir))
            }
            val branches = if (status.isRepository) runCatching { git.branches(dir) }.getOrDefault(emptyList()) else emptyList()
            val log = git.log(dir)
            _state.update { it.copy(status = status, branches = branches, log = log) }
        }
    }

    private fun run(label: String?, block: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = label) }
            val message = runCatching { block() }.getOrElse { it.message ?: it.javaClass.simpleName }
            _state.update { it.copy(busy = null) }
            message?.let { _messages.emit(it) }
            refresh()
        }
    }
}
