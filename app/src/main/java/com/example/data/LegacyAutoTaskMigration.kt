package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.RoomDatabase
import java.io.File

/**
 * Imports the Android-owned tables from the pre-PR2 shared database.
 *
 * The legacy file is opened read-only and is never deleted or written. That
 * keeps the migration compatible with a Rust brain that may still own the
 * file during an application upgrade. Once imported, Room only uses
 * `databases/autotask.db`.
 */
internal object LegacyAutoTaskMigration {
    private const val TAG = "AutoTaskDbMigration"
    private const val PREFS = "autotask_database_migration"
    private const val COMPLETE_KEY = "legacy_shared_db_v1_complete"
    private const val PROFILES_TABLE = "automation_profiles"
    private const val LOGS_TABLE = "execution_logs"

    fun importIfNeeded(context: Context, roomDatabase: RoomDatabase) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        importIfNeeded(
            roomDatabase = roomDatabase,
            legacyFile = File(com.example.wa.BrainService.dbPath(context)),
            prefs = prefs
        )
    }

    internal fun importIfNeeded(
        roomDatabase: RoomDatabase,
        legacyFile: File,
        prefs: android.content.SharedPreferences,
    ) {
        if (prefs.getBoolean(COMPLETE_KEY, false)) return

        if (!legacyFile.exists() || legacyFile.length() == 0L) {
            markComplete(prefs)
            return
        }

        var legacy: SQLiteDatabase? = null
        try {
            legacy = SQLiteDatabase.openDatabase(
                legacyFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val hasProfiles = hasTable(legacy, PROFILES_TABLE)
            val hasLogs = hasTable(legacy, LOGS_TABLE)
            if (!hasProfiles && !hasLogs) {
                markComplete(prefs)
                return
            }

            val target = roomDatabase.openHelper.writableDatabase
            target.beginTransaction()
            try {
                if (hasProfiles) copyProfiles(legacy, target)
                if (hasLogs) copyLogs(legacy, target)
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
            markComplete(prefs)
        } catch (e: Exception) {
            // Leave the marker unset so a later startup can retry after a
            // transient lock or an interrupted upgrade.
            Log.w(TAG, "Legacy AutoTask database import deferred", e)
        } finally {
            legacy?.close()
        }
    }

    private fun hasTable(database: SQLiteDatabase, table: String): Boolean {
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(table)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun copyProfiles(source: SQLiteDatabase, target: androidx.sqlite.db.SupportSQLiteDatabase) {
        source.rawQuery("SELECT * FROM $PROFILES_TABLE", null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues().apply {
                    put("id", cursor.string("id"))
                    put("name", cursor.string("name"))
                    put("description", cursor.string("description"))
                    put("isEnabled", cursor.int("isEnabled"))
                    put("triggerType", cursor.string("triggerType"))
                    put("triggerConfigJson", cursor.string("triggerConfigJson"))
                    put("conditionsJson", cursor.string("conditionsJson"))
                    put("actionsJson", cursor.string("actionsJson"))
                    put("cooldownMs", cursor.long("cooldownMs"))
                    put("priority", cursor.int("priority"))
                    put("createdAt", cursor.long("createdAt"))
                    put("updatedAt", cursor.long("updatedAt"))
                    put("lastTriggeredAt", cursor.long("lastTriggeredAt"))
                }
                target.insert(PROFILES_TABLE, SQLiteDatabase.CONFLICT_IGNORE, values)
            }
        }
    }

    private fun copyLogs(source: SQLiteDatabase, target: androidx.sqlite.db.SupportSQLiteDatabase) {
        source.rawQuery("SELECT * FROM $LOGS_TABLE", null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues().apply {
                    put("id", cursor.long("id"))
                    put("profileId", cursor.string("profileId"))
                    put("profileName", cursor.string("profileName"))
                    put("triggerType", cursor.string("triggerType"))
                    put("status", cursor.string("status"))
                    put("skippedReason", cursor.string("skippedReason"))
                    put("actionsResultJson", cursor.string("actionsResultJson"))
                    put("durationMs", cursor.long("durationMs"))
                    put("timestamp", cursor.long("timestamp"))
                }
                target.insert(LOGS_TABLE, SQLiteDatabase.CONFLICT_IGNORE, values)
            }
        }
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun markComplete(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean(COMPLETE_KEY, true).apply()
    }
}
