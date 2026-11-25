package com.codein.ide.signer.bundle

import com.android.tools.build.bundletool.commands.BuildBundleCommand
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Inputs for a single base module of an App Bundle. */
data class BundleModuleInputs(
    /** Output of `aapt2 link --proto-format` (contains proto AndroidManifest.xml, resources.pb, res/). */
    val protoResourcesApk: File,
    val dexFiles: List<File>,
    val nativeLibDirs: List<File> = emptyList(),
    val assetsDirs: List<File> = emptyList(),
    /** Java resources (non-class files from jars) placed under `root/`. */
    val javaResources: Map<String, File> = emptyMap(),
)

/**
 * Builds Play Store-ready `.aab` files with Google's bundletool:
 * 1. Re-layouts the proto-format resource APK into the module zip format
 *    (`manifest/`, `dex/`, `res/`, `resources.pb`, `assets/`, `lib/`, `root/`).
 * 2. Runs `BuildBundleCommand` to produce the bundle (signing is done afterwards by
 *    [com.codein.ide.signer.apk.ApkSignerService.signBundle]).
 */
@Singleton
class AabBuilder @Inject constructor() {

    suspend fun build(inputs: BundleModuleInputs, workDir: File, outputAab: File): File = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val moduleZip = File(workDir, "base.zip")
        createModuleZip(inputs, moduleZip)
        outputAab.parentFile?.mkdirs()
        if (outputAab.exists()) outputAab.delete()
        BuildBundleCommand.builder()
            .setModulesPaths(ImmutableList.of(moduleZip.toPath()))
            .setOutputPath(outputAab.toPath())
            .setOverwriteOutput(true)
            .build()
            .execute()
        outputAab
    }

    fun createModuleZip(inputs: BundleModuleInputs, moduleZip: File) {
        val written = mutableSetOf<String>()
        ZipOutputStream(moduleZip.outputStream().buffered()).use { out ->
            fun put(name: String, bytes: ByteArray) {
                if (!written.add(name)) return
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
            ZipFile(inputs.protoResourcesApk).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val target = when {
                        entry.name == "AndroidManifest.xml" -> "manifest/AndroidManifest.xml"
                        entry.name == "resources.pb" -> "resources.pb"
                        entry.name.startsWith("res/") -> entry.name
                        entry.name.startsWith("assets/") -> entry.name
                        entry.name.startsWith("lib/") -> entry.name
                        entry.name.endsWith(".dex") -> "dex/" + entry.name
                        else -> "root/" + entry.name
                    }
                    put(target, bytes)
                }
            }
            inputs.dexFiles.sortedBy { it.name }.forEach { put("dex/${it.name}", it.readBytes()) }
            inputs.assetsDirs.filter { it.isDirectory }.forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { f -> put("assets/" + f.relativeTo(dir).invariantSeparatorsPath, f.readBytes()) }
            }
            inputs.nativeLibDirs.filter { it.isDirectory }.forEach { dir ->
                dir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { f ->
                    put("lib/" + f.relativeTo(dir).invariantSeparatorsPath, f.readBytes())
                }
            }
            inputs.javaResources.forEach { (path, file) -> put("root/$path", file.readBytes()) }
        }
    }
}
