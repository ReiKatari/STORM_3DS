package com.storm3ds.emulator.ui.emulator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.storm3ds.emulator.R

class Storm3dsForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "storm3ds_emulation_fgs"
        const val NOTIFICATION_ID = 3001
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PAUSED = "extra_paused"
        const val ACTION_TOGGLE_PAUSE = "com.storm3ds.emulator.ACTION_TOGGLE_PAUSE"
        const val ACTION_STOP_EMULATION = "com.storm3ds.emulator.ACTION_STOP_EMULATION"

        @Volatile
        var onTogglePauseRequested: (() -> Unit)? = null
        @Volatile
        var onStopRequested: (() -> Unit)? = null
    }

    private var currentTitle: String = "Nintendo 3DS"
    private var paused: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                paused = !paused
                onTogglePauseRequested?.invoke()
            }
            ACTION_STOP_EMULATION -> {
                onStopRequested?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                currentTitle = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: currentTitle
                if (intent?.hasExtra(EXTRA_PAUSED) == true) {
                    paused = intent.getBooleanExtra(EXTRA_PAUSED, false)
                }
            }
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.storm3ds_fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val toggleIntent = Intent(this, Storm3dsForegroundService::class.java).apply { action = ACTION_TOGGLE_PAUSE }
        val stopIntent = Intent(this, Storm3dsForegroundService::class.java).apply { action = ACTION_STOP_EMULATION }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val togglePi = PendingIntent.getService(this, 10, toggleIntent, piFlags)
        val stopPi = PendingIntent.getService(this, 11, stopIntent, piFlags)

        val statusText = if (paused) getString(R.string.storm3ds_fgs_paused) else getString(R.string.storm3ds_fgs_running)
        val toggleLabel = if (paused) getString(R.string.storm3ds_resume) else getString(R.string.storm3ds_pause)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_quick_resume_tile)
            .setOngoing(true)
            .addAction(0, toggleLabel, togglePi)
            .addAction(0, getString(R.string.storm3ds_stop), stopPi)
            .build()
    }
}
