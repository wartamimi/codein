package com.codein.ide.compiler.packaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class PackagingInputs(
    /** `aapt2 link` output (binary format): AndroidManifest.xml, resources.arsc, res/, assets/. */
    val resourcesApk: File,
    val dexFiles: List<File>,
    /** Directories laid out as `<abi>/lib*.so` (e.g. `src/main/jniLibs`, AAR `jni/`). */
    val nativeLibDirs: List<File>,
    /** Runtime jars whose non-class resources (e.g. `META-INF/services`) must ship in the APK. */
    val javaResourceJars: List<File>,
    val abiFilters: Set<String> = emptySet(),
)

/**
 * Assembles an unsigned APK. `resources.arsc` and native libraries are STORED (uncompressed) so
 * that they can be memory-mapped and page-aligned by [com.codein.ide.signer.apk.ZipAligner];
 * everything else is DEFLATED.
 */
@Singleton
class ApkPackager @Inject constructor() {

    suspend fun packageApk(inputs: PackagingInputs, output: File): File = withContext(Dispatchers.IO) {
        output.parentFile?.mkdirs()
        val written = mutableSetOf<String>()
        ZipOutputStream(output.outputStream().buffered()).use { out ->
            fun put(name: String, data: ByteArray, store: Boolean) {
                if (!written.add(name)) return
                val entry = ZipEntry(name)
                if (store) {
                    entry.method = ZipEntry.STORED
                    entry.size = data.size.toLong()
                    entry.compressedSize = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                } else {
                    entry.method = ZipEntry.DEFLATED
                }
                out.putNextEntry(entry)
                out.write(data)
                out.closeEntry()
            }

            // 1. Manifest + resources from aapt2 (manifest must be first for some tools).
            ZipFile(inputs.resourcesApk).use { zip ->
                val entries = zip.entries().toList().filterNot { it.isDirectory }
                    .sortedBy { if (it.name == "AndroidManifest.xml") 0 else 1 }
                for (e in entries) {
                    val data = zip.getInputStream(e).use { it.readBytes() }
                    val store = e.name == "resources.arsc" || e.method == ZipEntry.STORED
                    put(e.name, data, store)
                }
            }

            // 2. DEX files.
            inputs.dexFiles.sortedWith(compareBy({ it.name.length }, { it.name })).forEach { dex ->
                put(dex.name, dex.readBytes(), store = false)
            }

            // 3. Native libraries.
            for (dir in inputs.nativeLibDirs.filter { it.isDirectory }) {
                dir.listFiles().orEmpty().filter { it.isDirectory }.forEach { abiDir ->
                    if (inputs.abiFilters.isNotEmpty() && abiDir.name !in inputs.abiFilters) return@forEach
                    abiDir.listFiles { f -> f.isFile && f.extension == "so" }.orEmpty().forEach { so ->
                        put("lib/${abiDir.name}/${so.name}", so.readBytes(), store = true)
                    }
                }
            }

            // 4. Java resources from runtime jars.
            for (jar in inputs.javaResourceJars.filter { it.isFile }) {
                runCatching {
                    ZipFile(jar).use { zip ->
                        for (e in zip.entries()) {
                            if (e.isDirectory || !isJavaResource(e.name)) continue
                            put(e.name, zip.getInputStream(e).use { it.readBytes() }, store = false)
                        }
                    }
                }
            }
        }
        output
    }

    /** Collects Java resources from jars as a path → extracted file map (used for AAB `root/`). */
    suspend fun extractJavaResources(jars: List<File>, targetDir: File): Map<String, File> = withContext(Dispatchers.IO) {
        targetDir.deleteRecursively()
        val result = linkedMapOf<String, File>()
        for (jar in jars.filter { it.isFile }) {
            runCatching {
                ZipFile(jar).use { zip ->
                    for (e in zip.entries()) {
                        if (e.isDirectory || !isJavaResource(e.name) || result.containsKey(e.name)) continue
                        val out = File(targetDir, e.name)
                        if (!out.canonicalPath.startsWith(targetDir.canonicalPath)) continue
                        out.parentFile?.mkdirs()
                        zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                        result[e.name] = out
                    }
                }
            }
        }
        result
    }

    private fun isJavaResource(name: String): Boolean {
        if (name.endsWith(".class")) return false
        if (name.endsWith(".java") || name.endsWith(".kt")) return false
        val upper = name.uppercase()
        if (upper.startsWith("META-INF/")) {
            if (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")) return false
            if (upper == "META-INF/MANIFEST.MF" || upper.startsWith("META-INF/VERSIONS/") || upper.startsWith("META-INF/MAVEN/")) return false
            if (upper.contains("LICENSE") || upper.contains("NOTICE") || upper.endsWith(".KOTLIN_MODULE")) return false
            if (upper == "META-INF/INDEX.LIST" || upper == "META-INF/DEPENDENCIES") return false
        }
        if (name == "module-info.class" || name.endsWith("/module-info.class")) return false
        if (name.startsWith("LICENSE") || name == "NOTICE" || name.endsWith(".proto")) return false
        return true
    }
}
