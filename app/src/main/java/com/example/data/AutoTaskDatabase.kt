package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AutomationProfile::class, ExecutionLog::class],
    version = 2,
    exportSchema = false
)
abstract class AutoTaskDatabase : RoomDatabase() {
    abstract fun profileDao(): AutomationProfileDao
    abstract fun logDao(): ExecutionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AutoTaskDatabase? = null

        // Version 1 -> 2: add provenance columns (#19). Existing rows get DEFAULT "local".
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE automation_profiles ADD COLUMN createdBy TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE automation_profiles ADD COLUMN modifiedBy TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE automation_profiles ADD COLUMN sourceSurface TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE automation_profiles ADD COLUMN reason TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): AutoTaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoTaskDatabase::class.java,
                    "autotask.db"
                )
                    .addMigration(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
