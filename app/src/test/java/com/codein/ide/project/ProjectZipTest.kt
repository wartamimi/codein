package com.codein.ide.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectZipTest {

    @Test
    fun exportSkipsBuildOutputsAndImportRestoresTheProject() {
        val tmp = Files.createTempDirectory("zip").toFile()
        val project = File(tmp, "MyApp").apply { mkdirs() }
        File(project, "settings.gradle.kts").writeText("rootProject.name = \"MyApp\"")
        File(project, "app/src/main/java/A.kt").apply { parentFile.mkdirs(); writeText("class A") }
        File(project, "app/build/outputs/app.apk").apply { parentFile.mkdirs(); writeText("apk") }
        File(project, ".codein/history/x/1.txt").apply { parentFile.mkdirs(); writeText("old") }
        File(project, "local.properties").writeText("sdk.dir=/x")

        val zip = File(tmp, "out/MyApp.zip")
        assertEquals(2, ProjectZip.export(project, zip))

        val target = File(tmp, "Imported")
        assertEquals(2, ProjectZip.import(zip.inputStream(), target))
        assertEquals("class A", File(target, "app/src/main/java/A.kt").readText())
        assertTrue(ProjectZip.looksLikeProject(target))
        assertFalse(File(target, "app/build").exists())
    }

    @Test(expected = SecurityException::class)
    fun rejectsEntriesOutsideTheTarget() {
        val bytes = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z -> z.putNextEntry(ZipEntry("../evil.txt")); z.write(1); z.closeEntry() }
        }.toByteArray()
        val tmp = Files.createTempDirectory("zip").toFile()
        ProjectZip.import(bytes.inputStream(), File(tmp, "t"))
    }
}
