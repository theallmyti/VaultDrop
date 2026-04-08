package com.adityaprasad.vaultdrop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.adityaprasad.vaultdrop.R
import com.adityaprasad.vaultdrop.data.downloader.DownloadManager
import com.adityaprasad.vaultdrop.data.repository.DownloadRepository
import com.adityaprasad.vaultdrop.domain.model.DownloadItem
import com.adityaprasad.vaultdrop.domain.model.DownloadStatus
import com.adityaprasad.vaultdrop.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var repository: DownloadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification("VaultDrop", "Ready to download"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification("VaultDrop", "Ready to download"))
        }
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "720p"
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY

        serviceScope.launch {
            // Retry a few times in case the DB insert hasn't completed yet
            var item: com.adityaprasad.vaultdrop.domain.model.DownloadItem? = null
            for (attempt in 1..5) {
                item = repository.getDownloadById(downloadId)
                if (item != null) break
                kotlinx.coroutines.delay(200L * attempt)
            }

            if (item != null) {
                downloadManager.enqueueDownload(item, quality)
            } else {
                // If still not found, create it directly in the service
                val platform = com.adityaprasad.vaultdrop.domain.model.Platform.detect(url)
                val newItem = com.adityaprasad.vaultdrop.domain.model.DownloadItem(
                    id = downloadId,
                    url = url,
                    title = "Fetching title...",
                    platform = platform,
                    status = com.adityaprasad.vaultdrop.domain.model.DownloadStatus.QUEUED,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertDownload(newItem)
                downloadManager.enqueueDownload(newItem, quality)
            }
        }

        return START_NOT_STICKY
    }

    private fun observeDownloads() {
        serviceScope.launch {
            repository.getActiveDownloads().collectLatest { activeDownloads ->
                if (activeDownloads.isEmpty()) {
                    updateNotification("VaultDrop", "No active downloads")
                } else {
                    val current = activeDownloads.firstOrNull { it.status == DownloadStatus.ACTIVE }
                    if (current != null) {
                        updateNotification(
                            current.title,
                            "${current.progressPercent}% · ${current.formattedSpeed}/s"
                        )
                    } else {
                        updateNotification(
                            "VaultDrop",
                            "${activeDownloads.size} downloads queued"
                        )
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(title, text))
    }

    fun sendCompletionNotification(item: DownloadItem) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_download_complete, item.title))
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "vaultdrop_downloads"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_URL = "extra_url"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        fun start(context: Context, downloadId: String, url: String, quality: String = "720p") {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_QUALITY, quality)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
