package com.example

import com.example.server.EventRequestParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventRequestParserTest {

  @Test
  fun topLevelProfileIdTargetsManualPayload() {
    val request = EventRequestParser.parse(
      JSONObject("""{"triggerType":"MANUAL","profileId":"cos-smoke"}""")
    )

    assertEquals("MANUAL", request.triggerType)
    assertEquals("cos-smoke", request.targetProfileId)
    assertEquals("cos-smoke", request.payload["profileId"])
    assertFalse(request.dryRun)
  }

  @Test
  fun snakeCaseAliasesAreAcceptedAndNormalized() {
    val request = EventRequestParser.parse(
      JSONObject("""{"trigger_type":"manual","profile_id":"cos-snake","dry_run":true}""")
    )

    assertEquals("MANUAL", request.triggerType)
    assertEquals("cos-snake", request.targetProfileId)
    assertEquals("cos-snake", request.payload["profileId"])
    assertTrue(request.dryRun)
  }

  @Test
  fun payloadProfileIdIsAcceptedForBackwardCompatibility() {
    val request = EventRequestParser.parse(
      JSONObject("""{"type":"MANUAL","payload":{"profile_id":"cos-payload","manualTriggeredAt":123}}""")
    )

    assertEquals("MANUAL", request.triggerType)
    assertEquals("cos-payload", request.targetProfileId)
    assertEquals("cos-payload", request.payload["profileId"])
    assertEquals(123, (request.payload["manualTriggeredAt"] as Number).toInt())
  }
}
