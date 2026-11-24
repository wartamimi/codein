package com.codein.ide.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.data.ProjectEntity
import com.codein.ide.project.ProjectRepository
import com.codein.ide.project.ProjectSpec
import com.codein.ide.vcs.git.GitCredentialsRepository
import com.codein.ide.vcs.git.GitService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface HomeEvent {
    data class OpenProject(val path: String) : HomeEvent
    data class Message(val text: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val git: GitService,
    private val gitCredentials: GitCredentialsRepository,
) : ViewModel() {

    val recent: StateFlow<List<ProjectEntity>> = projects.recentProjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    fun create(spec: ProjectSpec) = run("Creating ${spec.name}") {
        projects.create(spec).fold(
            onSuccess = { _events.emit(HomeEvent.OpenProject(it.absolutePath)) },
            onFailure = { _events.emit(HomeEvent.Message(it.message ?: "Could not create project")) },
        )
    }

    fun importFolder(uri: Uri) = run("Importing project") {
        projects.importFromTree(uri).fold(
            onSuccess = { _events.emit(HomeEvent.OpenProject(it.absolutePath)) },
            onFailure = { _events.emit(HomeEvent.Message(it.message ?: "Import failed")) },
        )
    }

    fun clone(url: String) = run("Cloning repository") {
        val name = url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifEmpty { "Cloned" }
        val target = projects.newCloneTarget(name)
        runCatching {
            target.delete()
            git.clone(url.trim(), target, gitCredentials.current())
            projects.register(target)
        }.fold(
            onSuccess = { _events.emit(HomeEvent.OpenProject(target.absolutePath)) },
            onFailure = {
                File(target.absolutePath).deleteRecursively()
                _events.emit(HomeEvent.Message(it.message ?: "Clone failed"))
            },
        )
    }

    fun delete(project: ProjectEntity) = run("Deleting ${project.name}") {
        projects.delete(project.path)
        _events.emit(HomeEvent.Message("Deleted ${project.name}"))
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = label
            try {
                block()
            } finally {
                _busy.value = null
            }
        }
    }
}
