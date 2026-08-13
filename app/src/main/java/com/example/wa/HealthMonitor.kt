package com.example.wa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Passive special-access drift detector.
 *
 * Android offers NO API to silently re-grant notification-listener, DND
 * policy, or WRITE_SETTINGS access — they are deliberate user-consent gates.
 * HealthMonitor therefore acts as a tripwire: it detects drift, posts a
 * notification with a precise one-tap deep-link to the exact settings screen,
 * verifies post-repair, and reports state. It never attempts to re-grant
 * programmatically (that always loses against the OS).
 */
class HealthMonitor : Service() {

    companion object {
        const val CHANNEL_ID = "health_monitor_channel"
        const val NOTIFICATION_ID = 8792
        const val ACTION_CHECK = "com.example.autotask.action.HEALTH_CHECK"
        const val CHECK_INTERVAL_MS = 60 * 60 * 1000L // hourly

        @Volatile
        var lastCheck: Long = 0

        @Volatile
        var grantsHealthy: Boolean = false

        fun startService(context: Context) {
            val intent = Intent(context, HealthMonitor::class.java).apply { action = ACTION_CHECK }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Report grant health (used by /v1/status). */
        fun statusJson(): org.json.JSONObject {
            val o = org.json.JSONObject()
            o.put("last_check", lastCheck)
            o.put("healthy", grantsHealthy)
            o.put("last_error", lastError ?: org.json.JSONObject.NULL)
            return o
        }

        @Volatile
        var lastError: String? = null
            private set
    }

    private var running = false
    private var thread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildIdleNotification())
        if (intent?.action == ACTION_CHECK) {
            // Trigger an immediate check, then keep the hourly loop.
            Thread { runCheck(applicationContext) }.start()
        }
        if (!running) {
            running = true
            thread = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    private fun loop() {
        while (running) {
            runCheck(applicationContext)
            Thread.sleep(CHECK_INTERVAL_MS)
        }
    }

    private fun runCheck(ctx: Context) {
        lastCheck = System.currentTimeMillis()
        val missing = try {
            findMissingGrants(ctx)
        } catch (e: Exception) {
            lastError = "health check failed: ${e.message}"
            return
        }
        grantsHealthy = missing.isEmpty()
        if (missing.isNotEmpty()) {
            postRepairNotification(ctx, missing)
        }
    }

    /** Find which special-access grants are missing (passive read-only). */
    private fun findMissingGrants(ctx: Context): List<Pair<String, String>> {
        val missing = mutableListOf<Pair<String, String>>()
        val nm = getSystemService(NotificationManager::class.java)

        // Notification listener.
        val listenerEnabled = isNotificationListenerEnabled(ctx)
        if (!listenerEnabled) {
            missing.add("Notification access" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

        // DND / notification policy access.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val dndOk = nm.isNotificationPolicyAccessGranted
            if (!dndOk) {
                missing.add("Do Not Disturb access" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
        }

        // WRITE_SETTINGS (appop).
        val writeSettingsOk = Settings.System.canWrite(ctx)
        if (!writeSettingsOk) {
            missing.add("System settings access" to Settings.ACTION_MANAGE_WRITE_SETTINGS)
        }
        return missing
    }

    private fun isNotificationListenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(
            ctx.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any {
            it.split("/").firstOrNull() == ctx.packageName
        }
    }

    private fun postRepairNotification(ctx: Context, missing: List<Pair<String, String>>) {
        val names = missing.joinToString(", ") { it.first }
        // One tap → the first missing settings screen.
        val actual = explicitSettingsIntent(ctx, missing.first().second)
        val pending = PendingIntent.getActivity(
            ctx, 0, actual, PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("CoS permissions need attention")
            .setContentText("Missing: $names. Tap to fix.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, n)
    }

    private fun explicitSettingsIntent(ctx: Context, settingsAction: String): Intent {
        return when (settingsAction) {
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS ->
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            else -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "CoS Health Monitor", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Detects and reports special-access grant drift"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildIdleNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoS Health Monitor")
            .setContentText("Watching special-access grants")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
