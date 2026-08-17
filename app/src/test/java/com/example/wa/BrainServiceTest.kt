package com.example.wa

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * QA unit tests for the brain supervisor's configuration surface — the pieces
 * of [BrainService] that are pure Kotlin and runnable on the host (Robolectric).
 *
 * Native/spawn/sync behaviour is exercised by the Rust `agent-cal-crm` suite and
 * by the on-device checks; these tests guard the Android-side glue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrainServiceTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `auth token is stable and has the expected shape`() {
    val first = BrainService.getToken(context)
    val second = BrainService.getToken(context)

    // Persisted: same value across calls.
    assertEquals(first, second)
    // Format: "cos-" + 32 hex chars (UUID without dashes).
    assertTrue("token must be cos-prefixed: $first", first.startsWith("cos-"))
    assertEquals(4 + 32, first.length)
    assertTrue(first.substring(4).all { it.isDigit() || it in 'a'..'f' })
  }

  @Test
  fun `brain dir paths resolve under the private brain directory`() {
    val db = File(BrainService.dbPath(context))
    val log = File(BrainService.logPath(context))
    val sock = File(BrainService.sockPath(context))
    val pid = File(BrainService.pidFile(context))

    assertEquals(BrainService.BRAIN_DATABASE_NAME, db.name)
    assertEquals("app_brain", db.parentFile!!.name)
    assertEquals("cosd.log", log.name)
    assertEquals("cosd.sock", sock.name)
    assertEquals("cosd.pid", pid.name)
    assertTrue(db.absolutePath.startsWith(context.dataDir.absolutePath))
  }

  @Test
  fun `debug tcp defaults to off`() {
    // Production shape: UNIX socket, no loopback TCP. Only flipped on manually.
    assertFalse(BrainService.debugTcp(context))
  }

  @Test
  fun `status json exposes supervisor fields`() {
    val status = BrainService.statusJson()

    assertTrue(status.has("running"))
    assertTrue(status.has("halted"))
    assertTrue(status.has("restart_count"))
    assertTrue(status.has("last_error"))
    assertTrue(status.has("backoff_ms"))
    // Supervisor starts in the un-halted state.
    assertFalse(status.getBoolean("halted"))
    assertEquals(0, status.getInt("restart_count"))
  }
}
