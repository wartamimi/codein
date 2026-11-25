package com.codein.ide.workspace

import java.io.File
import java.util.zip.ZipFile

/** One finished build, as listed in the build console's History tab. */
data class BuildRecord(
    val time: Long,
    val variant: String,
    val outcome: Outcome,
    val durationMillis: Long,
    val errors: Int,
    val warnings: Int,
    /** Size of the APK/AAB in bytes, or 0 when the build made none. */
    val outputBytes: Long,
    val summary: String,
    val outputPath: String? = null,
) {
    enum class Outcome { Success, Failure, Cancelled }
}

/**
 * Keeps the last [MAX_RECORDS] builds of a project in `<project>/.codein/builds.tsv`, so build
 * times, failures and APK growth can be compared over time.
 */
object BuildHistory {
    const val MAX_RECORDS = 50

    private fun file(projectDir: File) = File(projectDir, ".codein/builds.tsv")

    fun load(projectDir: File): List<BuildRecord> {
        val f = file(projectDir)
        if (!f.isFile) return emptyList()
        return f.readLines().mapNotNull(::parse).sortedByDescending { it.time }
    }

    fun append(projectDir: File, record: BuildRecord): List<BuildRecord> {
        val all = (listOf(record) + load(projectDir)).take(MAX_RECORDS)
        val f = file(projectDir)
        f.parentFile?.mkdirs()
        File(projectDir, ".codein/.gitignore").takeIf { !it.exists() }?.writeText("*\n")
        f.writeText(all.joinToString("\n", postfix = "\n") { format(it) })
        return all
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun format(r: BuildRecord): String = listOf(
        r.time, clean(r.variant), r.outcome.name, r.durationMillis, r.errors, r.warnings, r.outputBytes, clean(r.summary), clean(r.outputPath.orEmpty()),
    ).joinToString("\t")

    fun parse(line: String): BuildRecord? {
        val p = line.split('\t')
        if (p.size < 8) return null
        return BuildRecord(
            time = p[0].toLongOrNull() ?: return null,
            variant = p[1],
            outcome = runCatching { BuildRecord.Outcome.valueOf(p[2]) }.getOrNull() ?: return null,
            durationMillis = p[3].toLongOrNull() ?: 0,
            errors = p[4].toIntOrNull() ?: 0,
            warnings = p[5].toIntOrNull() ?: 0,
            outputBytes = p[6].toLongOrNull() ?: 0,
            summary = p[7],
            outputPath = p.getOrNull(8)?.takeIf { it.isNotBlank() },
        )
    }
}

/** What takes space in an APK or AAB, by kind of content, plus the largest single entries. */
data class ApkSize(val totalBytes: Long, val categories: List<Category>, val largest: List<Entry>) {
    data class Category(val name: String, val bytes: Long, val files: Int)
    data class Entry(val path: String, val bytes: Long)
}

object ApkSizeAnalyzer {

    /** Groups the archive's entries by compressed size into code, resources, native libraries and so on. */
    fun analyze(archive: File): ApkSize? = runCatching {
        ZipFile(archive).use { zip ->
            val sizes = LinkedHashMap<String, Pair<Long, Int>>()
            val entries = ArrayList<ApkSize.Entry>()
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                val bytes = if (e.compressedSize >= 0) e.compressedSize else e.size.coerceAtLeast(0)
                val path = e.name.removePrefix("base/")
                val category = categoryOf(path)
                val (b, n) = sizes[category] ?: (0L to 0)
                sizes[category] = (b + bytes) to (n + 1)
                entries += ApkSize.Entry(e.name, bytes)
            }
            ApkSize(
                totalBytes = archive.length(),
                categories = sizes.map { (name, v) -> ApkSize.Category(name, v.first, v.second) }.sortedByDescending { it.bytes },
                largest = entries.sortedByDescending { it.bytes }.take(10),
            )
        }
    }.getOrNull()

    fun categoryOf(path: String): String = when {
        path.endsWith(".dex") || path.startsWith("dex/") -> "Code (dex)"
        path.startsWith("lib/") -> "Native libraries"
        path == "resources.arsc" || path == "resources.pb" -> "Resource table"
        path.startsWith("res/") -> "Resources"
        path.startsWith("assets/") -> "Assets"
        path.startsWith("META-INF/") -> "Signature & metadata"
        path.startsWith("kotlin/") -> "Kotlin metadata"
        path.endsWith("AndroidManifest.xml") -> "Manifest"
        else -> "Other"
    }

    fun human(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
