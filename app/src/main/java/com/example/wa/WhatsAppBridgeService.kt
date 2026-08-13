package com.example.wa

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
import com.example.ui.AutoTaskMainScreen

/**
 * Foreground service that keeps the WhatsApp Web bridge alive. Hosts the
 * WebView off-screen so web.whatsapp.com keeps its WebSocket + session even
 * when the app is backgrounded. The pairing Activity surfaces the WebView for
 * the one-time QR scan.
 */
class WhatsAppBridgeService : Service() {

    companion object {
        const val CHANNEL_ID = "whatsapp_bridge_channel"
        const val NOTIFICATION_ID = 8789
        const val ACTION_START = "com.example.autotask.action.WA_START"
        const val ACTION_STOP = "com.example.autotask.action.WA_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, WhatsAppBridgeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WhatsAppBridgeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        WhatsAppBridgeManager.initialize(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                // Create the WebView (idempotent) on the main thread.
                runOnMain {
                    WhatsAppBridgeManager.ensureWebView(applicationContext)
                }
            }
        }
        return START_STICKY
    }

    private fun runOnMain(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post(block)
        } else {
            block()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WhatsApp Web Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the CoS WhatsApp Web bridge connected"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WhatsAppBridgeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WhatsAppBridgeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoS WhatsApp Bridge")
            .setContentText("web.whatsapp.com running in background")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
