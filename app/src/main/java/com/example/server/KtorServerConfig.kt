package com.example.server

import android.content.Context

data class KtorServerSnapshot(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val listenerPort: Int,
    val isRunning: Boolean,
    val lastError: String,
    val lastResult: String
) {
    val baseUrl: String = "http://$host:$port"
}

object KtorServerConfig {
    // Bind on all interfaces so a paired development harness can use the
    // phone's LAN address. Non-loopback REST requests are bearer-authenticated
    // by KtorLoopbackServer; the MCP route has its own auth check.
    const val HOST = "0.0.0.0"
    const val LOCAL_CLIENT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 8788
    const val LISTENER_PORT = 8787

    fun isLoopbackHost(host: String): Boolean =
        host == LOCAL_CLIENT_HOST || host == "::1" || host == "localhost" || host == "::ffff:127.0.0.1"

    private const val PREFS_NAME = "autotask_server_config"
    private const val KEY_ENABLED = "ktor_server_enabled"
    private const val KEY_PORT = "ktor_server_port"
    private const val KEY_RUNNING = "ktor_server_running"
    private const val KEY_LAST_ERROR = "ktor_server_last_error"
    private const val KEY_LAST_RESULT = "ktor_server_last_result"

    fun getSnapshot(context: Context): KtorServerSnapshot {
        val prefs = prefs(context)
        val port = readValidatedPort(context)
        return KtorServerSnapshot(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            host = HOST,
            port = port,
            listenerPort = LISTENER_PORT,
            isRunning = prefs.getBoolean(KEY_RUNNING, false),
            lastError = prefs.getString(KEY_LAST_ERROR, "") ?: "",
            lastResult = prefs.getString(KEY_LAST_RESULT, "Not started") ?: "Not started"
        )
    }

    fun getPort(context: Context): Int = getSnapshot(context).port

    fun isEnabled(context: Context): Boolean = getSnapshot(context).enabled

    fun savePort(context: Context, port: Int): String? {
        val validationError = validatePort(port)
        if (validationError != null) return validationError

        prefs(context).edit()
            .putInt(KEY_PORT, port)
            .apply()
        return null
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_PORT, DEFAULT_PORT)
            .putString(KEY_LAST_ERROR, "")
            .putString(KEY_LAST_RESULT, "Reset to default")
            .apply()
    }

    fun markStarted(context: Context, port: Int) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, true)
            .putString(KEY_LAST_ERROR, "")
            .putString(KEY_LAST_RESULT, "Running at $HOST:$port")
            .apply()
    }

    fun markStopped(context: Context, message: String = "Stopped") {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putString(KEY_LAST_RESULT, message)
            .apply()
    }

    fun markFailed(context: Context, message: String) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putString(KEY_LAST_ERROR, message)
            .putString(KEY_LAST_RESULT, "Failed to start")
            .apply()
    }

    fun validatePort(port: Int): String? {
        return when {
            port == LISTENER_PORT -> "Port $LISTENER_PORT is reserved for the listener."
            port !in 1024..65535 -> "Port must be between 1024 and 65535."
            else -> null
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readValidatedPort(context: Context): Int {
        val prefs = prefs(context)
        val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        if (validatePort(port) == null) return port

        prefs.edit()
            .putInt(KEY_PORT, DEFAULT_PORT)
            .putString(KEY_LAST_ERROR, "Invalid stored port $port; reset to $DEFAULT_PORT.")
            .putString(KEY_LAST_RESULT, "Port reset to default")
            .apply()
        return DEFAULT_PORT
    }
}
