package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.wa.BrainService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoTaskDatabasePathTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `Room and Rust database paths are independent`() {
        val room = AutoTaskDatabase.databasePath(context)
        val rust = File(BrainService.dbPath(context))

        assertEquals(AutoTaskDatabase.DATABASE_NAME, room.name)
        assertEquals(BrainService.BRAIN_DATABASE_NAME, rust.name)
        assertTrue(room.absolutePath.startsWith(context.dataDir.absolutePath))
        assertTrue(rust.absolutePath.startsWith(context.dataDir.absolutePath))
        assertNotEquals(room.absolutePath, rust.absolutePath)
    }
}
