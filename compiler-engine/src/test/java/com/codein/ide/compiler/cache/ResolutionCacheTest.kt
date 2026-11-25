package com.codein.ide.compiler.cache

import com.codein.ide.maven.model.ArtifactType
import com.codein.ide.maven.model.MavenCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResolutionCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun roundTripsAndInvalidatesOnMissingFiles() {
        val cache = ResolutionCache(File(tmp.root, "cache"))
        val jar = tmp.newFile("a.jar")
        val aar = tmp.newFile("b.aar")
        val key = cache.key(listOf("g:a:1.0", "g:b:2.0"), listOf("g:bom:3"), listOf("https://maven.google.com"))!!
        val artifacts = listOf(
            CachedArtifact(MavenCoordinate.parse("g:a:1.0"), ArtifactType.Jar, jar),
            CachedArtifact(MavenCoordinate.parse("g:b:2.0@aar"), ArtifactType.Aar, aar),
            CachedArtifact(MavenCoordinate.parse("g:p:1"), ArtifactType.PomOnly, null),
        )
        cache.save(key, artifacts)
        assertEquals(artifacts, cache.load(key))
        aar.delete()
        assertNull(cache.load(key))
    }

    @Test
    fun keyDependsOnInputsAndSkipsDynamicVersions() {
        val cache = ResolutionCache(tmp.root)
        val a = cache.key(listOf("g:a:1.0", "g:b:2.0"), emptyList(), listOf("r"))
        assertEquals(a, cache.key(listOf("g:b:2.0", "g:a:1.0"), emptyList(), listOf("r")))
        assertNotEquals(a, cache.key(listOf("g:a:1.1", "g:b:2.0"), emptyList(), listOf("r")))
        assertNotEquals(a, cache.key(listOf("g:a:1.0", "g:b:2.0"), emptyList(), listOf("r2")))
        assertNull(cache.key(listOf("g:a:1.+"), emptyList(), listOf("r")))
        assertNull(cache.key(listOf("g:a:1.0-SNAPSHOT"), emptyList(), listOf("r")))
        assertNull(cache.key(listOf("g:a:[1.0,2.0)"), emptyList(), listOf("r")))
    }
}
