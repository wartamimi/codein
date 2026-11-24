package com.codein.ide.maven.resolve

import android.content.Context
import com.codein.ide.maven.model.ArtifactType
import com.codein.ide.maven.model.DependencyNode
import com.codein.ide.maven.model.MavenCoordinate
import com.codein.ide.maven.model.MavenRepository
import com.codein.ide.maven.model.ResolutionFailure
import com.codein.ide.maven.model.ResolutionResult
import com.codein.ide.maven.model.ResolveEvent
import com.codein.ide.maven.model.ResolvedArtifact
import com.codein.ide.maven.net.MavenHttpClient
import com.codein.ide.maven.pom.EffectivePom
import com.codein.ide.maven.pom.PomDependency
import com.codein.ide.maven.pom.PomParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recursive transitive dependency resolver for Google Maven, Maven Central and JitPack.
 *
 * - POM inheritance (`<parent>`), property interpolation and `dependencyManagement` (including
 *   `import`-scoped BOMs) are honored.
 * - Version conflicts are resolved Gradle-style: the highest requested version wins. Resolution
 *   iterates to a fixpoint so that dependencies of discarded versions are not included.
 * - Kotlin Multiplatform root publications are redirected to their Android/JVM variant through
 *   Gradle Module Metadata (`.module`, `available-at`).
 * - Artifacts (`.jar` / `.aar`) are cached under `filesDir/maven-cache` in standard repository layout.
 */
