package com.example.ota

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat

/**
 * Receives the PackageInstaller commit result after an OTA self-update.
 * Surfaces the outcome (success / failure reason) as a notification so the
 * user and the aware.* layer can react. The app is killed on success (it is
 * being replaced) — BootReceiver's MY_PACKAGE_REPLACED handler restarts the
 * services on next launch.
 *
 * For a non-default installer, commit() returns STATUS_PENDING_USER_ACTION and
 * hands us a confirmation Intent in Intent.EXTRA_INTENT — the app must launch
 * it (PackageInstaller's confirm dialog) itself.
 */
class OtaInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "Update installed. Restarting services."
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Update requires your confirmation."
            PackageInstaller.STATUS_FAILURE -> "Update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown"}"
            else -> "Update status: $status"
        }
        android.util.Log.i("OtaInstallReceiver", "status=$status msg=$message")

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Launch the system's confirmation dialog ourselves.
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            android.util.Log.i("OtaInstallReceiver", "EXTRA_INTENT=${confirm}")
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(confirm) } catch (e: Exception) { android.util.Log.w("OtaInstallReceiver", "startActivity failed: $e") }
            }
        }

        val channel = NotificationChannel(
            "ota_channel", "CoS Updates", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Self-update status" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val n = NotificationCompat.Builder(context, "ota_channel")
            .setContentTitle("CoS Self-Update")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(8801, n)
        } catch (_: Exception) {
            // Notification permission not granted; the log record carries the result.
        }
    }
}
