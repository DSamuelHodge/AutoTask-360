package com.example

import com.example.data.PolicySeeder
import com.example.domain.DefinitionCompiler
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PolicySeederTest {

  @Test
  fun starterProfilesHaveValidJsonAndKnownActions() {
    val knownActions = setOf(
      "NOTIFICATION",
      "AUDIO",
      "DND",
      "BRIGHTNESS",
      "SCREEN_TIMEOUT",
      "ROTATION",
      "FLASHLIGHT",
      "SPEAK",
      "TOAST",
      "VIBRATE",
      "HTTP",
      "SEND_SMS",
      "CALL",
      "OPEN_URL",
      "LAUNCH_APP",
      "OPEN_SETTINGS",
      "CLIPBOARD",
      "WRITE_FILE",
      "READ_FILE",
      "BROADCAST",
      "PROFILE",
      "WAIT",
      "LOG",
      "POWER_SAVE",
      "WIFI_ACTION",
      "BLUETOOTH_ACTION",
      "AIRPLANE_MODE_ACTION",
      "HOTSPOT",
      "NFC_ACTION",
      "KILL_APP",
      "CAMERA"
    )

    val profiles = PolicySeeder.getStarterProfiles()
    assertTrue("Expected starter profiles", profiles.isNotEmpty())

    profiles.forEach { profile ->
      JSONObject(profile.triggerConfigJson)
      JSONObject(profile.conditionsJson)

      val actions = JSONArray(profile.actionsJson)
      assertTrue("${profile.id} should define at least one action", actions.length() > 0)

      for (i in 0 until actions.length()) {
        val actionType = actions.getJSONObject(i).getString("type").uppercase()
        assertTrue("${profile.id} uses unknown action $actionType", actionType in knownActions)
      }

      DefinitionCompiler.compile(profile)
    }
  }

  @Test
  fun starterProfileIdsAreUnique() {
    val profiles = PolicySeeder.getStarterProfiles()
    val ids = profiles.map { it.id }
    assertFalse("Expected more than one starter profile", ids.size <= 1)
    assertTrue("Starter profile ids must be unique", ids.size == ids.toSet().size)
  }
}
