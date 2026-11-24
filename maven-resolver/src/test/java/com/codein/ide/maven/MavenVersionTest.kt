package com.codein.ide.maven

import com.codein.ide.maven.gradle.GradleDependencyWriter
import com.codein.ide.maven.model.MavenCoordinate
import com.codein.ide.maven.resolve.MavenVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenVersionTest {
    @Test
    fun ordersVersions() {
        assertTrue(MavenVersion.compare("1.10.0", "1.9.9") > 0)
        assertTrue(MavenVersion.compare("1.0.0-alpha01", "1.0.0") < 0)
        assertTrue(MavenVersion.compare("1.0.0-rc01", "1.0.0-beta02") > 0)
        assertEquals(0, MavenVersion.compare("1.0", "1.0.0"))
    }

    @Test
    fun selectsDynamicVersions() {
        val available = listOf("1.0.0", "1.1.0", "1.2.0-alpha01", "2.0.0")
        assertEquals("2.0.0", MavenVersion.select("+", available))
        assertEquals("1.1.0", MavenVersion.select("1.+", available))
        assertEquals("1.1.0", MavenVersion.select("[1.0,2.0)", available))
    }

    @Test
    fun writesDependencies() {
        val writer = GradleDependencyWriter()
        val text = "plugins {}\n\ndependencies {\n    implementation(\"a:b:1.0\")\n}\n"
        val (added, _) = writer.addDependencyToText(text, MavenCoordinate("c", "d", "2.0"), "implementation", isKts = true)
        assertTrue(added.contains("implementation(\"c:d:2.0\")"))
        val (updated, result) = writer.addDependencyToText(text, MavenCoordinate("a", "b", "1.5"), "implementation", isKts = true)
        assertTrue(updated.contains("a:b:1.5"))
        assertTrue(result is GradleDependencyWriter.WriteResult.Updated)
    }
}
