package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutoTaskRepository
import com.example.data.AutoTaskRepository
import com.example.engine.ActionExecutor
import com.example.engine.AutomationEvent
import com.example.engine.CapabilityProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DndAudioRegressionTest {

  private lateinit var context: Context
  private lateinit var executor: ActionExecutor

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    executor = ActionExecutor(context, AutoTaskRepository(context))
  }

  @After
  fun tearDown() {
    CapabilityProvider.notificationPolicyAccessOverride = null
  }

  private fun profileWith(actionsJson: String): AutomationProfile =
    AutomationProfile(id = "t", name = "t", triggerType = "MANUAL", actionsJson = actionsJson)

  @Test
  fun dndWithoutPolicyAccessReturnsSkippedNotFailed() {
    CapabilityProvider.notificationPolicyAccessOverride = false
    val profile = profileWith("""[{"type":"DND","params":{"enabled":true,"policy":"priority"}}]""")
    val (status, results) = runBlocking {
      executor.executeActions(profile, AutomationEvent(type = "MANUAL"))
    }
    assertEquals("SKIPPED", results.first().status)
    assertTrue(results.first().detail.contains("Notification Policy Access not granted for DND"))
    assertEquals("FAILED", status)
  }

  @Test
  fun audioSilentWithoutPolicyAccessReturnsSkipped() {
    CapabilityProvider.notificationPolicyAccessOverride = false
    val profile = profileWith("""[{"type":"AUDIO","params":{"ringerMode":"silent"}}]""")
    val (_, results) = runBlocking {
      executor.executeActions(profile, AutomationEvent(type = "MANUAL"))
    }
    assertEquals("SKIPPED", results.first().status)
    assertTrue(results.first().detail.contains("ringerMode=silent"))
  }

  @Test
  fun audioNormalDoesNotRequirePolicyAccess() {
    CapabilityProvider.notificationPolicyAccessOverride = false
    val profile = profileWith("""[{"type":"AUDIO","params":{"ringerMode":"normal"}}]""")
    val (_, results) = runBlocking {
      executor.executeActions(profile, AutomationEvent(type = "MANUAL"))
    }
    assertEquals("OK", results.first().status)
  }
}
