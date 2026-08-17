package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AutomationRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AutomationRunEntity)

    @Update
    suspend fun update(entity: AutomationRunEntity)

    @Query("SELECT * FROM automation_runs WHERE runId = :runId LIMIT 1")
    suspend fun getById(runId: String): AutomationRunEntity?

    @Query("SELECT * FROM automation_runs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun list(limit: Int): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE profileId = :profileId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listForProfile(profileId: String, limit: Int): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE eventId = :eventId ORDER BY createdAt ASC")
    suspend fun listForEvent(eventId: String): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE status IN ('QUEUED', 'RUNNING', 'WAITING') ORDER BY createdAt ASC")
    suspend fun listIncomplete(): List<AutomationRunEntity>

    @Query("SELECT COUNT(DISTINCT eventId) FROM automation_runs WHERE status IN ('QUEUED', 'RUNNING', 'WAITING')")
    suspend fun incompleteEventCount(): Int

    @Query("SELECT COUNT(*) FROM automation_runs WHERE status IN ('QUEUED', 'RUNNING', 'WAITING')")
    suspend fun incompleteRunCount(): Int
}
