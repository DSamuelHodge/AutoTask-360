package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AutomationProfile::class,
        ExecutionLog::class,
        EventEnvelopeEntity::class,
        AutomationRunEntity::class,
        StepRunEntity::class,
        ScheduleRegistrationEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AutoTaskDatabase : RoomDatabase() {
    abstract fun profileDao(): AutomationProfileDao
    abstract fun logDao(): ExecutionLogDao
    abstract fun eventDao(): EventEnvelopeDao
    abstract fun runDao(): AutomationRunDao
    abstract fun stepDao(): StepRunDao
    abstract fun scheduleDao(): ScheduleRegistrationDao

    companion object {
        const val DATABASE_NAME = "autotask.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE automation_profiles ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE automation_profiles ADD COLUMN revision INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE execution_logs ADD COLUMN runId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS event_envelopes (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        source TEXT NOT NULL,
                        occurredAt INTEGER NOT NULL,
                        receivedAt INTEGER NOT NULL,
                        dedupeKey TEXT,
                        correlationId TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        idempotencyKey TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_envelopes_type_dedupeKey ON event_envelopes(type, dedupeKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_event_envelopes_idempotencyKey ON event_envelopes(idempotencyKey)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS automation_runs (
                        runId TEXT NOT NULL PRIMARY KEY,
                        eventId TEXT NOT NULL,
                        profileId TEXT NOT NULL,
                        profileName TEXT NOT NULL,
                        profileRevision INTEGER NOT NULL,
                        triggerType TEXT NOT NULL,
                        correlationId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        currentStepIndex INTEGER NOT NULL,
                        attempt INTEGER NOT NULL,
                        maxAttempts INTEGER NOT NULL,
                        skippedReason TEXT NOT NULL,
                        error TEXT NOT NULL,
                        actionsJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        startedAt INTEGER,
                        finishedAt INTEGER,
                        timeoutAt INTEGER,
                        wakeAt INTEGER,
                        retryOfRunId TEXT,
                        durationMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_runs_eventId ON automation_runs(eventId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_runs_profileId ON automation_runs(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_runs_status ON automation_runs(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_runs_retryOfRunId ON automation_runs(retryOfRunId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS step_runs (
                        stepRunId TEXT NOT NULL PRIMARY KEY,
                        runId TEXT NOT NULL,
                        stepIndex INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        attempt INTEGER NOT NULL,
                        startedAt INTEGER,
                        finishedAt INTEGER,
                        continuationJson TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_step_runs_runId ON step_runs(runId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_step_runs_runId_stepIndex ON step_runs(runId, stepIndex)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS schedule_registrations (
                        scheduleId TEXT NOT NULL PRIMARY KEY,
                        profileId TEXT NOT NULL,
                        profileRevision INTEGER NOT NULL,
                        triggerType TEXT NOT NULL,
                        delivery TEXT NOT NULL,
                        timezone TEXT NOT NULL,
                        nextFireAt INTEGER,
                        lastFiredAt INTEGER,
                        lastDeliveryId TEXT,
                        missedCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        error TEXT NOT NULL,
                        configJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_registrations_profileId ON schedule_registrations(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_registrations_status ON schedule_registrations(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_registrations_nextFireAt ON schedule_registrations(nextFireAt)")
            }
        }

        @Volatile
        private var INSTANCE: AutoTaskDatabase? = null

        fun getInstance(context: Context): AutoTaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoTaskDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                LegacyAutoTaskMigration.importIfNeeded(context.applicationContext, instance)
                INSTANCE = instance
                instance
            }
        }

        fun databasePath(context: android.content.Context): java.io.File {
            return context.getDatabasePath(DATABASE_NAME)
        }
    }
}
