package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScheduleRegistrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduleRegistrationEntity)

    @Query("SELECT * FROM schedule_registrations WHERE scheduleId = :scheduleId LIMIT 1")
    suspend fun getById(scheduleId: String): ScheduleRegistrationEntity?

    @Query("SELECT * FROM schedule_registrations ORDER BY nextFireAt IS NULL, nextFireAt ASC")
    suspend fun list(): List<ScheduleRegistrationEntity>

    @Query("DELETE FROM schedule_registrations WHERE scheduleId = :scheduleId")
    suspend fun delete(scheduleId: String): Int

    @Query("SELECT COUNT(*) FROM schedule_registrations WHERE status = 'SCHEDULED'")
    suspend fun scheduledCount(): Int
}