@Singleton
class MavenResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: MavenHttpClient,
) {
    val cacheRoot: File get() = File(context.filesDir, "maven-cache").apply { mkdirs() }

    private val pomCache = mutableMapOf<String, Pair<EffectivePom, MavenRepository>>()
    private val rawCache = mutableMapOf<String, Pair<String, MavenRepository>>()
    private val redirectCache = mutableMapOf<String, MavenCoordinate?>()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val runtimeScopes = setOf("compile", "runtime", "")

    suspend fun resolve(
        roots: List<MavenCoordinate>,
        repositories: List<MavenRepository> = MavenRepository.Defaults,
        onEvent: (ResolveEvent) -> Unit = {},
    ): ResolutionResult = mutex.withLock {
        val failures = mutableListOf<ResolutionFailure>()

        // Pin dynamic root versions first.
        val pinnedRoots = roots.mapNotNull { root ->
            if (!MavenVersion.isDynamic(root.version)) return@mapNotNull root
            val versions = availableVersions(root.group, root.artifact, repositories)
            val chosen = MavenVersion.select(root.version, versions)
            if (chosen == null) {
                failures += ResolutionFailure(root, "No version matches '${root.version}'")
                onEvent(ResolveEvent.Failed(root, "No version matches '${root.version}'"))
                null
            } else root.withVersion(chosen)
        }

        var selected = mutableMapOf<String, String>()
        pinnedRoots.forEach { selected[it.groupArtifact] = it.version }
        var tree: List<DependencyNode> = emptyList()
        val requestedHistory = mutableMapOf<String, MutableSet<String>>()

        for (iteration in 0 until MAX_ITERATIONS) {
            currentCoroutineContext().ensureActive()
            val requested = mutableMapOf<String, MutableSet<String>>()
            pinnedRoots.forEach { requested.getOrPut(it.groupArtifact) { mutableSetOf() } += it.version }
            val expanded = mutableSetOf<String>()

            suspend fun visit(coordinate: MavenCoordinate, requestedVersion: String, exclusions: Set<String>, depth: Int): DependencyNode? {
                currentCoroutineContext().ensureActive()
                val chosenVersion = selected[coordinate.groupArtifact] ?: requestedVersion
                val effective = coordinate.withVersion(chosenVersion)
                val key = effective.toString()
                if (!expanded.add(key) || depth > MAX_DEPTH) {
                    return DependencyNode(effective, requestedVersion, emptyList(), chosenVersion != requestedVersion)
                }
                onEvent(ResolveEvent.Resolving(effective))
                val pom = try {
                    effectivePom(effective, repositories)
                } catch (e: IOException) {
                    failures += ResolutionFailure(effective, e.message ?: "I/O error")
                    onEvent(ResolveEvent.Failed(effective, e.message ?: "I/O error"))
                    return null
                }
                if (pom == null) {
                    failures += ResolutionFailure(effective, "POM not found in ${repositories.joinToString { it.name }}")
                    onEvent(ResolveEvent.Failed(effective, "POM not found"))
                    return null
                }
                val children = mutableListOf<DependencyNode>()
                for (dep in runtimeDependencies(pom.first)) {
                    if (dep.groupArtifact in exclusions || "${dep.group}:*" in exclusions || "*:*" in exclusions) continue
                    val versionSpec = dep.version ?: pom.first.managedVersions[dep.groupArtifact] ?: continue
                    val concrete = if (MavenVersion.isDynamic(versionSpec)) {
                        MavenVersion.select(versionSpec, availableVersions(dep.group, dep.artifact, repositories)) ?: continue
                    } else MavenVersion.preferred(versionSpec)
                    requested.getOrPut(dep.groupArtifact) { mutableSetOf() } += concrete
                    val childCoordinate = MavenCoordinate(dep.group, dep.artifact, concrete, dep.classifier)
                    visit(childCoordinate, concrete, exclusions + dep.exclusions, depth + 1)?.let(children::add)
                }
                return DependencyNode(effective, requestedVersion, children, chosenVersion != requestedVersion)
            }

            tree = pinnedRoots.mapNotNull { visit(it, it.version, emptySet(), 0) }
            val newSelected = requested.mapValues { (_, versions) -> versions.maxWith(MavenVersion) }.toMutableMap()
            requested.forEach { (ga, versions) -> requestedHistory.getOrPut(ga) { mutableSetOf() }.addAll(versions) }
            if (newSelected == selected) break
            selected = newSelected
        }

        requestedHistory.filter { it.value.size > 1 }.forEach { (ga, versions) ->
            selected[ga]?.let { onEvent(ResolveEvent.VersionConflict(ga, versions.sortedWith(MavenVersion), it)) }
        }

        val artifacts = mutableListOf<ResolvedArtifact>()
        for ((ga, version) in selected) {
            currentCoroutineContext().ensureActive()
            val (group, artifact) = ga.split(':').let { it[0] to it[1] }
            val coordinate = MavenCoordinate(group, artifact, version)
            val artifactResult = runCatching { downloadArtifact(coordinate, repositories, onEvent) }
            artifactResult.onSuccess { it?.let(artifacts::add) }
                .onFailure { e ->
                    failures += ResolutionFailure(coordinate, e.message ?: e.javaClass.simpleName)
                    onEvent(ResolveEvent.Failed(coordinate, e.message ?: "download failed"))
                }
        }
        ResolutionResult(artifacts.sortedBy { it.coordinate.toString() }, tree, failures)
    }

    private fun runtimeDependencies(pom: EffectivePom): List<PomDependency> =
        pom.dependencies.filter { !it.optional && it.scope in runtimeScopes && it.type != "pom" }

    /** Lists all published versions of an artifact across repositories (maven-metadata.xml). */
    suspend fun availableVersions(group: String, artifact: String, repositories: List<MavenRepository> = MavenRepository.Defaults): List<String> {
        val versions = linkedSetOf<String>()
        for (repo in repositories) {
            val url = http.artifactUrl(repo, group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml")
            val text = runCatching { http.getText(url) }.getOrNull() ?: continue
            Regex("""<version>([^<]+)</version>""").findAll(text).forEach { versions += it.groupValues[1].trim() }
        }
        return versions.sortedWith(MavenVersion)
    }

    private suspend fun fetchPomText(coordinate: MavenCoordinate, repositories: List<MavenRepository>): Pair<String, MavenRepository>? {
        val key = "${coordinate.group}:${coordinate.artifact}:${coordinate.version}"
        rawCache[key]?.let { return it }
        val local = File(cacheRoot, coordinate.path + "/${coordinate.artifact}-${coordinate.version}.pom")
        val repoMarker = File(local.parentFile, ".repository")
        if (local.isFile) {
            val repo = repositories.firstOrNull { it.id == repoMarker.takeIf { f -> f.isFile }?.readText()?.trim() } ?: repositories.first()
            return (local.readText() to repo).also { rawCache[key] = it }
        }
        var lastError: IOException? = null
        for (repo in repositories) {
            val url = http.artifactUrl(repo, coordinate.path + "/${coordinate.artifact}-${coordinate.version}.pom")
            val text = try {
                http.getText(url)
            } catch (e: IOException) {
                lastError = e
                null
            } ?: continue
            local.parentFile?.mkdirs()
            local.writeText(text)
            repoMarker.writeText(repo.id)
            return (text to repo).also { rawCache[key] = it }
        }
        lastError?.let { throw it }
        return null
    }

    /** Builds the effective POM: parent chain, properties, dependencyManagement and BOM imports. */
    suspend fun effectivePom(coordinate: MavenCoordinate, repositories: List<MavenRepository>, depth: Int = 0): Pair<EffectivePom, MavenRepository>? {
        val key = "${coordinate.group}:${coordinate.artifact}:${coordinate.version}"
        pomCache[key]?.let { return it }
        if (depth > 12) return null
        val (text, repo) = fetchPomText(coordinate, repositories) ?: return null
        val raw = runCatching { PomParser.parse(text) }.getOrElse { throw IOException("Malformed POM for $coordinate: ${it.message}") }

        val parent = raw.parent?.let { p -> effectivePom(MavenCoordinate(p.group, p.artifact, p.version), repositories, depth + 1)?.first }
        val group = raw.group ?: raw.parent?.group ?: coordinate.group
        val version = raw.version ?: raw.parent?.version ?: coordinate.version

        val props = mutableMapOf<String, String>()
        parent?.let {
            props["parent.version"] = it.version
            props["project.parent.version"] = it.version
            props["parent.groupId"] = it.group
            props["project.parent.groupId"] = it.group
        }
        parentProperties(raw.parent?.let { MavenCoordinate(it.group, it.artifact, it.version) }, repositories, depth).forEach { (k, v) -> props.putIfAbsent(k, v) }
        props.putAll(raw.properties)
        props["project.groupId"] = group
        props["pom.groupId"] = group
        props["groupId"] = group
        props["project.artifactId"] = raw.artifact
        props["project.version"] = version
        props["pom.version"] = version
        props["version"] = version

        val managed = mutableMapOf<String, String>()
        parent?.managedVersions?.let(managed::putAll)
        for (dm in raw.dependencyManagement) {
            val g = PomParser.interpolate(dm.group, props) ?: continue
            val a = PomParser.interpolate(dm.artifact, props) ?: continue
            val v = PomParser.interpolate(dm.version, props) ?: continue
            if (dm.scope == "import" && dm.type == "pom") {
                effectivePom(MavenCoordinate(g, a, v), repositories, depth + 1)?.first?.managedVersions?.forEach { (k, value) -> managed.putIfAbsent(k, value) }
            } else {
                managed["$g:$a"] = v
            }
        }

        val dependencies = mutableListOf<PomDependency>()
        parent?.dependencies?.let(dependencies::addAll)
        for (d in raw.dependencies) {
            val g = PomParser.interpolate(d.group, props) ?: continue
            val a = PomParser.interpolate(d.artifact, props) ?: continue
            val v = PomParser.interpolate(d.version, props)?.takeIf { !it.contains("\${") } ?: managed["$g:$a"]
            dependencies.removeAll { it.groupArtifact == "$g:$a" }
            dependencies += d.copy(group = g, artifact = a, version = v, classifier = PomParser.interpolate(d.classifier, props))
        }

        val effective = EffectivePom(
            group = group,
            artifact = raw.artifact,
            version = version,
            packaging = PomParser.interpolate(raw.packaging, props) ?: "jar",
            dependencies = dependencies,
            managedVersions = managed,
        )
        return (effective to repo).also { pomCache[key] = it }
    }

    private suspend fun parentProperties(parent: MavenCoordinate?, repositories: List<MavenRepository>, depth: Int): Map<String, String> {
        if (parent == null || depth > 12) return emptyMap()
        val (text, _) = fetchPomText(parent, repositories) ?: return emptyMap()
        val raw = runCatching { PomParser.parse(text) }.getOrNull() ?: return emptyMap()
        val inherited = parentProperties(raw.parent?.let { MavenCoordinate(it.group, it.artifact, it.version) }, repositories, depth + 1)
        return inherited + raw.properties
    }

    /** Follows Gradle Module Metadata `available-at` for KMP root publications. */
    private suspend fun androidVariantOf(coordinate: MavenCoordinate, repositories: List<MavenRepository>): MavenCoordinate? {
        val key = coordinate.toString()
        if (redirectCache.containsKey(key)) return redirectCache[key]
        // Persisted answer from an earlier build ("-" means the module has no Android variant).
        val marker = File(cacheRoot, coordinate.path + "/${coordinate.artifact}-${coordinate.version}.codein-variant")
        if (marker.isFile) {
            val stored = marker.readText().trim()
            val cached = if (stored == "-") null else runCatching { MavenCoordinate.parse(stored) }.getOrNull()
            redirectCache[key] = cached
            return cached
        }
        var result: MavenCoordinate? = null
        var reachedAnyRepository = false
        for (repo in repositories) {
            val url = http.artifactUrl(repo, coordinate.path + "/${coordinate.artifact}-${coordinate.version}.module")
            val text = runCatching { http.getText(url) }.getOrNull() ?: continue
            reachedAnyRepository = true
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
            val variants = ((root["variants"] as? JsonArray) ?: JsonArray(emptyList())).map { it.jsonObject }
            fun attr(v: JsonObject, name: String) = (v["attributes"] as? JsonObject)?.get(name)?.jsonPrimitive?.content
            val candidate = variants.firstOrNull { attr(it, "org.jetbrains.kotlin.platform.type") == "androidJvm" && attr(it, "org.gradle.usage") == "java-runtime" }
                ?: variants.firstOrNull { attr(it, "org.jetbrains.kotlin.platform.type") == "jvm" && attr(it, "org.gradle.usage") == "java-runtime" }
            val availableAt = candidate?.get("available-at") as? JsonObject ?: continue
            val g = availableAt["group"]?.jsonPrimitive?.content ?: continue
            val m = availableAt["module"]?.jsonPrimitive?.content ?: continue
            val v = availableAt["version"]?.jsonPrimitive?.content ?: continue
            result = MavenCoordinate(g, m, v)
            break
        }
        redirectCache[key] = result
        if (result != null || reachedAnyRepository) {
            runCatching { marker.parentFile?.mkdirs(); marker.writeText(result?.toString() ?: "-") }
        }
        return result
    }

    private suspend fun downloadArtifact(
        coordinate: MavenCoordinate,
        repositories: List<MavenRepository>,
        onEvent: (ResolveEvent) -> Unit,
    ): ResolvedArtifact? {
        val (pom, repo) = effectivePom(coordinate, repositories) ?: throw IOException("POM not found for $coordinate")
        val dependencies = runtimeDependencies(pom).mapNotNull { d -> d.version?.let { MavenCoordinate(d.group, d.artifact, it) } }
        val preferredExt = when (pom.packaging) {
            "aar" -> listOf("aar", "jar")
            "pom" -> emptyList()
            "bundle", "jar", "maven-plugin", "eclipse-plugin" -> listOf("jar", "aar")
            else -> listOf(pom.packaging, "jar", "aar")
        }
        val orderedRepos = listOf(repo) + repositories.filter { it != repo }
        // Check every cached extension before touching the network, so warm builds stay offline.
        for (ext in preferredExt) {
            val local = File(cacheRoot, coordinate.path + "/${coordinate.baseFileName}.$ext")
            if (local.isFile && local.length() > 0) {
                onEvent(ResolveEvent.CacheHit(coordinate))
                return ResolvedArtifact(coordinate, typeOf(ext), local, repo, dependencies)
            }
        }
        for (ext in preferredExt) {
            val fileName = "${coordinate.baseFileName}.$ext"
            val local = File(cacheRoot, coordinate.path + "/" + fileName)
            for (r in orderedRepos) {
                val bytes = runCatching { http.download(http.artifactUrl(r, coordinate.path + "/" + fileName), local) }.getOrNull() ?: continue
                onEvent(ResolveEvent.Downloaded(coordinate, r.name, bytes))
                return ResolvedArtifact(coordinate, typeOf(ext), local, r, dependencies)
            }
        }
        // KMP root module or BOM: no binary of its own.
        val variant = androidVariantOf(coordinate, repositories)
        if (variant != null && variant != coordinate) {
            return downloadArtifact(variant, repositories, onEvent)?.let { it.copy(dependencies = it.dependencies + dependencies) }
        }
        return if (pom.packaging == "pom") ResolvedArtifact(coordinate, ArtifactType.PomOnly, null, repo, dependencies)
        else throw IOException("No .jar or .aar published for $coordinate")
    }

    private fun typeOf(ext: String) = if (ext == "aar") ArtifactType.Aar else ArtifactType.Jar

    /** Clears in-memory caches (the on-disk cache is kept). */
    fun clearMemoryCaches() {
        pomCache.clear()
        rawCache.clear()
        redirectCache.clear()
    }

    /** Total size of the local artifact cache in bytes. */
    fun cacheSize(): Long = cacheRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearDiskCache() {
        cacheRoot.deleteRecursively()
        clearMemoryCaches()
    }

    private companion object {
        const val MAX_ITERATIONS = 6
        const val MAX_DEPTH = 40
    }
}

