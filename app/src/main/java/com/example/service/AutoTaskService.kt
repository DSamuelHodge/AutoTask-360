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
import com.example.application.AutoTaskRuntime
import com.example.application.AutomationCommandFacade
import com.example.server.KtorLoopbackServer
import com.example.server.KtorServerConfig
import com.example.server.WatchLoopbackServer

class AutoTaskService : Service() {

    companion object {
        const val CHANNEL_ID = "autotask_service_channel"
        const val NOTIFICATION_ID = 8788
        const val ACTION_START = "com.example.autotask.action.START"
        const val ACTION_STOP = "com.example.autotask.action.STOP"
        const val ACTION_RESTART_KTOR = "com.example.autotask.action.RESTART_KTOR"

        @Volatile
        var isForegroundActive: Boolean = false
            private set

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

        fun restartKtorServer(context: Context) {
            val intent = Intent(context, AutoTaskService::class.java).apply {
                action = ACTION_RESTART_KTOR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val binder = LocalBinder()
    private var ktorServer: KtorLoopbackServer? = null
    private var watchServer: WatchLoopbackServer? = null
    private var receivers: SystemEventReceivers? = null
    private lateinit var commands: AutomationCommandFacade

    inner class LocalBinder : Binder() {
        fun getService(): AutoTaskService = this@AutoTaskService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AutoTaskRuntime.start(this)
        commands = AutomationCommandFacade.getInstance(this)

        startKtorServer()

        // Register system broadcast receivers
        receivers = SystemEventReceivers(this)
        receivers?.register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isForegroundActive = false
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART_KTOR -> {
                restartKtorServerInternal()
            }
        }

        val notification = createServiceNotification()
        startForeground(NOTIFICATION_ID, notification)
        isForegroundActive = true
        commands.setRunningState(true)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {

        receivers?.unregister()
        watchServer?.stop()
        watchServer = null
        ktorServer?.stop()
        KtorServerConfig.markStopped(this)
        commands.setRunningState(false)
        isForegroundActive = false
        super.onDestroy()
    }

    private fun restartKtorServerInternal() {
        ktorServer?.stop()
        ktorServer = null
        watchServer?.stop()
        watchServer = null
        KtorServerConfig.markStopped(this, "Restarting")
        startKtorServer()
    }

    private fun startKtorServer() {
        if (!KtorServerConfig.isEnabled(this)) {
            KtorServerConfig.markStopped(this, "Disabled")
            return
        }

        val port = KtorServerConfig.getPort(this)
        try {
            ktorServer = KtorLoopbackServer(this, port)
            ktorServer?.start()
            KtorServerConfig.markStarted(this, port)
            startWatchServer()
        } catch (e: Exception) {
            ktorServer = null
            val message = e.localizedMessage ?: e.javaClass.simpleName
            KtorServerConfig.markFailed(this, message)
            e.printStackTrace()
        }
    }

    private fun startWatchServer() {
        try {
            watchServer = WatchLoopbackServer()
            watchServer?.start()
        } catch (e: Exception) {
            watchServer = null
            e.printStackTrace()
        }
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
            .setContentText("Ktor server ${KtorServerConfig.getSnapshot(this).baseUrl} • ContentProvider active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
