package com.codein.ide.ai.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectRetrieverTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun findsTheFilesAQuestionIsAbout() {
        val root = tmp.root
        File(root, "app/src/main/kotlin/a/LoginScreen.kt").apply { parentFile!!.mkdirs() }.writeText("fun LoginScreen() { Button(onClick = {}) { Text(\"Login\") } }")
        File(root, "app/src/main/kotlin/a/Settings.kt").apply { parentFile!!.mkdirs() }.writeText("fun Settings() {}")
        File(root, "app/build/tmp/LoginScreen.kt").apply { parentFile!!.mkdirs() }.writeText("generated login")
        val files = ProjectRetriever.relevantFiles(root, "Di mana tombol login dibuat?")
        assertEquals(listOf("app/src/main/kotlin/a/LoginScreen.kt"), files.map { it.first })
    }

    @Test
    fun splitsIdentifiers() {
        val words = ProjectRetriever.keywords("Why does submitButton in activity_main crash?")
        assertTrue("submit" in words && "button" in words && "submitbutton" in words && "activity" in words)
        assertTrue("why" !in words)
    }
}
