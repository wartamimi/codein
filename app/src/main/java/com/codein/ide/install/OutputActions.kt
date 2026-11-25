package com.codein.ide.install

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Installs built APKs and shares APK/AAB outputs through FileProvider content URIs. */
object OutputActions {

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    /**
     * Launches the system package installer. When the user has not yet allowed Codein to install
     * apps, opens the "Install unknown apps" settings page instead and returns false.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, apk), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** The package name inside an APK file, or null when it cannot be read. */
    fun packageOf(context: Context, apk: File): String? =
        runCatching { context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName }.getOrNull()

    /** True when [packageName] was installed or updated at or after [sinceMillis] (with a little clock slack). */
    fun installedSince(context: Context, packageName: String, sinceMillis: Long): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime >= sinceMillis - 2_000
    }.getOrDefault(false)

    /** Starts the app's launcher activity. */
    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun share(context: Context, file: File, mimeType: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
