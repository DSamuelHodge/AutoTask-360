package com.example.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File

/**
 * A `SupportSQLiteOpenHelper.Factory` that opens an exact absolute file path.
 *
 * Room's `databaseBuilder(name)` resolves names under the app's `databases/`
 * dir, but Android's `Context.getDatabasePath()` returns an absolute path
 * as-is. By passing the absolute path as the database name AND delegating to
 * the framework factory, Room opens the brain's `<app_brain>/cos.db` file
 * directly. Result: one on-disk database shared by Room (engine tables:
 * profiles, logs) and the Rust daemon's libSQL (CRM/calendar tables) — one
 * file, two drivers.
 */
class SingleFileOpenHelperFactory(
    @Suppress("UNUSED_PARAMETER") private val file: File,
) : SupportSQLiteOpenHelper.Factory {

    private val delegate = FrameworkSQLiteOpenHelperFactory()

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        return delegate.create(configuration)
    }
}
