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
import com.example.MainActivity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Spawns the Rust CoS brain (`libcosd.so`) as a supervised child process and
 * keeps it alive.
 *
 * The binary ships as a native lib (`jniLibs/arm64-v8a/libcosd.so`) with
 * `extractNativeLibs=true`, so PackageManager materializes it into the
 * OS-owned `nativeLibraryDir` at install time — an executable-but-not-writable
 * location exempt from Android 10+'s W^X rule. We spawn it (never dlopen it;
 * it's a PIE executable, not a shared lib) via ProcessBuilder.
 *
 * On Android 10+, `ProcessBuilder(".../libcosd.so")` works only because that
 * directory is app-non-writable. Running it from filesDir would fail with
 * EACCES.
 */
class BrainService : Service() {

    companion object {
        const val CHANNEL_ID = "brain_channel"
        const val NOTIFICATION_ID = 8791
        const val ACTION_START = "com.example.autotask.action.BRAIN_START"
        const val ACTION_STOP = "com.example.autotask.action.BRAIN_STOP"
        const val DEFAULT_PORT = 8790
        const val PREFS = "brain_config"
        const val KEY_TOKEN = "brain_token"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String? = null

        fun startService(context: Context) {
            val intent = Intent(context, BrainService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BrainService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        /** Path to the extracted daemon binary. */
        fun binaryPath(context: Context): String {
            return File(context.applicationInfo.nativeLibraryDir, "libcosd.so").absolutePath
        }

        /** Writable DB location (app-private). */
        fun dbPath(context: Context): String {
            return File(context.getDir("brain", Context.MODE_PRIVATE), "cos.db").absolutePath
        }

        /** Log file for the daemon's stdout/stderr. */
        fun logPath(context: Context): String {
            return File(context.getDir("brain", Context.MODE_PRIVATE), "cosd.log").absolutePath
        }

        /** UNIX domain socket path for engine↔brain IPC. */
        fun sockPath(context: Context): String {
            return File(context.getDir("brain", Context.MODE_PRIVATE), "cosd.sock").absolutePath
        }

        /** The shared auth token (generated once, persisted in prefs). */
        fun getToken(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(KEY_TOKEN, null)?.let { return it }
            val tok = "cos-" + java.util.UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_TOKEN, tok).apply()
            return tok
        }

        /** Debug: expose the brain over loopback TCP for adb forward. */
        fun debugTcp(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("debug_tcp", false)
        }
    }

    private val alive = AtomicBoolean(false)
    private var process: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBrain()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!alive.get()) {
            Thread { spawnBrain() }.start()
        }
        return START_STICKY
    }

    private fun spawnBrain() {
        val ctx = applicationContext
        val binary = binaryPath(ctx)
        val db = dbPath(ctx)
        val sock = sockPath(ctx)
        val token = getToken(ctx)
        val logFile = File(logPath(ctx))
        try {
            if (!File(binary).canExecute()) {
                lastError = "binary not executable: $binary"
                isRunning = false
                return
            }
            // Seed idempotently (creates DB + default contacts), then serve.
            val seed = ProcessBuilder(binary, "seed", "--db", db).start()
            seed.waitFor()
            val args = mutableListOf(
                binary, "serve",
                "--db", db,
                "--token", token,
            )
            if (debugTcp(ctx)) {
                args += listOf("--addr", "127.0.0.1:$DEFAULT_PORT")
            } else {
                // Remove a stale socket file before binding.
                File(sock).delete()
                args += listOf("--sock", sock)
            }
            val p = ProcessBuilder(args).redirectErrorStream(true).start()
            process = p
            alive.set(true)
            isRunning = true
            lastError = null
            // Drain output to the log file so we don't block the pipe.
            p.inputStream.bufferedReader().useLines { lines ->
                logFile.parentFile?.mkdirs()
                lines.forEach { line ->
                    logFile.appendText(line + "\n")
                }
            }
        } catch (e: Exception) {
            alive.set(false)
            isRunning = false
            lastError = "brain spawn failed: ${e.message}"
        }
    }

    private fun stopBrain() {
        alive.set(false)
        isRunning = false
        try { process?.destroy() } catch (_: Exception) {}
        process = null
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "CoS Brain", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the on-device CoS brain (Rust daemon)"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, BrainService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoS Brain")
            .setContentText("Rust daemon running (127.0.0.1:8790)")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopBrain()
        super.onDestroy()
    }
}
