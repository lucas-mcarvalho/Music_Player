package com.musicplayer.app.online

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.musicplayer.app.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Mantém os downloads rodando com o app fechado e mostra o progresso na notificação. */
class DownloadService : Service() {
    private val scope = MainScope()
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Downloads de músicas", NotificationManager.IMPORTANCE_LOW)
            )
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build("Preparando download…", null),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (watcher == null) {
            watcher = scope.launch {
                snapshotFlow { Downloads.states.values.filter { it.active } }.collect { active ->
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val current = active.firstOrNull { it.status != DownloadStatus.QUEUED } ?: active.first()
                        val title = if (active.size == 1) "Baixando 1 música" else "Baixando ${active.size} músicas"
                        manager.notify(NOTIFICATION_ID, build(title, current))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, current: DownloadState?) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(current?.item?.title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1,
                    Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .apply {
                when (current?.status) {
                    DownloadStatus.DOWNLOADING ->
                        if (current.progress >= 0) setProgress(100, (current.progress * 100).toInt(), false)
                        else setProgress(0, 0, true)
                    else -> setProgress(0, 0, true)
                }
            }
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}
