package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StepRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StepRunEntity)

    @Query("SELECT * FROM step_runs WHERE runId = :runId ORDER BY stepIndex ASC")
    suspend fun listForRun(runId: String): List<StepRunEntity>

    @Query("SELECT * FROM step_runs WHERE runId = :runId AND stepIndex = :stepIndex LIMIT 1")
    suspend fun getByIndex(runId: String, stepIndex: Int): StepRunEntity?

    @Query("DELETE FROM step_runs WHERE runId NOT IN (SELECT runId FROM automation_runs)")
    suspend fun deleteOrphans(): Int
}
