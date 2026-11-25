package com.codein.ide.compiler.cache

import com.codein.ide.maven.model.ArtifactType
import com.codein.ide.maven.model.MavenCoordinate
import java.io.File
import java.security.MessageDigest

/** One resolved artifact as remembered between builds. */
data class CachedArtifact(val coordinate: MavenCoordinate, val type: ArtifactType, val file: File?)

/**
 * Remembers the result of Maven resolution for a given set of declared dependencies, so a
 * rebuild with unchanged dependencies skips reading hundreds of POMs (and any network lookups)
 * and goes straight to the jars and AARs already on disk.
 *
 * The key covers the declared coordinates, BOMs and repositories. Dynamic versions (`+`,
 * ranges, `latest.*`, `-SNAPSHOT`) are never cached, and an entry is ignored when any of its
 * files has disappeared.
 */
class ResolutionCache(private val dir: File) {

    fun key(declared: List<String>, platforms: List<String>, repositories: List<String>): String? {
        val all = declared + platforms
        if (all.any(::isDynamic)) return null
        val text = buildString {
            append("v1\n")
            declared.sorted().forEach { append("d ").append(it).append('\n') }
            platforms.sorted().forEach { append("p ").append(it).append('\n') }
            repositories.forEach { append("r ").append(it).append('\n') }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun load(key: String): List<CachedArtifact>? {
        val file = File(dir, "$key.tsv")
        if (!file.isFile) return null
        val artifacts = runCatching {
            file.readLines().filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                require(parts.size == 3) { "Bad cache line" }
                CachedArtifact(MavenCoordinate.parse(parts[1]), ArtifactType.valueOf(parts[0]), parts[2].takeIf { it.isNotEmpty() }?.let(::File))
            }
        }.getOrNull() ?: return null
        if (artifacts.any { it.type != ArtifactType.PomOnly && it.file?.isFile != true }) return null
        return artifacts
    }

    fun save(key: String, artifacts: List<CachedArtifact>) {
        dir.mkdirs()
        val tmp = File(dir, "$key.tmp")
        tmp.writeText(artifacts.joinToString("\n") { "${it.type.name}\t${it.coordinate}\t${it.file?.absolutePath.orEmpty()}" })
        tmp.renameTo(File(dir, "$key.tsv"))
    }

    private fun isDynamic(coordinate: String): Boolean {
        val version = coordinate.substringBefore('@').split(':').getOrNull(2) ?: return true
        return version.isBlank() || version.contains('+') || version.startsWith("[") || version.startsWith("(") ||
            version.startsWith("latest.") || version.endsWith("-SNAPSHOT")
    }
}
