package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(AndroidJUnit4::class)
class LegacyAutoTaskMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val legacyFile = File(context.cacheDir, "legacy-autotask-migration.db")
    private val prefs = context.getSharedPreferences(
        "autotask_database_migration_test",
        Context.MODE_PRIVATE
    )

    @After
    fun tearDown() {
        legacyFile.delete()
        prefs.edit().clear().commit()
    }

    @Test
    fun importsProfilesAndLogsFromLegacyDatabase() {
        val legacy = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
        legacy.execSQL(
            """
            CREATE TABLE automation_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                isEnabled INTEGER NOT NULL,
                triggerType TEXT NOT NULL,
                triggerConfigJson TEXT NOT NULL,
                conditionsJson TEXT NOT NULL,
                actionsJson TEXT NOT NULL,
                cooldownMs INTEGER NOT NULL,
                priority INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                lastTriggeredAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        legacy.execSQL(
            """
            CREATE TABLE execution_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                profileId TEXT NOT NULL,
                profileName TEXT NOT NULL,
                triggerType TEXT NOT NULL,
                status TEXT NOT NULL,
                skippedReason TEXT NOT NULL,
                actionsResultJson TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        legacy.execSQL(
            """
            INSERT INTO automation_profiles VALUES
            ('legacy-profile', 'Legacy profile', 'migrated', 1, 'MANUAL', '{}', '{}', '[]', 0, 2, 10, 11, 0)
            """.trimIndent()
        )
        legacy.execSQL(
            """
            INSERT INTO execution_logs VALUES
            (7, 'legacy-profile', 'Legacy profile', 'MANUAL', 'SUCCESS', '', '[]', 12, 13)
            """.trimIndent()
        )
        legacy.close()

        val room = Room.inMemoryDatabaseBuilder(context, AutoTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            LegacyAutoTaskMigration.importIfNeeded(room, legacyFile, prefs)

            runBlocking {
                val profile = room.profileDao().getProfileById("legacy-profile")
                val logs = room.logDao().getLogs(10)
                assertNotNull(profile)
                assertEquals("Legacy profile", profile!!.name)
                assertEquals(1, profile.schemaVersion)
                assertEquals(1L, profile.revision)
                assertEquals(1, logs.size)
                assertEquals(7L, logs.single().id)
            }
        } finally {
            room.close()
        }
    }
}
