package com.example.domain

import androidx.test.core.app.ApplicationProvider
import com.example.application.AutomationCommandFacade
import com.example.data.AutomationProfile
import com.example.data.PolicySeeder
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import com.example.engine.SchemaProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationDefinitionTest {

    @Before
    fun setUp() {
        DefinitionCompiler.resetForTests()
    }

    @After
    fun tearDown() {
        DefinitionCompiler.resetForTests()
    }

    @Test
    fun validCompatibilityJsonRoundTripsThroughCanonicalForm() {
        val input = JSONObject(
            """
            {
              "id": "battery-low",
              "name": "Low battery",
              "description": "Speak when battery is low",
              "isEnabled": true,
              "triggerType": "BATTERY",
              "triggerConfig": {"levelBelow": 20, "isCharging": false},
              "conditions": {"isScreenOn": true},
              "actions": [{"type": "SPEAK", "params": {"text": "Battery low"}}],
              "cooldownMs": 300000,
              "priority": 10
            }
            """.trimIndent()
        )

        val compiled = DefinitionCompiler.compile(input)
        val canonical = DefinitionCodec.toCanonicalJson(compiled.definition)
        val again = DefinitionCompiler.compile(canonical).definition

        assertEquals("battery-low", again.id)
        assertEquals(AutomationSchema.CURRENT_VERSION, again.schemaVersion)
        assertEquals("BATTERY", again.trigger.type)
        assertEquals(20, again.trigger.config.fields.getValue("levelBelow").asIntOrNull())
        assertEquals(false, again.trigger.config.fields.getValue("isCharging").asBooleanOrNull())
        assertEquals(1, again.conditions.size)
        assertEquals("isScreenOn", again.conditions[0].type)
        assertEquals("SPEAK", again.steps[0].type)
        assertEquals(300000L, again.executionPolicy.cooldownMs)
        assertEquals(10, again.executionPolicy.priority)
        assertEquals("low", again.riskPolicy.maxRisk)
        assertFalse(again.riskPolicy.requireConfirmation)
        assertEquals(again, DefinitionCompiler.compile(canonical).definition)
    }

    @Test
    fun unknownTriggerFieldIsRejected() {
        val error = compileExpectingError(
            validBatteryJson().put("triggerConfig", JSONObject().put("level", 20))
        )
        assertTrue(error.errors.any { it.message.contains("unknown field 'level'") })
    }

    @Test
    fun unknownActionTypeIsRejected() {
        val error = compileExpectingError(
            validBatteryJson().put(
                "actions",
                org.json.JSONArray().put(JSONObject().put("type", "NOTIFY").put("params", JSONObject()))
            )
        )
        assertTrue(error.errors.any { it.message.contains("unknown action type 'NOTIFY'") })
    }

    @Test
    fun invalidParameterTypeIsRejected() {
        val error = compileExpectingError(
            validBatteryJson().put("triggerConfig", JSONObject().put("levelBelow", "twenty"))
        )
        assertTrue(error.errors.any { it.message.contains("must be an integer") })
    }

    @Test
    fun futureSchemaVersionIsRejected() {
        val error = compileExpectingError(validBatteryJson().put("schemaVersion", 99))
        assertTrue(error.errors.any { it.path == "schemaVersion" })
    }

    @Test
    fun highRiskActionDerivesConfirmationPolicy() {
        val input = JSONObject(
            """
            {
              "id": "sms-reply",
              "name": "SMS reply",
              "triggerType": "SMS",
              "actions": [{"type": "SEND_SMS", "params": {"number": "+15551212", "text": "ok"}}]
            }
            """.trimIndent()
        )
        val definition = DefinitionCompiler.compile(input).definition
        assertEquals("high", definition.riskPolicy.maxRisk)
        assertTrue(definition.riskPolicy.requireConfirmation)
    }

    @Test
    fun cacheInvalidatesWhenRevisionChanges() {
        val first = DefinitionCompiler.compile(
            AutomationProfile(
                id = "cached",
                name = "First",
                triggerType = "MANUAL",
                triggerConfigJson = "{}",
                actionsJson = """[{"type":"LOG","params":{"message":"one"}}]""",
                revision = 1L
            )
        )
        DefinitionCompiler.put(first)

        assertSame(first, DefinitionCompiler.getOrCompile(first.toProfile(1L, 1L)))

        val updated = first.toProfile(1L, 2L).copy(revision = 2L, name = "Second")
        val second = DefinitionCompiler.getOrCompile(updated)

        assertEquals(2L, second.revision)
        assertEquals("Second", second.definition.name)
        assertNotEquals(first.definition.name, second.definition.name)
        assertSame(second, DefinitionCompiler.cached("cached"))
    }

    @Test
    fun starterProfilesCompileAgainstCurrentSchema() {
        PolicySeeder.getStarterProfiles().forEach { profile ->
            val compiled = DefinitionCompiler.compile(profile)
            assertEquals(profile.id, compiled.id)
            assertEquals(profile.triggerType, compiled.definition.trigger.type)
            assertTrue(compiled.definition.steps.isNotEmpty())
        }
    }

    @Test
    fun schemaJsonAdvertisesCurrentSchemaVersion() {
        val schema = JSONObject(SchemaProvider.getSchemaJson())
        assertEquals(AutomationSchema.CURRENT_VERSION, schema.getInt("schemaVersion"))
        assertTrue(schema.getJSONObject("triggerTypes").has("BATTERY"))
        assertTrue(schema.getJSONObject("actionTypes").has("NOTIFICATION"))
        val schedule = schema.getJSONObject("triggerTypes").getJSONObject("SCHEDULE")
        assertEquals("delivery-ready", schedule.getString("state"))
        assertTrue(schedule.getJSONObject("configKeys").getString("cronExpression").contains("5-field"))
        assertFalse(schedule.getString("description").contains("cron is not implemented"))
    }

    @Test
    fun scheduleRequiresCronOrIntervalAndRejectsInvalidCron() {
        val missing = compileExpectingError(
            JSONObject()
                .put("id", "sched-missing")
                .put("name", "Sched")
                .put("triggerType", "SCHEDULE")
                .put("actions", org.json.JSONArray().put(JSONObject().put("type", "LOG")))
        )
        assertTrue(missing.errors.any { it.message.contains("cronExpression or intervalMs") })

        val badCron = compileExpectingError(
            JSONObject()
                .put("id", "sched-bad")
                .put("name", "Sched")
                .put("triggerType", "SCHEDULE")
                .put("triggerConfig", JSONObject().put("cronExpression", "not-cron"))
                .put("actions", org.json.JSONArray().put(JSONObject().put("type", "LOG")))
        )
        assertTrue(badCron.errors.any { it.path.contains("cronExpression") })
    }

    @Test
    fun timeTriggerRequiresHourAndMinute() {
        val error = compileExpectingError(
            JSONObject()
                .put("id", "time-missing")
                .put("name", "Time")
                .put("triggerType", "TIME")
                .put("actions", org.json.JSONArray().put(JSONObject().put("type", "LOG")))
        )
        assertTrue(error.errors.any { it.path == "trigger.config.hour" })
        assertTrue(error.errors.any { it.path == "trigger.config.minute" })
    }

    @Test
    fun facadeRejectsMalformedProfileBeforePersist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val facade = AutomationCommandFacade.getInstance(context)
        val id = "invalid-pr3-${System.nanoTime()}"

        try {
            facade.upsertProfile(
                JSONObject()
                    .put("id", id)
                    .put("name", "Bad")
                    .put("triggerType", "NOT_A_TRIGGER")
                    .put("actions", org.json.JSONArray().put(JSONObject().put("type", "LOG")))
            )
            fail("expected InvalidAutomationException")
        } catch (_: InvalidAutomationException) {
        }

        assertNull(facade.getProfile(id))
    }

    @Test
    fun facadePersistsRevisionAndValidateDoesNotWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val facade = AutomationCommandFacade.getInstance(context)
        val id = "valid-pr3-${System.nanoTime()}"
        val body = JSONObject()
            .put("id", id)
            .put("name", "Manual log")
            .put("isEnabled", true)
            .put("triggerType", "MANUAL")
            .put("actions", org.json.JSONArray().put(
                JSONObject().put("type", "LOG").put("params", JSONObject().put("message", "hi"))
            ))

        val validated = facade.validateAutomation(body)
        assertEquals("MANUAL", validated.trigger.type)
        assertNull(facade.getProfile(id))

        val saved = facade.upsertProfile(body)
        assertEquals(1L, saved.revision)
        assertEquals(AutomationSchema.CURRENT_VERSION, saved.schemaVersion)
        assertNotNull(facade.getProfile(id))

        val patched = facade.patchProfile(id, JSONObject().put("name", "Manual log v2"))
        assertEquals(2L, patched.revision)
        assertEquals("Manual log v2", patched.name)
    }

    @Test
    fun fireEventReturnsDurableRunId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val facade = AutomationCommandFacade.getInstance(context)
        val id = "run-pr4-${System.nanoTime()}"
        facade.upsertProfile(
            JSONObject()
                .put("id", id)
                .put("name", "Run log")
                .put("isEnabled", true)
                .put("triggerType", "MANUAL")
                .put("actions", org.json.JSONArray().put(
                    JSONObject().put("type", "LOG").put("params", JSONObject().put("message", "hi"))
                ))
        )

        val result = facade.fireEvent(
            JSONObject().put("triggerType", "MANUAL").put("profileId", id)
        )

        assertEquals(1, result.runs.size)
        assertTrue(result.runs[0].run.runId.isNotBlank())
        assertEquals("SUCCESS", result.runs[0].run.status)
        assertEquals(result.runs[0].run.runId, facade.getRun(result.runs[0].run.runId).run.runId)
    }

    @Test
    fun malformedPersistedProfileDoesNotReachExecutor() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = AutoTaskEngine.getInstance(context)
        val id = "legacy-bad-${System.nanoTime()}"
        engine.repository.upsertProfile(
            AutomationProfile(
                id = id,
                name = "Legacy bad",
                isEnabled = true,
                triggerType = "MANUAL",
                triggerConfigJson = "{}",
                actionsJson = """[{"type":"NOT_AN_ACTION"}]"""
            )
        )

        val logs = engine.processEvent(
            AutomationEvent(type = "MANUAL", payload = mapOf("profileId" to id))
        )

        assertTrue(logs.isNotEmpty())
        assertEquals("SKIPPED", logs.first().status)
        assertEquals("invalid_definition", logs.first().skippedReason)
    }

    @Test
    fun savingTimeProfileRegistersObservableNextFire() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val facade = AutomationCommandFacade.getInstance(context)
        val id = "time-pr6-${System.nanoTime()}"
        facade.upsertProfile(
            JSONObject()
                .put("id", id)
                .put("name", "Night")
                .put("isEnabled", true)
                .put("triggerType", "TIME")
                .put("triggerConfig", JSONObject().put("hour", 22).put("minute", 0))
                .put("actions", org.json.JSONArray().put(
                    JSONObject().put("type", "LOG").put("params", JSONObject().put("message", "night"))
                ))
        )

        val schedule = facade.getSchedule(id)
        assertEquals("SCHEDULED", schedule.status)
        assertEquals("EXACT", schedule.delivery)
        assertNotNull(schedule.nextFireAt)
        assertTrue(schedule.nextFireAt!! > System.currentTimeMillis())

        facade.setProfileEnabled(id, false)
        assertEquals("DISABLED", facade.getSchedule(id).status)

        facade.deleteProfile(id)
        try {
            facade.getSchedule(id)
            fail("expected ScheduleNotFoundException")
        } catch (_: com.example.domain.ScheduleNotFoundException) {
        }
    }

    @Test
    fun remoteHighRiskExecuteRequiresStoredApproval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val facade = AutomationCommandFacade.getInstance(context)
        val id = "sms-pr7-${System.nanoTime()}"
        facade.upsertProfile(
            JSONObject()
                .put("id", id)
                .put("name", "SMS")
                .put("isEnabled", true)
                .put("triggerType", "MANUAL")
                .put(
                    "actions",
                    org.json.JSONArray().put(
                        JSONObject().put("type", "SEND_SMS")
                            .put("params", JSONObject().put("number", "+15551212").put("text", "hi"))
                    )
                )
        )
        val remote = com.example.security.CommandContext(
            com.example.security.AccessPrincipal(
                kind = com.example.security.PrincipalKind.PAIRED_CLIENT,
                id = "cred-test",
                scopes = setOf(com.example.security.AccessScope.EXECUTE),
                approvedActions = emptySet()
            )
        )
        try {
            facade.fireEvent(JSONObject().put("triggerType", "MANUAL").put("profileId", id), remote)
            fail("expected ApprovalRequiredException")
        } catch (e: com.example.security.ApprovalRequiredException) {
            assertTrue(e.actions.contains("SEND_SMS"))
        }

        val approved = remote.copy(
            principal = remote.principal.copy(approvedActions = setOf("SEND_SMS"))
        )
        val result = facade.fireEvent(JSONObject().put("triggerType", "MANUAL").put("profileId", id), approved)
        assertTrue(result.runs.isNotEmpty())
    }

    private fun validBatteryJson(): JSONObject = JSONObject(
        """
        {
          "id": "battery-low",
          "name": "Low battery",
          "triggerType": "BATTERY",
          "triggerConfig": {"levelBelow": 20},
          "actions": [{"type": "TOAST", "params": {"text": "low"}}]
        }
        """.trimIndent()
    )

    private fun compileExpectingError(input: JSONObject): InvalidAutomationException {
        return try {
            DefinitionCompiler.compile(input)
            fail("expected InvalidAutomationException")
            throw IllegalStateException("unreachable")
        } catch (e: InvalidAutomationException) {
            e
        }
    }
}
