package com.example

import com.example.engine.ActionExecutor
import com.example.engine.ActionRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRiskTest {

  @Test
  fun riskForClassifiesEveryActionTypeHandledByExecutor() {
    // Types actually branched in ActionExecutor.runSingleAction (plus grouped constants).
    val handledTypes = setOf(
      "NOTIFICATION", "AUDIO", "DND", "BRIGHTNESS", "SCREEN_TIMEOUT", "ROTATION",
      "FLASHLIGHT", "SPEAK", "TOAST", "VIBRATE", "HTTP", "SEND_SMS", "CALL",
      "OPEN_URL", "LAUNCH_APP", "OPEN_SETTINGS", "CLIPBOARD", "WRITE_FILE",
      "READ_FILE", "BROADCAST", "PROFILE", "WAIT", "LOG", "POWER_SAVE",
      "WIFI_ACTION", "BLUETOOTH_ACTION", "AIRPLANE_MODE_ACTION", "HOTSPOT",
      "NFC_ACTION", "KILL_APP", "CAMERA"
    )

    handledTypes.forEach { type ->
      val risk = ActionRisk.riskFor(type)
      assertTrue("Action $type must have a known risk class", risk != null)
    }
  }

  @Test
  fun messageAndSettingsActionsAreHighRisk() {
    assertTrue(ActionRisk.isHighRisk("SEND_SMS"))
    assertTrue(ActionRisk.isHighRisk("CALL"))
    assertTrue(ActionRisk.isHighRisk("DND"))
    assertTrue(ActionRisk.isHighRisk("BRIGHTNESS"))
  }

  @Test
  fun observeOnlyAndLocalUxAreNotHighRisk() {
    assertFalse(ActionRisk.isHighRisk("READ_FILE"))
    assertFalse(ActionRisk.isHighRisk("TOAST"))
    assertFalse(ActionRisk.isHighRisk("NOTIFICATION"))
  }

  @Test
  fun confirmationRequiredForSmsCallAndDnd() {
    assertTrue(ActionRisk.requiresConfirmation("SEND_SMS"))
    assertTrue(ActionRisk.requiresConfirmation("CALL"))
    assertTrue(ActionRisk.requiresConfirmation("DND"))
  }

  @Test
  fun audioSilentRequiresConfirmationButOtherModesDoNot() {
    assertTrue(ActionRisk.requiresConfirmation("AUDIO", mapOf("ringerMode" to "silent")))
    assertFalse(ActionRisk.requiresConfirmation("AUDIO", mapOf("ringerMode" to "normal")))
    assertFalse(ActionRisk.requiresConfirmation("AUDIO"))
  }

  @Test
  fun lowRiskActionsDoNotRequireConfirmation() {
    assertFalse(ActionRisk.requiresConfirmation("TOAST"))
    assertFalse(ActionRisk.requiresConfirmation("NOTIFICATION"))
  }
}
