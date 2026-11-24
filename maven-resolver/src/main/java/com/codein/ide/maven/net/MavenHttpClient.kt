package com.codein.ide.maven.net

import com.codein.ide.maven.model.MavenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** HTTP access to Maven repositories with cancellation-aware coroutine calls. */
@Singleton
class MavenHttpClient @Inject constructor() {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()

    /** Returns the body text, or null on 404. Throws on other failures. */
    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        client.newCall(request(url)).await().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                response.code == 404 || response.code == 410 -> null
                else -> throw IOException("HTTP ${response.code} for $url")
            }
        }
    }

    /** Downloads [url] into [target] atomically. Returns the byte count, or null on 404. */
    suspend fun download(url: String, target: File): Long? = withContext(Dispatchers.IO) {
        client.newCall(request(url)).await().use { response ->
            when {
                response.isSuccessful -> {
                    target.parentFile?.mkdirs()
                    val temp = File(target.parentFile, target.name + ".part")
                    val body = response.body ?: throw IOException("Empty body for $url")
                    temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) throw IOException("Unable to move ${temp.path}")
                    target.length()
                }
                response.code == 404 || response.code == 410 -> null
                else -> throw IOException("HTTP ${response.code} for $url")
            }
        }
    }

    fun artifactUrl(repository: MavenRepository, path: String): String = repository.baseUrl + "/" + path

    companion object {
        const val USER_AGENT = "Codein-IDE/1.0 (Android)"
    }
}
