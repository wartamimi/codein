package com.codein.ide.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.codein.ide.MainActivity
import com.codein.ide.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Codein alive while a build runs and tells the user when it is done.
 *
 * A first Kotlin/Compose build takes minutes. Without a foreground service Android may kill the
 * app as soon as the user switches away, losing the build. While a build runs,
 * [BuildKeepAliveService] holds a foreground notification with the current step; when the build
 * ends, a separate notification reports the result and offers Install for APKs.
 */
@Singleton
class BuildNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val system = context.getSystemService(NotificationManager::class.java)
            system.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Build progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a build runs, so Android keeps Codein running in the background"
                setShowBadge(false)
            })
            system.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, "Build results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Build finished or failed"
            })
        }
    }

    fun started(projectName: String) {
        latest = progressNotification(projectName, "Starting…", null)
        val intent = Intent(context, BuildKeepAliveService::class.java)
        runCatching { ContextCompat.startForegroundService(context, intent) }
        manager.cancel(RESULT_ID)
    }

    fun progress(projectName: String, step: String, fraction: Float?) {
        latest = progressNotification(projectName, step, fraction)
        if (canPost()) runCatching { manager.notify(PROGRESS_ID, latest!!) }
    }

    fun finished(projectName: String, success: Boolean, message: String, apk: File?) {
        context.stopService(Intent(context, BuildKeepAliveService::class.java))
        latest = null
        if (!canPost()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_build)
            .setContentTitle(if (success) "$projectName built" else "$projectName: build failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp())
            .setAutoCancel(true)
        if (apk != null && apk.isFile && apk.extension == "apk") {
            val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
            val install = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(context, 2, install, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, "Install", pending)
        }
        runCatching { manager.notify(RESULT_ID, builder.build()) }
    }

    internal fun currentNotification(): android.app.Notification =
        latest ?: progressNotification("Codein", "Building…", null)

    private var latest: android.app.Notification? = null

    private fun progressNotification(projectName: String, step: String, fraction: Float?) =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_build)
            .setContentTitle("Building $projectName")
            .setContentText(step)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .setProgress(100, ((fraction ?: 0f) * 100).toInt().coerceIn(0, 100), fraction == null)
            .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_PROGRESS = "build-progress"
        const val CHANNEL_RESULT = "build-result"
        const val PROGRESS_ID = 4101
        const val RESULT_ID = 4102
    }
}

/** Foreground service that only exists to keep the process alive during a build. */
@dagger.hilt.android.AndroidEntryPoint
class BuildKeepAliveService : Service() {

    @Inject
    lateinit var notifier: BuildNotifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, BuildNotifier.PROGRESS_ID, notifier.currentNotification(), type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 limits dataSync services; a build never needs that long.
        stopSelf()
    }
}
