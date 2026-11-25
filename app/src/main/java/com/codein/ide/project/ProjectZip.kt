package com.codein.ide.project

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Packs a project into a ZIP to share or back up, and unpacks one into a new project folder.
 * Build outputs, caches, Codein's own `.codein` folder and machine-specific files are left out.
 */
object ProjectZip {

    private val skippedDirs = setOf("build", ".gradle", ".idea", ".kotlin", ".cxx", ".codein")
    private val skippedFiles = setOf("local.properties")

    /** Writes [projectDir] into [zip] under a top-level folder named after the project; returns the file count. */
    fun export(projectDir: File, zip: File, includeGit: Boolean = true): Int {
        var count = 0
        zip.parentFile?.mkdirs()
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            val top = projectDir.name
            projectDir.walkTopDown()
                .onEnter { dir -> dir == projectDir || (dir.name !in skippedDirs && (includeGit || dir.name != ".git")) }
                .filter { it.isFile && it.name !in skippedFiles && !it.name.endsWith(".codein-tmp") }
                .forEach { file ->
                    val rel = file.relativeTo(projectDir).invariantSeparatorsPath
                    out.putNextEntry(ZipEntry("$top/$rel").apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                    count++
                }
        }
        return count
    }

    /**
     * Unpacks [input] into [target]. When every entry sits under one top-level folder (the usual
     * shape of a zipped project or a GitHub download) that folder is stripped. Entries that would
     * land outside [target] are rejected.
     */
    fun import(input: InputStream, target: File): Int {
        val staging = File(target.parentFile, ".${target.name}.unzip").apply { deleteRecursively(); mkdirs() }
        var count = 0
        try {
            ZipInputStream(input.buffered()).use { zip ->
                val root = staging.canonicalPath + File.separator
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val out = File(staging, entry.name)
                    if (!out.canonicalPath.startsWith(root)) throw SecurityException("Unsafe path in ZIP: ${entry.name}")
                    if (entry.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                        count++
                    }
                    zip.closeEntry()
                }
            }
            val children = staging.listFiles().orEmpty().filter { it.name != "__MACOSX" }
            val source = if (children.size == 1 && children[0].isDirectory) children[0] else staging
            target.deleteRecursively()
            if (!source.renameTo(target)) {
                source.copyRecursively(target, overwrite = true)
            }
        } finally {
            staging.deleteRecursively()
        }
        return count
    }

    /** True when [dir] holds a Gradle Android project. */
    fun looksLikeProject(dir: File): Boolean =
        File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists() || File(dir, "app").isDirectory
}
