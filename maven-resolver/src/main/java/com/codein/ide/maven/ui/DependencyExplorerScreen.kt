package com.codein.ide.maven.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.EmptyState
import com.codein.ide.core.ui.components.PanelHeader
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.codeTextStyle
import com.codein.ide.maven.model.DependencyNode
import com.codein.ide.maven.model.MavenCoordinate
import com.codein.ide.maven.model.MavenRepository
import com.codein.ide.maven.model.SearchHit

/** Searchable Maven Dependency Explorer with a resolved dependency tree tab. */
@Composable
fun DependencyExplorerScreen(
    state: DependencyExplorerState,
    onIntent: (DependencyExplorerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        PanelHeader(title = "Maven Dependencies", icon = CodeinIcons.Package) {
            ToolbarIconButton(CodeinIcons.Refresh, "Resolve dependency tree", size = 32.dp) { onIntent(DependencyExplorerIntent.ResolveTree) }
        }
        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Search", style = MaterialTheme.typography.labelLarge) })
            Tab(selected = tab == 1, onClick = { tab = 1; if (state.tree.isEmpty()) onIntent(DependencyExplorerIntent.ResolveTree) }, text = { Text("Resolved tree", style = MaterialTheme.typography.labelLarge) })
        }
        when (tab) {
            0 -> SearchTab(state, onIntent)
            else -> TreeTab(state)
        }
    }
}

@Composable
private fun SearchTab(state: DependencyExplorerState, onIntent: (DependencyExplorerIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(DependencyExplorerIntent.QueryChanged(it)) },
            placeholder = { Text("Search artifacts, e.g. retrofit or androidx.room") },
            leadingIcon = { Icon(CodeinIcons.Search, null, Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MavenRepository.Defaults.forEach { repo ->
                PillChip(
                    text = repo.name,
                    selected = repo.id in state.repositories,
                    onClick = { onIntent(DependencyExplorerIntent.ToggleRepository(repo.id)) },
                )
            }
        }
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp))
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
        if (state.results.isEmpty() && !state.searching) {
            EmptyState(
                icon = CodeinIcons.Package,
                title = if (state.query.length < 2) "Find a library" else "No results",
                message = if (state.query.length < 2) "Search Google Maven, Maven Central and JitPack (use user/repo for JitPack)."
                else "Try a group id such as com.squareup.okhttp3 or a different repository.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.results, key = { "${it.repository.id}:${it.group}:${it.artifact}" }) { hit ->
                val key = "${hit.group}:${hit.artifact}"
                SearchHitCard(
                    hit = hit,
                    expanded = state.expandedHit == key,
                    versions = state.versions[key],
                    canAdd = state.targetBuildFile != null,
                    onExpand = { onIntent(DependencyExplorerIntent.Expand(hit)) },
                    onAdd = { version, config -> onIntent(DependencyExplorerIntent.Add(MavenCoordinate(hit.group, hit.artifact, version), config)) },
                )
            }
        }
    }
}

@Composable
private fun SearchHitCard(
    hit: SearchHit,
    expanded: Boolean,
    versions: List<String>?,
    canAdd: Boolean,
    onExpand: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var configuration by remember { mutableStateOf("implementation") }
    CodeinCard(onClick = onExpand, selected = expanded) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hit.artifact, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.group, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CountBadge(hit.latestVersion, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            CountBadge(hit.repository.name, CodeinTheme.colors.info)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("implementation", "api", "debugImplementation", "testImplementation").forEach { c ->
                        PillChip(c, selected = configuration == c, onClick = { configuration = c })
                    }
                }
                if (versions == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        versions.take(12).forEach { v ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(v, style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false), modifier = Modifier.weight(1f))
                                TextButton(onClick = { onAdd(v, configuration) }, enabled = canAdd) {
                                    Icon(CodeinIcons.Plus, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeTab(state: DependencyExplorerState) {
    Column(Modifier.fillMaxSize()) {
        if (state.resolving) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            state.resolveLog.lastOrNull()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
        if (state.tree.isEmpty() && !state.resolving) {
            EmptyState(CodeinIcons.Layers, "No resolved dependencies", "Declare dependencies in build.gradle(.kts) and tap refresh.", Modifier.fillMaxWidth())
        }
        val rows = remember(state.tree) { flatten(state.tree) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(rows.size) { index ->
                val (depth, node) = rows[index]
                TreeRow(depth, node)
            }
        }
    }
}

@Composable
private fun TreeRow(depth: Int, node: DependencyNode) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (12 + depth * 14).dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (depth == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
        Spacer(Modifier.width(8.dp))
        Text(
            "${node.coordinate.group}:${node.coordinate.artifact}",
            style = codeTextStyle(CodeinTheme.editorPreferences.codingFont, 12f, false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val versionText = if (node.conflictResolved) "${node.requestedVersion} → ${node.coordinate.version}" else node.coordinate.version
        Text(
            versionText,
            style = MaterialTheme.typography.labelSmall,
            color = if (node.conflictResolved) CodeinTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun flatten(nodes: List<DependencyNode>, depth: Int = 0): List<Pair<Int, DependencyNode>> =
    nodes.flatMap { listOf(depth to it) + flatten(it.children, depth + 1) }
