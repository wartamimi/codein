package com.codein.ide.workspace

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BuildHistoryTest {

    @Test
    fun appendsNewestFirstAndKeepsTheLimit() {
        val root = Files.createTempDirectory("builds").toFile()
        repeat(BuildHistory.MAX_RECORDS + 3) { i ->
            BuildHistory.append(root, BuildRecord(1000L + i, "debug\tAPK", BuildRecord.Outcome.Success, 8000, 0, 2, 4_200_000, "app-debug.apk\nok", "/x/app-debug.apk"))
        }
        val loaded = BuildHistory.load(root)
        assertEquals(BuildHistory.MAX_RECORDS, loaded.size)
        assertEquals(1000L + BuildHistory.MAX_RECORDS + 2, loaded.first().time)
        assertEquals("debug APK", loaded.first().variant)
        assertEquals("app-debug.apk ok", loaded.first().summary)
        assertEquals("/x/app-debug.apk", loaded.first().outputPath)
    }

    @Test
    fun groupsApkEntriesBySize() {
        val apk = File(Files.createTempDirectory("apk").toFile(), "a.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            fun put(name: String, size: Int) {
                zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.STORED; this.size = size.toLong(); compressedSize = size.toLong(); crc = java.util.zip.CRC32().apply { update(ByteArray(size)) }.value })
                zip.write(ByteArray(size)); zip.closeEntry()
            }
            put("classes.dex", 5000)
            put("classes2.dex", 1000)
            put("res/layout/a.xml", 300)
            put("resources.arsc", 700)
            put("lib/arm64-v8a/libx.so", 2000)
        }
        val size = ApkSizeAnalyzer.analyze(apk)!!
        assertEquals("Code (dex)", size.categories.first().name)
        assertEquals(6000L, size.categories.first().bytes)
        assertEquals(2, size.categories.first().files)
        assertEquals("classes.dex", size.largest.first().path)
        assertEquals("1.5 MB", ApkSizeAnalyzer.human(1_572_864))
    }
}
