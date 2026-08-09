package com.example.data

import android.content.Context
import com.example.engine.CapabilityProvider
import com.example.server.KtorServerConfig
import kotlinx.coroutines.flow.Flow

class AutoTaskRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AutoTaskDatabase.getInstance(context)
    val profileDao = db.profileDao()
    val logDao = db.logDao()

    val allProfilesFlow: Flow<List<AutomationProfile>> = profileDao.getAllProfilesFlow()
    val logsFlow: Flow<List<ExecutionLog>> = logDao.getLogsFlow(100)

    suspend fun seedDefaultRecipesIfNeeded() {
        if (profileDao.getProfileCount() <= 5) {
            val starterProfiles = PolicySeeder.getStarterProfiles()
            profileDao.insertProfiles(starterProfiles)
        }
        disableUnsafeStarterProfiles()
    }

    suspend fun getProfileById(id: String): AutomationProfile? = profileDao.getProfileById(id)

    suspend fun upsertProfile(profile: AutomationProfile) {
        profileDao.upsertProfile(profile)
    }

    suspend fun updateProfile(profile: AutomationProfile) {
        profileDao.updateProfile(profile)
    }

    suspend fun setProfileEnabled(id: String, isEnabled: Boolean) {
        profileDao.setProfileEnabled(id, isEnabled)
    }

    suspend fun deleteProfileById(id: String): Boolean {
        return profileDao.deleteProfileById(id) > 0
    }

    suspend fun clearLogs() {
        logDao.clearLogs()
    }

    suspend fun insertLog(log: ExecutionLog): Long {
        return logDao.insertLog(log)
    }

    suspend fun getStatusMap(): Map<String, Any> {
        val profileCount = profileDao.getProfileCount()
        val logCount = logDao.getLogCount()
        val serverConfig = KtorServerConfig.getSnapshot(appContext)
        val permissionSummary = CapabilityProvider.permissionSummary(appContext)
        return mapOf(
            "engine_running" to 1,
            "profile_count" to profileCount,
            "log_count" to logCount,
            "relay_target" to serverConfig.baseUrl,
            "ktor_server_enabled" to serverConfig.enabled,
            "ktor_server_host" to serverConfig.host,
            "ktor_server_port" to serverConfig.port,
            "ktor_server_running" to serverConfig.isRunning,
            "listener_port" to serverConfig.listenerPort,
            "last_server_error" to serverConfig.lastError,
            "last_server_result" to serverConfig.lastResult,
            "notification_policy_declared" to (permissionSummary["notification_policy_declared"] ?: false),
            "notification_policy_granted" to (permissionSummary["notification_policy_granted"] ?: false),
            "write_settings_granted" to (permissionSummary["write_settings_granted"] ?: false),
            "notification_listener_enabled" to (permissionSummary["notification_listener_enabled"] ?: false),
            "dnd_ready" to (permissionSummary["dnd_ready"] ?: false),
            "device_settings_ready" to (permissionSummary["device_settings_ready"] ?: false),
            "provider_uri" to "content://com.example.autotask.provider",
            "version" to "1.0.0"
        )
    }

    private suspend fun disableUnsafeStarterProfiles() {
        listOf("call-direct").forEach { profileId ->
            val profile = profileDao.getProfileById(profileId)
            if (profile != null && profile.isEnabled) {
                profileDao.setProfileEnabled(profileId, false)
            }
        }
    }
}
