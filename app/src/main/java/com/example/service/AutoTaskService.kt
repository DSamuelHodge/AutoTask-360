package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.engine.AutoTaskEngine
import com.example.server.KtorLoopbackServer

class AutoTaskService : Service() {

    companion object {
        const val CHANNEL_ID = "autotask_service_channel"
        const val NOTIFICATION_ID = 8788
        const val ACTION_START = "com.example.autotask.action.START"
        const val ACTION_STOP = "com.example.autotask.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, AutoTaskService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AutoTaskService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private var ktorServer: KtorLoopbackServer? = null
    private var receivers: SystemEventReceivers? = null
    private lateinit var engine: AutoTaskEngine

    inner class LocalBinder : Binder() {
        fun getService(): AutoTaskService = this@AutoTaskService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        engine = AutoTaskEngine.getInstance(this)

        // Start Ktor HTTP server on 127.0.0.1:8788
        try {
            ktorServer = KtorLoopbackServer(this, 8788)
            ktorServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Register system broadcast receivers
        receivers = SystemEventReceivers(this)
        receivers?.register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createServiceNotification()
        startForeground(NOTIFICATION_ID, notification)
        engine.setRunningState(true)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {

        receivers?.unregister()
        ktorServer?.stop()
        engine.setRunningState(false)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoTask Tool Server Heartbeat",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground heartbeat service for on-device AI tool server"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("AutoTask Engine Running")
            .setContentText("Ktor server active at 127.0.0.1:8788 • ContentProvider active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
