package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventEnvelopeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: EventEnvelopeEntity): Long

    @Query("SELECT * FROM event_envelopes WHERE eventId = :eventId LIMIT 1")
    suspend fun getById(eventId: String): EventEnvelopeEntity?

    @Query("SELECT * FROM event_envelopes WHERE type = :type AND dedupeKey = :dedupeKey LIMIT 1")
    suspend fun getByDedupeKey(type: String, dedupeKey: String): EventEnvelopeEntity?

    @Query("SELECT * FROM event_envelopes WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getByIdempotencyKey(key: String): EventEnvelopeEntity?

    @Query(
        """
        DELETE FROM event_envelopes
        WHERE receivedAt < :cutoffMs
          AND eventId NOT IN (
            SELECT eventId FROM automation_runs
            WHERE status IN ('QUEUED', 'RUNNING', 'WAITING')
          )
        """
    )
    suspend fun deleteOrphansOlderThan(cutoffMs: Long): Int
}
