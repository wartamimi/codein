package com.codein.ide.terminal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TerminalCompletionTest {

    @Test
    fun completesUniqueAndCommonPrefixes() {
        val root = Files.createTempDirectory("term").toFile()
        File(root, "app/src/main").mkdirs()
        File(root, "build.gradle.kts").writeText("")
        File(root, "build-notes.txt").writeText("")

        assertEquals("ls app/", TerminalCompletion.complete("ls a", root).text)
        assertEquals("ls app/src/", TerminalCompletion.complete("ls app/s", root).text)
        val ambiguous = TerminalCompletion.complete("cat bu", root)
        assertEquals("cat build", ambiguous.text)
        assertEquals(listOf("build-notes.txt", "build.gradle.kts"), ambiguous.candidates)
        assertEquals("cat build.gradle.kts ", TerminalCompletion.complete("cat build.", root).text)
        assertEquals("cat zz", TerminalCompletion.complete("cat zz", root).text)
    }

    @Test
    fun followsCd() {
        val root = Files.createTempDirectory("term").toFile()
        File(root, "app").mkdirs()
        val inApp = TerminalCompletion.afterCommand("cd app", root, root)
        assertEquals(File(root, "app").normalize(), inApp)
        assertEquals(root.normalize(), TerminalCompletion.afterCommand("cd ..", inApp, root))
        assertEquals(root, TerminalCompletion.afterCommand("cd missing", root, root))
        assertEquals(root, TerminalCompletion.afterCommand("cd", inApp, root))
    }
}
