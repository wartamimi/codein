package com.codein.ide.workspace

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalHistoryTest {

    @Test
    fun keepsDistinctVersionsNewestFirstAndPrunes() {
        val root = Files.createTempDirectory("hist").toFile()
        val file = File(root, "app/src/Main.kt").apply { parentFile.mkdirs(); writeText("v1") }
        LocalHistory.record(root, file, "v1", now = 1000)
        LocalHistory.record(root, file, "v1", now = 2000)
        LocalHistory.record(root, file, "v2", now = 3000)
        assertEquals(listOf("v2", "v1"), LocalHistory.versions(root, file).map { it.read() })

        repeat(LocalHistory.MAX_VERSIONS + 5) { LocalHistory.record(root, file, "x$it", now = 10_000L + it) }
        val versions = LocalHistory.versions(root, file)
        assertEquals(LocalHistory.MAX_VERSIONS, versions.size)
        assertEquals("x${LocalHistory.MAX_VERSIONS + 4}", versions.first().read())
    }

    @Test
    fun historyFollowsMoves() {
        val root = Files.createTempDirectory("hist").toFile()
        val from = File(root, "a/One.kt").apply { parentFile.mkdirs(); writeText("one") }
        LocalHistory.record(root, from)
        val to = File(root, "b/One.kt")
        LocalHistory.moved(root, from, to)
        assertEquals(listOf("one"), LocalHistory.versions(root, to).map { it.read() })
        assertEquals(emptyList<String>(), LocalHistory.versions(root, from).map { it.read() })
    }
}
