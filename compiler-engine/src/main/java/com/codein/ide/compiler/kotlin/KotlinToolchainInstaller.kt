package com.codein.ide.compiler.kotlin

import com.codein.ide.compiler.sdk.BuildToolchain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A single Maven artifact of the on-device Kotlin toolchain. */
data class ToolchainArtifact(val group: String, val name: String, val version: String) {
    val fileName: String get() = "$name-$version.jar"
    fun path(): String = "${group.replace('.', '/')}/$name/$version/$fileName"
}

/** Progress of a toolchain download: [fraction] is 0..1 across all artifacts. */
data class InstallProgress(val artifact: String, val index: Int, val total: Int, val fraction: Float)

/**
 * Downloads the Kotlin compiler (and optionally the Compose compiler plugin) from Maven Central
 * into [BuildToolchain.kotlinCompilerDir], verifying every jar against its published SHA-1.
 * The versions match the Kotlin version Codein itself is built with.
 */
@Singleton
class KotlinToolchainInstaller @Inject constructor(
    private val toolchain: BuildToolchain,
) {

    fun artifacts(includeCompose: Boolean): List<ToolchainArtifact> = buildList {
        add(ToolchainArtifact("org.jetbrains.kotlin", "kotlin-compiler-embeddable", KOTLIN_VERSION))
        add(ToolchainArtifact("org.jetbrains.kotlin", "kotlin-stdlib", KOTLIN_VERSION))
        add(ToolchainArtifact("org.jetbrains.kotlin", "kotlin-script-runtime", KOTLIN_VERSION))
        add(ToolchainArtifact("org.jetbrains.kotlin", "kotlin-daemon-embeddable", KOTLIN_VERSION))
        add(ToolchainArtifact("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"))
        add(ToolchainArtifact("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"))
        add(ToolchainArtifact("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"))
        add(ToolchainArtifact("org.jetbrains", "annotations", "13.0"))
        if (includeCompose) add(ToolchainArtifact("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", KOTLIN_VERSION))
    }

    /** True when every artifact (and the Compose plugin when asked) is already installed. */
    fun isInstalled(includeCompose: Boolean): Boolean =
        artifacts(includeCompose).all { File(toolchain.kotlinCompilerDir, it.fileName).isFile }

    suspend fun install(includeCompose: Boolean, onProgress: (InstallProgress) -> Unit): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = toolchain.kotlinCompilerDir.apply { mkdirs() }
            val list = artifacts(includeCompose)
            val installed = mutableListOf<File>()
            list.forEachIndexed { index, artifact ->
                currentCoroutineContext().ensureActive()
                val target = File(dir, artifact.fileName)
                if (target.isFile && target.length() > 0) {
                    installed += target
                    onProgress(InstallProgress(artifact.name, index + 1, list.size, (index + 1f) / list.size))
                    return@forEachIndexed
                }
                val expectedSha1 = fetchText("$MAVEN_CENTRAL/${artifact.path()}.sha1").trim().substringBefore(' ').lowercase()
                val temp = File(dir, artifact.fileName + ".part")
                download("$MAVEN_CENTRAL/${artifact.path()}", temp) { fileFraction ->
                    onProgress(InstallProgress(artifact.name, index + 1, list.size, (index + fileFraction) / list.size))
                }
                val actual = sha1(temp)
                if (expectedSha1.length == 40 && actual != expectedSha1) {
                    temp.delete()
                    throw IOException("Checksum mismatch for ${artifact.fileName}")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) throw IOException("Unable to move ${artifact.fileName} into place")
                installed += target
                onProgress(InstallProgress(artifact.name, index + 1, list.size, (index + 1f) / list.size))
            }
            installed
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            val conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Codein")
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without Location from $current")
                conn.disconnect()
                current = URL(current, location)
            } else {
                if (code != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    throw IOException("HTTP $code for $current")
                }
                return conn
            }
        }
        throw IOException("Too many redirects for $url")
    }

    private fun fetchText(url: String): String {
        val conn = open(url)
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }

    private suspend fun download(url: String, target: File, onFraction: (Float) -> Unit) {
        val conn = open(url)
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0 && read - lastReported > 256 * 1024) {
                            lastReported = read
                            onFraction((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KOTLIN_VERSION = "2.1.21"
        private const val MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
        private const val MAX_REDIRECTS = 8
    }
}
