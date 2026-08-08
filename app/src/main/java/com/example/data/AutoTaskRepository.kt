package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AutoTaskRepository(context: Context) {
    private val db = AutoTaskDatabase.getInstance(context)
    val profileDao = db.profileDao()
    val logDao = db.logDao()

    val allProfilesFlow: Flow<List<AutomationProfile>> = profileDao.getAllProfilesFlow()
    val logsFlow: Flow<List<ExecutionLog>> = logDao.getLogsFlow(100)

    suspend fun seedDefaultRecipesIfNeeded() {
        if (profileDao.getProfileCount() == 0) {
            val starterProfiles = PolicySeeder.getStarterProfiles()
            profileDao.insertProfiles(starterProfiles)
        }
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
        return mapOf(
            "engine_running" to 1,
            "profile_count" to profileCount,
            "log_count" to logCount,
            "relay_target" to "http://127.0.0.1:8788",
            "provider_uri" to "content://com.example.autotask.provider",
            "version" to "1.0.0"
        )
    }
}
