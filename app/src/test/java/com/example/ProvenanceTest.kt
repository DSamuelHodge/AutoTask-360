package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.engine.ExecutionPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProvenanceTest {

  private lateinit var repository: AutoTaskRepository
  private val profileId = "prov-test-${System.currentTimeMillis()}"

  @Before
  fun setUp() {
    ExecutionPolicy.reset()
    val context = ApplicationProvider.getApplicationContext<Context>()
    repository = AutoTaskRepository(context)
  }

  @After
  fun tearDown() {
    ExecutionPolicy.reset()
    runBlocking { repository.deleteProfileById(profileId) }
  }

  @Test
  fun localWriteDefaultsProvenanceToLocal() {
    val profile = baseProfile()
    runBlocking { repository.upsertProfileWithProvenance(profile, callerIdentity = "local") }
    val stored = runBlocking { repository.getProfileById(profileId) }
    assertEquals("local", stored?.createdBy)
    assertEquals("local", stored?.modifiedBy)
    assertEquals("local", stored?.sourceSurface)
  }

  @Test
  fun agentWriteRecordsCallerIdentityAndSourceSurface() {
    val profile = baseProfile()
    runBlocking {
      repository.upsertProfileWithProvenance(
        profile = profile,
        callerIdentity = "agent:relay-client",
        sourceSurface = "agent",
        reason = "CoS trust contract"
      )
    }
    val stored = runBlocking { repository.getProfileById(profileId) }
    assertEquals("agent:relay-client", stored?.createdBy)
    assertEquals("agent:relay-client", stored?.modifiedBy)
    assertEquals("agent", stored?.sourceSurface)
    assertEquals("CoS trust contract", stored?.reason)
  }

  @Test
  fun agentUpdateKeepsOriginalCreatorButUpdatesModifier() {
    runBlocking {
      repository.upsertProfileWithProvenance(baseProfile(), callerIdentity = "agent:first", sourceSurface = "agent")
    }
    runBlocking {
      repository.upsertProfileWithProvenance(baseProfile(), callerIdentity = "agent:second", sourceSurface = "agent")
    }
    val stored = runBlocking { repository.getProfileById(profileId) }
    assertEquals("agent:first", stored?.createdBy)
    assertEquals("agent:second", stored?.modifiedBy)
  }

  @Test
  fun agentWriteRejectedWhenKillSwitchDisabled() {
    ExecutionPolicy.agentWritesEnabled = false
    var threw = false
    try {
      runBlocking {
        repository.upsertProfileWithProvenance(baseProfile(), callerIdentity = "agent:x", sourceSurface = "agent")
      }
    } catch (e: com.example.engine.ExecutionPolicy.AgentWriteDisabledException) {
      threw = true
    }
    assertTrue("agent write should be rejected when agentWritesEnabled=false", threw)
    val stored = runBlocking { repository.getProfileById(profileId) }
    assertEquals("profile should not have been written", null, stored)
  }

  private fun baseProfile() = AutomationProfile(
    id = profileId,
    name = "Provenance Test",
    isEnabled = true,
    triggerType = "MANUAL",
    actionsJson = "[]"
  )

  private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
}
