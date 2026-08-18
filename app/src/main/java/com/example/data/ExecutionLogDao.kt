package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsFlow(limit: Int = 100): Flow<List<ExecutionLog>>

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogs(limit: Int = 100): List<ExecutionLog>

    @Query("SELECT COUNT(*) FROM execution_logs")
    suspend fun getLogCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLog): Long

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs(): Int

    @Query("DELETE FROM execution_logs WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query(
        """
        DELETE FROM execution_logs
        WHERE id NOT IN (
            SELECT id FROM (
                SELECT id FROM execution_logs ORDER BY timestamp DESC LIMIT :keep
            )
        )
        """
    )
    suspend fun trimToNewest(keep: Int): Int
}
