package com.example.security

import android.content.Context
import com.example.BuildConfig
import com.example.wa.BrainService

class ExternalAccess private constructor(context: Context) {
    private val appContext = context.applicationContext
    val credentials: CredentialStore = PrefsCredentialStore(appContext)
    val pairing = PairingManager(credentials)
    val limiter = RateLimiter()
    val idempotency = IdempotencyStore()
    val audit = SecurityAuditLog()
    val guard = AccessGuard(
        credentials = credentials,
        brainToken = { BrainService.getToken(appContext) },
        lanEnabled = { isLanEnabled() },
        debugBuild = BuildConfig.DEBUG
    )

    fun isLanEnabled(): Boolean =
        prefs().getBoolean(KEY_LAN, false) && pairing.activeCredentials().isNotEmpty()

    fun setLanEnabled(enabled: Boolean) {
        if (enabled && pairing.activeCredentials().isEmpty()) {
            throw PairingRequiredException()
        }
        prefs().edit().putBoolean(KEY_LAN, enabled).apply()
        audit.record(
            AccessPrincipal.LOCAL,
            "LAN_MODE",
            "/v1/pairing/lan",
            if (enabled) "ENABLED" else "DISABLED",
            "OK"
        )
    }

    fun startPairing(): PairingChallenge = pairing.start()

    fun completePairing(
        code: String,
        name: String,
        scopes: Set<AccessScope>,
        approvedActions: Set<String>
    ): IssuedCredential {
        val issued = pairing.complete(code, name, scopes, approvedActions)
        audit.record(
            AccessPrincipal.LOCAL,
            "PAIRING_COMPLETE",
            "/v1/pairing/complete",
            "ISSUED",
            "OK",
            issued.credential.id
        )
        return issued
    }

    companion object {
        private const val PREFS = "autotask_server_config"
        private const val KEY_LAN = "ktor_lan_enabled"

        @Volatile
        private var instance: ExternalAccess? = null

        fun getInstance(context: Context): ExternalAccess {
            return instance ?: synchronized(this) {
                instance ?: ExternalAccess(context).also { instance = it }
            }
        }

        fun resetForTests() {
            instance = null
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
