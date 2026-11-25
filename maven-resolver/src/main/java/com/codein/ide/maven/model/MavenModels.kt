package com.codein.ide.maven.model

import java.io.File

data class MavenRepository(val id: String, val name: String, val url: String) {
    val baseUrl: String get() = url.trimEnd('/')

    companion object {
        val Google = MavenRepository("google", "Google Maven", "https://maven.google.com")
        val Central = MavenRepository("central", "Maven Central", "https://repo1.maven.org/maven2")
        val JitPack = MavenRepository("jitpack", "JitPack", "https://jitpack.io")
        val Defaults = listOf(Google, Central, JitPack)
    }
}

/** `group:artifact:version[:classifier][@extension]`. */
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String? = null,
    val extension: String? = null,
) {
    val groupArtifact: String get() = "$group:$artifact"
    val path: String get() = group.replace('.', '/') + "/" + artifact + "/" + version
    val baseFileName: String get() = "$artifact-$version" + (classifier?.let { "-$it" } ?: "")

    override fun toString(): String = buildString {
        append(group).append(':').append(artifact).append(':').append(version)
        if (classifier != null) append(':').append(classifier)
        if (extension != null) append('@').append(extension)
    }

    fun withVersion(newVersion: String) = copy(version = newVersion)

    companion object {
        fun parse(text: String): MavenCoordinate {
            val (base, ext) = text.trim().split('@').let { it[0] to it.getOrNull(1) }
            val parts = base.split(':')
            require(parts.size >= 3) { "Invalid Maven coordinate '$text' (expected group:artifact:version)" }
            return MavenCoordinate(parts[0], parts[1], parts[2], parts.getOrNull(3), ext)
        }
    }
}

enum class ArtifactType { Jar, Aar, PomOnly }

/** A downloaded artifact on disk. */
data class ResolvedArtifact(
    val coordinate: MavenCoordinate,
    val type: ArtifactType,
    val file: File?,
    val repository: MavenRepository,
    val dependencies: List<MavenCoordinate>,
)

/** A node in the dependency tree for the explorer UI. */
data class DependencyNode(
    val coordinate: MavenCoordinate,
    val requestedVersion: String,
    val children: List<DependencyNode>,
    val conflictResolved: Boolean,
)

data class ResolutionResult(
    val artifacts: List<ResolvedArtifact>,
    val roots: List<DependencyNode>,
    val failures: List<ResolutionFailure>,
) {
    val jars: List<File> get() = artifacts.filter { it.type == ArtifactType.Jar }.mapNotNull { it.file }
    val aars: List<ResolvedArtifact> get() = artifacts.filter { it.type == ArtifactType.Aar }
}

data class ResolutionFailure(val coordinate: MavenCoordinate, val reason: String)

sealed interface ResolveEvent {
    data class Resolving(val coordinate: MavenCoordinate) : ResolveEvent
    data class Downloaded(val coordinate: MavenCoordinate, val repository: String, val bytes: Long) : ResolveEvent
    data class CacheHit(val coordinate: MavenCoordinate) : ResolveEvent
    data class Failed(val coordinate: MavenCoordinate, val reason: String) : ResolveEvent
    data class VersionConflict(val groupArtifact: String, val versions: List<String>, val selected: String) : ResolveEvent
}

/** A search hit from an online repository. */
data class SearchHit(
    val group: String,
    val artifact: String,
    val latestVersion: String,
    val repository: MavenRepository,
    val packaging: String? = null,
    val versionCount: Int? = null,
    /** Plain-language description for well-known libraries matched by name. */
    val description: String? = null,
) {
    val coordinate: MavenCoordinate get() = MavenCoordinate(group, artifact, latestVersion)
}
