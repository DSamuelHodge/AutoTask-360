package com.example

import com.example.mcp.McpTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class McpToolsTest {

  @Test
  fun requiredFieldsArePreservedInToolSchemas() {
    val tool = McpTools.byName.getValue("autotask.profiles.upsert")
    val properties = tool.params.getJSONObject("properties")
    val required = tool.params.getJSONArray("required")

    assertNotNull(properties.getJSONObject("id"))
    assertNotNull(properties.getJSONObject("name"))
    assertNotNull(properties.getJSONObject("triggerType"))
    assertEquals("string", properties.getJSONObject("id").getString("type"))
    assertTrue((0 until required.length()).map { required.getString(it) }.containsAll(listOf("id", "name", "triggerType")))
  }

  @Test
  fun automationToolsAreExposedBeforeAwareTools() {
    val toolNames = McpTools.tools.map { it.name }

    assertTrue(toolNames.indexOf("autotask.schema") >= 0)
    assertTrue(toolNames.indexOf("autotask.events.fire") >= 0)
    assertTrue(toolNames.indexOf("autotask.profiles.validate") >= 0)
    assertTrue(toolNames.indexOf("autotask.runs.request") >= 0)
    assertTrue(toolNames.indexOf("autotask.runs.get") >= 0)
    assertTrue(toolNames.indexOf("autotask.schedules.list") >= 0)
    assertTrue(toolNames.indexOf("autotask.schedules.get") >= 0)
    assertTrue(toolNames.indexOf("autotask.schema") < toolNames.indexOf("aware.sms"))
    assertTrue(McpTools.byName.getValue("autotask.schema").description.contains("AutoTask 2.0"))
    val advertised = org.json.JSONObject(com.example.engine.SchemaProvider.getSchemaJson())
      .getJSONArray("mcpTools")
    val advertisedNames = (0 until advertised.length()).map { advertised.getString(it) }
    val live = McpTools.tools.map { it.name }.filter { it.startsWith("autotask.") }
    assertEquals(live, advertisedNames)
  }
}
