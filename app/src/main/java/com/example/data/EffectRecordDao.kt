package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EffectRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EffectRecordEntity)

    @Query("SELECT * FROM effect_records WHERE effectId = :effectId LIMIT 1")
    suspend fun getById(effectId: String): EffectRecordEntity?

    @Query("DELETE FROM effect_records WHERE completedAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
