package com.example.wa

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage + provisioning for the Turso cloud credentials used by the
 * CoS brain embedded replica.
 *
 * The auth token is treated as a secret: it is stored in
 * [EncryptedSharedPreferences] (Keystore-backed AES-GCM) and never in plain
 * SharedPreferences. The DB URL is not secret but is kept in the same store
 * for symmetry.
 *
 * Provisioning: the secrets-gradle-plugin reads `TURSO_URL` / `TURSO_TOKEN`
 * from the git-ignored `.env` file and exposes them as `BuildConfig` fields.
 * The first call to [getUrl] / [getToken] seeds the encrypted store from
 * those build-time defaults, so values can also be overridden at runtime.
 */
object TursoConfig {

    const val PREFS = "turso_config"
    const val KEY_URL = "turso_url"
    const val KEY_TOKEN = "turso_token"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** The Turso DB URL, provisioned from BuildConfig on first use. */
    fun getUrl(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_URL, null)?.let { if (it.isNotEmpty()) return it }
        val builtIn = com.example.BuildConfig.TURSO_URL
        if (builtIn.isNotEmpty()) {
            prefs.edit().putString(KEY_URL, builtIn).apply()
            return builtIn
        }
        return ""
    }

    /** The Turso auth token, provisioned from BuildConfig on first use. */
    fun getToken(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_TOKEN, null)?.let { if (it.isNotEmpty()) return it }
        val builtIn = com.example.BuildConfig.TURSO_TOKEN
        if (builtIn.isNotEmpty()) {
            prefs.edit().putString(KEY_TOKEN, builtIn).apply()
            return builtIn
        }
        return ""
    }

    /** True when both URL and token are provisioned (i.e. Turso sync is on). */
    fun isConfigured(context: Context): Boolean {
        return getUrl(context).isNotEmpty() && getToken(context).isNotEmpty()
    }

    /** Override the stored URL (e.g. via an admin endpoint). */
    fun setUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_URL, url).apply()
    }

    /** Override the stored token (e.g. via an admin endpoint). */
    fun setToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    /** Clear stored credentials. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
