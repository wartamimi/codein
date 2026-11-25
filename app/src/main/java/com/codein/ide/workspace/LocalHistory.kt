package com.codein.ide.workspace

import java.io.File
import java.security.MessageDigest

/**
 * Earlier versions of project files, kept by Codein whenever it overwrites a file (save, project
 * replace, AI diff). Versions live in `<project>/.codein/history/<path hash>/<millis>.txt`, at most
 * [MAX_VERSIONS] per file, so a bad edit can be undone even without Git.
 */
object LocalHistory {
    const val MAX_VERSIONS = 30
    private const val MAX_BYTES = 1_000_000L

    data class Version(val file: File, val time: Long) {
        fun read(): String = file.readText()
    }

    /** Keeps the text currently on disk at [file] as a version, unless it equals the newest one. */
    fun record(projectDir: File, file: File) {
        if (!file.isFile || file.length() > MAX_BYTES) return
        val text = runCatching { file.readText() }.getOrNull() ?: return
        record(projectDir, file, text)
    }

    fun record(projectDir: File, file: File, text: String, now: Long = System.currentTimeMillis()) {
        val dir = dirFor(projectDir, file)
        val newest = versions(projectDir, file).firstOrNull()
        if (newest != null && runCatching { newest.read() }.getOrNull() == text) return
        dir.mkdirs()
        // Keeps the history out of Git without touching the project's own .gitignore.
        File(projectDir, ".codein/.gitignore").takeIf { !it.exists() }?.writeText("*\n")
        File(dir, "path").takeIf { !it.exists() }?.writeText(file.relativeTo(projectDir).invariantSeparatorsPath)
        var time = now
        while (File(dir, "$time.txt").exists()) time++
        File(dir, "$time.txt").writeText(text)
        versions(projectDir, file).drop(MAX_VERSIONS).forEach { it.file.delete() }
    }

    /** Versions of [file], newest first. */
    fun versions(projectDir: File, file: File): List<Version> =
        dirFor(projectDir, file).listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
            .mapNotNull { f -> f.name.removeSuffix(".txt").toLongOrNull()?.let { Version(f, it) } }
            .sortedByDescending { it.time }

    /** Moves a file's history along with a rename or move. */
    fun moved(projectDir: File, from: File, to: File) {
        val old = dirFor(projectDir, from)
        if (!old.isDirectory) return
        val target = dirFor(projectDir, to)
        target.parentFile?.mkdirs()
        if (!target.exists() && old.renameTo(target)) {
            File(target, "path").writeText(to.relativeTo(projectDir).invariantSeparatorsPath)
        }
    }

    private fun dirFor(projectDir: File, file: File): File {
        val rel = file.relativeTo(projectDir).invariantSeparatorsPath
        val hash = MessageDigest.getInstance("SHA-1").digest(rel.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        return File(projectDir, ".codein/history/$hash")
    }
}
