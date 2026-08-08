package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AutomationProfile::class, ExecutionLog::class],
    version = 1,
    exportSchema = false
)
abstract class AutoTaskDatabase : RoomDatabase() {
    abstract fun profileDao(): AutomationProfileDao
    abstract fun logDao(): ExecutionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AutoTaskDatabase? = null

        fun getInstance(context: Context): AutoTaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoTaskDatabase::class.java,
                    "autotask.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
