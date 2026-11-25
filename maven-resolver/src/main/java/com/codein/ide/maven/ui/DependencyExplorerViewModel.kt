package com.codein.ide.maven.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.maven.gradle.GradleDependencyWriter
import com.codein.ide.maven.model.DependencyNode
import com.codein.ide.maven.model.MavenCoordinate
import com.codein.ide.maven.model.MavenRepository
import com.codein.ide.maven.model.SearchHit
import com.codein.ide.maven.resolve.MavenResolver
import com.codein.ide.maven.resolve.MavenSearchService
import com.codein.ide.maven.resolve.MavenVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DependencyExplorerState(
    val query: String = "",
    val repositories: Set<String> = MavenRepository.Defaults.map { it.id }.toSet(),
    val searching: Boolean = false,
    val results: List<SearchHit> = emptyList(),
    val expandedHit: String? = null,
    val versions: Map<String, List<String>> = emptyMap(),
    val targetBuildFile: File? = null,
    val resolving: Boolean = false,
    val tree: List<DependencyNode> = emptyList(),
    val resolveLog: List<String> = emptyList(),
    val error: String? = null,
)

sealed interface DependencyExplorerIntent {
    data class QueryChanged(val query: String) : DependencyExplorerIntent
    data class ToggleRepository(val id: String) : DependencyExplorerIntent
    data class Expand(val hit: SearchHit) : DependencyExplorerIntent
    data class Add(val coordinate: MavenCoordinate, val configuration: String) : DependencyExplorerIntent
    data class Remove(val group: String, val artifact: String) : DependencyExplorerIntent
    data class SetTarget(val buildFile: File?, val declared: List<MavenCoordinate>) : DependencyExplorerIntent
    data object ResolveTree : DependencyExplorerIntent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DependencyExplorerViewModel @Inject constructor(
    private val search: MavenSearchService,
    private val resolver: MavenResolver,
    private val writer: GradleDependencyWriter,
) : ViewModel() {

    private val _state = MutableStateFlow(DependencyExplorerState())
    val state: StateFlow<DependencyExplorerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var declared: List<MavenCoordinate> = emptyList()
    private var searchJob: Job? = null
    private var resolveJob: Job? = null

    init {
        viewModelScope.launch {
            _state.map { it.query to it.repositories }
                .distinctUntilChanged()
                .debounce(350)
                .collect { (query, repos) -> runSearch(query, repos) }
        }
    }

    fun onIntent(intent: DependencyExplorerIntent) {
        when (intent) {
            is DependencyExplorerIntent.QueryChanged -> _state.update { it.copy(query = intent.query) }
            is DependencyExplorerIntent.ToggleRepository -> _state.update {
                val repos = if (intent.id in it.repositories) it.repositories - intent.id else it.repositories + intent.id
                it.copy(repositories = repos.ifEmpty { setOf(intent.id) })
            }
            is DependencyExplorerIntent.Expand -> expand(intent.hit)
            is DependencyExplorerIntent.Add -> add(intent.coordinate, intent.configuration)
            is DependencyExplorerIntent.Remove -> remove(intent.group, intent.artifact)
            is DependencyExplorerIntent.SetTarget -> {
                declared = intent.declared
                _state.update { it.copy(targetBuildFile = intent.buildFile) }
            }
            DependencyExplorerIntent.ResolveTree -> resolveTree()
        }
    }

    private fun runSearch(query: String, repos: Set<String>) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null) }
            val hits = runCatching { search.search(query, repos) }
            _state.update {
                it.copy(
                    searching = false,
                    results = hits.getOrDefault(emptyList()),
                    error = hits.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun expand(hit: SearchHit) {
        val key = "${hit.group}:${hit.artifact}"
        if (_state.value.expandedHit == key) {
            _state.update { it.copy(expandedHit = null) }
            return
        }
        _state.update { it.copy(expandedHit = key) }
        if (_state.value.versions.containsKey(key)) return
        viewModelScope.launch {
            val versions = runCatching { resolver.availableVersions(hit.group, hit.artifact, listOf(hit.repository)) }
                .getOrDefault(emptyList())
                .sortedWith(MavenVersion.reversed())
            _state.update { it.copy(versions = it.versions + (key to versions.ifEmpty { listOf(hit.latestVersion) })) }
        }
    }

    private fun add(coordinate: MavenCoordinate, configuration: String) {
        val file = _state.value.targetBuildFile
        if (file == null) {
            _messages.tryEmit("Open a project first to add dependencies.")
            return
        }
        viewModelScope.launch {
            val result = runCatching { writer.addDependency(file, coordinate, configuration) }
            result.onSuccess { r ->
                when (r) {
                    is GradleDependencyWriter.WriteResult.Added -> {
                        declared = declared + coordinate
                        _messages.tryEmit("Added ${r.line} to ${file.name}")
                    }
                    is GradleDependencyWriter.WriteResult.Updated -> {
                        declared = declared.filterNot { it.groupArtifact == coordinate.groupArtifact } + coordinate
                        _messages.tryEmit("Updated ${coordinate.groupArtifact} ${r.from} → ${r.to}")
                    }
                    GradleDependencyWriter.WriteResult.AlreadyPresent -> _messages.tryEmit("$coordinate is already declared")
                }
            }.onFailure { _messages.tryEmit("Could not edit ${file.name}: ${it.message}") }
        }
    }

    private fun remove(group: String, artifact: String) {
        val file = _state.value.targetBuildFile ?: return
        if (writer.removeDependency(file, group, artifact)) {
            declared = declared.filterNot { it.group == group && it.artifact == artifact }
            _messages.tryEmit("Removed $group:$artifact")
            resolveTree()
        }
    }

    private fun resolveTree() {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _state.update { it.copy(resolving = true, resolveLog = emptyList(), error = null) }
            val result = runCatching {
                resolver.resolve(declared) { event ->
                    _state.update { s -> s.copy(resolveLog = (s.resolveLog + event.toString()).takeLast(200)) }
                }
            }
            _state.update {
                it.copy(
                    resolving = false,
                    tree = result.getOrNull()?.roots ?: it.tree,
                    error = result.exceptionOrNull()?.message
                        ?: result.getOrNull()?.failures?.takeIf { f -> f.isNotEmpty() }?.joinToString("\n") { f -> "${f.coordinate}: ${f.reason}" },
                )
            }
        }
    }
}
