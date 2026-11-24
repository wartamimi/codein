package com.codein.ide.compiler.sdk

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** Availability of each external toolchain component. */
data class ToolchainStatus(
    val aapt2: File?,
    val androidJar: File?,
    val kotlinCompilerDir: File?,
) {
    val canBuildResources: Boolean get() = aapt2 != null && androidJar != null
    val missing: List<String>
        get() = buildList {
            if (aapt2 == null) add("aapt2 (libaapt2.so in jniLibs)")
            if (androidJar == null) add("android.jar platform stubs")
        }
}

/**
 * Locates the native and JVM toolchain pieces used by the on-device build.
 *
 * - **aapt2** must ship inside the APK as `lib/<abi>/libaapt2.so`. Because the app uses legacy
 *   JNI packaging, Android extracts it into [android.content.pm.ApplicationInfo.nativeLibraryDir],
 *   which is the only app-writable-at-install location that is executable under the Android 10+
 *   W^X policy. Binaries written to `filesDir` at runtime cannot be executed on API 29+.
 * - **android.jar** (platform stubs) is imported once by the user or copied from the optional
 *   `assets/sdk/android.jar` asset into `filesDir/sdk/platforms/android-<api>/android.jar`.
 * - **kotlinc** is an optional directory of `kotlin-compiler-embeddable` jars, see
 *   [com.codein.ide.compiler.kotlin.KotlinCompilerHook].
 */
@Singleton
class BuildToolchain @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val sdkRoot: File get() = File(context.filesDir, "sdk").apply { mkdirs() }
    val kotlinCompilerDir: File get() = File(sdkRoot, "kotlinc")
    val workRoot: File get() = File(context.filesDir, "build-cache").apply { mkdirs() }

    fun aapt2Binary(): File? {
        val dir = context.applicationInfo.nativeLibraryDir ?: return null
        return File(dir, AAPT2_LIB_NAME).takeIf { it.isFile && it.canExecute() }
    }

    fun platformDir(api: Int): File = File(sdkRoot, "platforms/android-$api")

    /**
     * Returns the best available android.jar: the requested API if imported, otherwise the highest
     * imported API, otherwise one extracted from bundled assets.
     */
    fun androidJar(preferredApi: Int = DEFAULT_COMPILE_SDK): File? {
        File(platformDir(preferredApi), "android.jar").takeIf { it.isFile }?.let { return it }
        val platforms = File(sdkRoot, "platforms").listFiles().orEmpty()
            .mapNotNull { dir -> dir.name.removePrefix("android-").toIntOrNull()?.let { it to File(dir, "android.jar") } }
            .filter { it.second.isFile }
            .sortedByDescending { it.first }
        platforms.firstOrNull()?.let { return it.second }
        return extractBundledAndroidJar(preferredApi)
    }

    private fun extractBundledAndroidJar(api: Int): File? {
        val target = File(platformDir(api), "android.jar")
        return runCatching {
            context.assets.open(BUNDLED_ANDROID_JAR_ASSET).use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { input.copyTo(it) }
            }
            target
        }.getOrNull()
    }

    /** Imports an android.jar picked through the Storage Access Framework. Validates its contents. */
    suspend fun importAndroidJar(uri: Uri, api: Int): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(platformDir(api), "android.jar")
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "android.jar.tmp")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected file" }
                temp.outputStream().use { input.copyTo(it) }
            }
            ZipFile(temp).use { zip ->
                require(zip.getEntry("android/app/Activity.class") != null) { "Selected file is not an Android platform android.jar" }
            }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Unable to move android.jar into place" }
            target
        }
    }

    /** Imports kotlin compiler jars (kotlin-compiler-embeddable, kotlin-stdlib, etc.). */
    suspend fun importKotlinCompilerJar(uri: Uri, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinCompilerDir.mkdirs()
            val target = File(kotlinCompilerDir, fileName.substringAfterLast('/'))
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected file" }
                target.outputStream().use { input.copyTo(it) }
            }
            target
        }
    }

    fun status(api: Int = DEFAULT_COMPILE_SDK): ToolchainStatus = ToolchainStatus(
        aapt2 = aapt2Binary(),
        androidJar = androidJar(api),
        kotlinCompilerDir = kotlinCompilerDir.takeIf { dir -> dir.listFiles()?.any { it.name.startsWith("kotlin-compiler") } == true },
    )

    companion object {
        const val AAPT2_LIB_NAME = "libaapt2.so"
        const val BUNDLED_ANDROID_JAR_ASSET = "sdk/android.jar"
        const val DEFAULT_COMPILE_SDK = 35
    }
}
