package com.example

import com.example.data.AutomationProfile
import com.example.engine.AutomationEvent
import com.example.engine.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MatcherTest {

  @Test
  fun batteryConfigMismatchRemainsANonMatch() {
    val profile = testProfile(
      triggerType = "BATTERY",
      triggerConfigJson = """{"levelBelow":20,"isCharging":false}"""
    )

    val result = Matcher.evaluate(
      profile = profile,
      event = AutomationEvent(
        type = "BATTERY",
        payload = mapOf("level" to 80, "levelPercent" to 80, "isCharging" to false)
      )
    )

    assertFalse(result.isMatch)
    assertEquals("config_mismatch", result.skippedReason)
  }

  @Test
  fun targetedManualRunCanBypassTriggerConfig() {
    val profile = testProfile(
      triggerType = "BATTERY",
      triggerConfigJson = """{"levelBelow":20,"isCharging":false}"""
    )

    val result = Matcher.evaluate(
      profile = profile,
      event = AutomationEvent(
        type = "MANUAL",
        payload = mapOf("profileId" to profile.id)
      ),
      evaluateTriggerConfig = false
    )

    assertTrue(result.isMatch)
  }

  @Test
  fun batteryCooldownRemainsANonMatch() {
    val nowMs = 10_000L
    val profile = testProfile(
      triggerType = "BATTERY",
      triggerConfigJson = """{"levelBelow":90}""",
      cooldownMs = 60_000L,
      lastTriggeredAt = nowMs - 1_000L
    )

    val result = Matcher.evaluate(
      profile = profile,
      event = AutomationEvent(
        type = "BATTERY",
        payload = mapOf("level" to 80, "levelPercent" to 80)
      ),
      nowMs = nowMs
    )

    assertFalse(result.isMatch)
    assertEquals("cooldown_active", result.skippedReason)
  }

  @Test
  fun targetedManualRunCanBypassCooldown() {
    val nowMs = 10_000L
    val profile = testProfile(
      triggerType = "BATTERY",
      triggerConfigJson = "{}",
      cooldownMs = 60_000L,
      lastTriggeredAt = nowMs - 1_000L
    )

    val result = Matcher.evaluate(
      profile = profile,
      event = AutomationEvent(
        type = "MANUAL",
        payload = mapOf("profileId" to profile.id)
      ),
      nowMs = nowMs,
      evaluateCooldown = false,
      evaluateTriggerConfig = false
    )

    assertTrue(result.isMatch)
  }

  private fun testProfile(
    triggerType: String,
    triggerConfigJson: String,
    cooldownMs: Long = 0L,
    lastTriggeredAt: Long = 0L
  ) = AutomationProfile(
    id = "test-profile",
    name = "Test Profile",
    isEnabled = true,
    triggerType = triggerType,
    triggerConfigJson = triggerConfigJson,
    actionsJson = "[]",
    cooldownMs = cooldownMs,
    lastTriggeredAt = lastTriggeredAt
  )
}
