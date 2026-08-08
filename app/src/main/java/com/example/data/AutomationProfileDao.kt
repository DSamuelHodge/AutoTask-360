package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationProfileDao {
    @Query("SELECT * FROM automation_profiles ORDER BY priority DESC, name ASC")
    fun getAllProfilesFlow(): Flow<List<AutomationProfile>>

    @Query("SELECT * FROM automation_profiles ORDER BY priority DESC, name ASC")
    suspend fun getAllProfiles(): List<AutomationProfile>

    @Query("SELECT * FROM automation_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): AutomationProfile?

    @Query("SELECT * FROM automation_profiles WHERE id = :id LIMIT 1")
    fun getProfileByIdFlow(id: String): Flow<AutomationProfile?>

    @Query("SELECT * FROM automation_profiles WHERE triggerType = :triggerType AND isEnabled = 1 ORDER BY priority DESC")
    suspend fun getEnabledProfilesForTrigger(triggerType: String): List<AutomationProfile>

    @Query("SELECT COUNT(*) FROM automation_profiles")
    suspend fun getProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: AutomationProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<AutomationProfile>)

    @Update
    suspend fun updateProfile(profile: AutomationProfile)

    @Query("UPDATE automation_profiles SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setProfileEnabled(id: String, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE automation_profiles SET lastTriggeredAt = :timestamp WHERE id = :id")
    suspend fun updateLastTriggeredAt(id: String, timestamp: Long)

    @Query("DELETE FROM automation_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String): Int

    @Delete
    suspend fun deleteProfile(profile: AutomationProfile)
}
