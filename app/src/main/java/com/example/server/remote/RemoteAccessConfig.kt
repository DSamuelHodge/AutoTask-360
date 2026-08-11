package com.example.server.remote

import android.content.Context
import com.example.server.KtorServerConfig

/**
 * Opt-in remote access configuration (#23). **Default OFF.**
 *
 * Deliberately separate from [KtorServerConfig] so remote exposure is never a side effect of a
 * loopback setting. Disabling remote mode leaves the loopback listener untouched
 * (docs/REMOTE_COS_THREAT_MODEL.md R2.1, R2.3).
 *
 * No listener is implemented in this PR. This type only records intent, so the UI can surface
 * the flag and other code can branch on it.
 */
data class RemoteAccessSnapshot(
    val enabled: Boolean,
    val port: Int,
    val requireTls: Boolean,
    val activeKeyCount: Int
) {
    /** True when the device is reachable off-device and the UI must warn (R2.6). */
    val isRemotelyReachable: Boolean = enabled
}

object RemoteAccessConfig {
    /** Separate from the loopback port so the two are independently reasoned about (R2.2). */
    const val DEFAULT_REMOTE_PORT = 8789

    /** Remote access is opt-in. This default must stay false. */
    const val DEFAULT_ENABLED = false

    /** Plaintext remote is never acceptable (R1.1, R1.4). */
    const val DEFAULT_REQUIRE_TLS = true

    private const val PREFS_NAME = "autotask_remote_config"
    private const val KEY_ENABLED = "remote_access_enabled"
    private const val KEY_PORT = "remote_access_port"
    private const val KEY_REQUIRE_TLS = "remote_access_require_tls"

    fun getSnapshot(context: Context, activeKeyCount: Int = 0): RemoteAccessSnapshot {
        val prefs = prefs(context)
        return RemoteAccessSnapshot(
            enabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
            port = readValidatedPort(context),
            requireTls = prefs.getBoolean(KEY_REQUIRE_TLS, DEFAULT_REQUIRE_TLS),
            activeKeyCount = activeKeyCount
        )
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    /**
     * Enable or disable remote access.
     *
     * TODO(#2): on disable, stop the listener and drop live connections (R7.11), and revoke or
     *  suspend active keys via [ApiKeyManager.revokeAll].
     * TODO(#24): require an explicit user confirmation before enabling, and show a persistent
     *  foreground notification while enabled (R2.6).
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun savePort(context: Context, port: Int): String? {
        val validationError = validatePort(port)
        if (validationError != null) return validationError

        prefs(context).edit()
            .putInt(KEY_PORT, port)
            .apply()
        return null
    }

    /** The remote port must not collide with the loopback or listener ports (R2.2). */
    fun validatePort(port: Int): String? {
        return when {
            port == KtorServerConfig.LISTENER_PORT ->
                "Port ${KtorServerConfig.LISTENER_PORT} is reserved for the listener."
            port == KtorServerConfig.DEFAULT_PORT ->
                "Port ${KtorServerConfig.DEFAULT_PORT} is reserved for the loopback server."
            port !in 1024..65535 -> "Port must be between 1024 and 65535."
            else -> null
        }
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, DEFAULT_ENABLED)
            .putInt(KEY_PORT, DEFAULT_REMOTE_PORT)
            .putBoolean(KEY_REQUIRE_TLS, DEFAULT_REQUIRE_TLS)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readValidatedPort(context: Context): Int {
        val prefs = prefs(context)
        val port = prefs.getInt(KEY_PORT, DEFAULT_REMOTE_PORT)
        if (validatePort(port) == null) return port

        prefs.edit().putInt(KEY_PORT, DEFAULT_REMOTE_PORT).apply()
        return DEFAULT_REMOTE_PORT
    }
}
