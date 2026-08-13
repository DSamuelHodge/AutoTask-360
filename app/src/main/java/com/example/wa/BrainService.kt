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

        // Supervisor tuning.
        const val HEALTHCHECK_MS = 2000L
        const val BACKOFF_START_MS = 2000L
        const val BACKOFF_MAX_MS = 60_000L
        const val CRASH_WINDOW_MS = 120_000L
        const val MAX_CRASHES_IN_WINDOW = 4

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String? = null

        @Volatile
        var restartCount: Int = 0
            private set

        /** True when the supervisor has tripped the kill-switch. */
        @Volatile
        var halted: Boolean = false
            private set

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

        /** PID file guard against double-spawn. */
        fun pidFile(context: Context): String {
            return File(context.getDir("brain", Context.MODE_PRIVATE), "cosd.pid").absolutePath
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

        /** Report supervisor state (used by /v1/status). */
        fun statusJson(): org.json.JSONObject {
            val o = org.json.JSONObject()
            o.put("running", isRunning)
            o.put("halted", halted)
            o.put("restart_count", restartCount)
            o.put("last_error", lastError ?: org.json.JSONObject.NULL)
            o.put("backoff_ms", currentBackoffMs)
            return o
        }

        @Volatile
        var currentBackoffMs: Long = BACKOFF_START_MS
            private set
    }

    private val running = AtomicBoolean(false)
    private var supervisor: Thread? = null
    private var process: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBrain()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running.get()) {
            running.set(true)
            halted = false
            restartCount = 0
            currentBackoffMs = BACKOFF_START_MS
            supervisor = Thread { supervise() }.also { it.start() }
        }
        return START_STICKY
    }

    /**
     * Supervisor loop: spawn, healthcheck every 2s, restart on death with
     * exponential backoff, and trip a kill-switch after too many crashes.
     */
    private fun supervise() {
        val ctx = applicationContext
        var crashTimes = ArrayDeque<Long>()
        var first = true
        while (running.get()) {
            if (first) {
                currentBackoffMs = BACKOFF_START_MS
                first = false
            }
            val started = spawnBrain(ctx)
            if (!started) {
                // Spawn itself failed (e.g. binary missing) — not a crash loop;
                // keep lastError visible and wait the window before retrying.
                sleepQuietly(currentBackoffMs)
                continue
            }
            // Wait for the process to die, polling the healthcheck.
            while (running.get() && isProcessAlive()) {
                sleepQuietly(HEALTHCHECK_MS)
            }
            if (!running.get()) break // stop requested
            // Process died: record crash, apply backoff / kill-switch.
            val now = System.currentTimeMillis()
            crashTimes.addLast(now)
            while (crashTimes.isNotEmpty() && now - crashTimes.first() > CRASH_WINDOW_MS) {
                crashTimes.removeFirst()
            }
            restartCount++
            if (crashTimes.size >= MAX_CRASHES_IN_WINDOW) {
                halted = true
                isRunning = false
                lastError = "brain killed: $MAX_CRASHES_IN_WINDOW crashes in ${CRASH_WINDOW_MS / 1000}s — halted"
                break
            }
            // Exponential backoff: 2s, 4s, 8s … capped at 60s.
            currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(BACKOFF_MAX_MS)
            isRunning = false
            lastError = "brain died (exit=${process?.exitValue()}); restarting in ${currentBackoffMs}ms"
            sleepQuietly(currentBackoffMs)
        }
        running.set(false)
        isRunning = false
    }

    /** Spawn (or reseed+spawn) the daemon. Returns true if it started. */
    private fun spawnBrain(ctx: android.content.Context): Boolean {
        val binary = binaryPath(ctx)
        val db = dbPath(ctx)
        val sock = sockPath(ctx)
        val token = getToken(ctx)
        val logFile = File(logPath(ctx))
        val pid = File(pidFile(ctx))
        try {
            if (!File(binary).canExecute()) {
                lastError = "binary not executable: $binary"
                isRunning = false
                return false
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
            pid.writeText(processPid(p).toString())
            isRunning = true
            lastError = null
            // Drain output to the log file on a separate thread so the
            // supervisor can healthcheck while the daemon runs.
            val logFileForDrain = logFile
            Thread {
                p.inputStream.bufferedReader().useLines { lines ->
                    logFileForDrain.parentFile?.mkdirs()
                    lines.forEach { line -> logFileForDrain.appendText(line + "\n") }
                }
            }.start()
            return true
        } catch (e: Exception) {
            isRunning = false
            lastError = "brain spawn failed: ${e.message}"
            return false
        }
    }

    private fun isProcessAlive(): Boolean {
        val p = process ?: return false
        return try {
            p.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    /** Process PID via reflection (Java 9 `Process.pid()` is gated on API 26+). */
    private fun processPid(p: Process): Long {
        return try {
            val m = Process::class.java.getMethod("pid")
            m.invoke(p) as Long
        } catch (_: Exception) {
            try {
                val f = p.javaClass.getDeclaredField("pid")
                f.isAccessible = true
                (f.getLong(p))
            } catch (_: Exception) {
                -1L
            }
        }
    }

    private fun sleepQuietly(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    private fun stopBrain() {
        running.set(false)
        isRunning = false
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        File(pidFile(this)).delete()
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
