package com.codein.ide.maven.aar

import com.codein.ide.maven.model.MavenCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** The exploded contents of an `.aar`. */
data class ExtractedAar(
    val coordinate: MavenCoordinate,
    val root: File,
    val packageName: String,
    val manifest: File,
    val classesJar: File?,
    val libsJars: List<File>,
    val resDir: File?,
    val assetsDir: File?,
    val jniDir: File?,
    val rTxt: File?,
    val proguardRules: File?,
    val lintJar: File?,
) {
    /** All jars contributing classes at compile time and runtime. */
    val allJars: List<File> get() = listOfNotNull(classesJar) + libsJars
    val hasResources: Boolean get() = resDir?.listFiles()?.isNotEmpty() == true
}

/**
 * Unpacks Android Archive (`.aar`) files into `exploded-aar/<group>/<artifact>/<version>/`.
 * Extraction is idempotent and keyed on the archive's size/mtime.
 */
@Singleton
class AarUnpacker @Inject constructor() {

    suspend fun unpack(aar: File, coordinate: MavenCoordinate, explodedRoot: File): ExtractedAar = withContext(Dispatchers.IO) {
        val target = File(explodedRoot, coordinate.path)
        val stamp = File(target, ".codein-stamp")
        val expectedStamp = "${aar.length()}:${aar.lastModified()}"
        if (!(stamp.isFile && stamp.readText() == expectedStamp)) {
            target.deleteRecursively()
            target.mkdirs()
            ZipFile(aar).use { zip ->
                val canonicalRoot = target.canonicalPath + File.separator
                for (entry in zip.entries()) {
                    val out = File(target, entry.name)
                    // Zip-slip protection.
                    if (!out.canonicalPath.startsWith(canonicalRoot)) continue
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    }
                }
            }
            stamp.writeText(expectedStamp)
        }
        describe(target, coordinate)
    }

    fun describe(root: File, coordinate: MavenCoordinate): ExtractedAar {
        val manifest = File(root, "AndroidManifest.xml")
        val packageName = manifest.takeIf { it.isFile }?.readText()?.let { text ->
            Regex("""package\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
        } ?: (coordinate.group + "." + coordinate.artifact.replace('-', '.')).lowercase()
        return ExtractedAar(
            coordinate = coordinate,
            root = root,
            packageName = packageName,
            manifest = manifest,
            classesJar = File(root, "classes.jar").takeIf { it.isFile },
            libsJars = File(root, "libs").listFiles { f -> f.extension == "jar" }.orEmpty().toList(),
            resDir = File(root, "res").takeIf { it.isDirectory },
            assetsDir = File(root, "assets").takeIf { it.isDirectory },
            jniDir = File(root, "jni").takeIf { it.isDirectory },
            rTxt = File(root, "R.txt").takeIf { it.isFile },
            proguardRules = File(root, "proguard.txt").takeIf { it.isFile },
            lintJar = File(root, "lint.jar").takeIf { it.isFile },
        )
    }

    /** Parses `R.txt` (`int type name 0x7f...`) into resource type/name pairs. */
    fun readSymbols(rTxt: File): List<Pair<String, String>> = rTxt.readLines().mapNotNull { line ->
        val parts = line.trim().split(' ')
        if (parts.size >= 3) parts[1] to parts[2] else null
    }
}
