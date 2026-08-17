package com.example.application

import android.content.Context
import com.example.data.AutomationProfile
import com.example.data.ExecutionLog
import com.example.domain.AutomationDefinition
import com.example.domain.CompiledAutomation
import com.example.domain.DefinitionCodec
import com.example.domain.DefinitionCompiler
import com.example.domain.DefinitionValidator
import com.example.domain.EventEnvelope
import com.example.domain.InvalidAutomationException
import com.example.domain.RunNotFoundException
import com.example.domain.RunSnapshot
import com.example.domain.ScheduleFire
import com.example.domain.ScheduleNotFoundException
import com.example.domain.ScheduleRegistration
import com.example.security.AccessPrincipal
import com.example.security.ApprovalRequiredException
import com.example.security.CommandContext
import com.example.security.HighRiskPolicy
import com.example.security.PrincipalKind
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import com.example.engine.CapabilityProvider
import com.example.engine.SchemaProvider
import com.example.server.EventRequestParser
import com.example.server.ParsedEventRequest
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single application boundary for automation commands.
 *
 * Transport adapters (REST, MCP, ContentProvider, and UI) should translate
 * their protocol into this boundary instead of reaching into the repository
 * or engine directly. The persistence and execution implementation can move
 * behind this class without changing those adapters.
 */
class AutomationCommandFacade private constructor(context: Context) {
    private val engine = AutoTaskEngine.getInstance(context.applicationContext)
    private val appContext = context.applicationContext

    val isRunning: Boolean
        get() = engine.isRunning

    val profilesFlow: Flow<List<AutomationProfile>>
        get() = engine.repository.allProfilesFlow

    val logsFlow: Flow<List<ExecutionLog>>
        get() = engine.repository.logsFlow

    suspend fun statusMap(): Map<String, Any> = engine.repository.getStatusMap()

    fun uptimeMs(): Long = engine.getUptimeMs()

    fun schemaJson(): String = SchemaProvider.getSchemaJson()

    fun capabilitiesJson(): String = CapabilityProvider.getCapabilitiesJson(appContext)

    suspend fun listProfiles(): List<AutomationProfile> = engine.repository.profileDao.getAllProfiles()

    suspend fun getProfile(id: String): AutomationProfile? = engine.repository.getProfileById(id)

    suspend fun seedDefaults() = engine.repository.seedDefaultRecipesIfNeeded()

    fun setRunningState(running: Boolean) = engine.setRunningState(running)

    suspend fun setProfileEnabled(id: String, enabled: Boolean) =
        engine.repository.setProfileEnabled(id, enabled)

    suspend fun upsertProfile(profile: AutomationProfile) {
        persistCompiled(DefinitionCompiler.compile(profile), getProfile(profile.id), profile)
    }

    suspend fun updateProfile(profile: AutomationProfile) {
        persistCompiled(DefinitionCompiler.compile(profile), getProfile(profile.id), profile)
    }

    suspend fun deleteProfile(id: String): Boolean {
        val deleted = engine.repository.deleteProfileById(id)
        if (deleted) DefinitionCompiler.invalidate(id)
        return deleted
    }

    fun validateAutomation(input: JSONObject): AutomationDefinition =
        DefinitionCompiler.compile(input).definition

    fun validateAutomation(profile: AutomationProfile): AutomationDefinition =
        DefinitionCompiler.compile(profile).definition

    suspend fun saveAutomation(definition: AutomationDefinition): AutomationProfile {
        DefinitionValidator.validate(definition)
        val existing = getProfile(definition.id)
        return persistCompiled(CompiledAutomation(definition), existing, existing)
    }

    suspend fun upsertProfile(input: JSONObject): AutomationProfile {
        val existing = if (input.has("id")) getProfile(input.optString("id")) else null
        return persistCompiled(DefinitionCompiler.compile(input, existing?.revision ?: 0L), existing, existing)
    }

    suspend fun patchProfile(id: String, input: JSONObject): AutomationProfile {
        val existing = getProfile(id) ?: throw ProfileNotFoundException(id)
        return persistCompiled(DefinitionCompiler.compilePatch(existing, input), existing, existing)
    }

    private suspend fun persistCompiled(
        compiled: CompiledAutomation,
        existing: AutomationProfile?,
        timestamps: AutomationProfile?
    ): AutomationProfile {
        val now = System.currentTimeMillis()
        val nextRevision = (existing?.revision ?: 0L) + 1L
        val versioned = compiled.copy(
            definition = compiled.definition.copy(revision = nextRevision)
        )
        val profile = versioned.toProfile(
            createdAt = existing?.createdAt ?: timestamps?.createdAt ?: now,
            updatedAt = now,
            lastTriggeredAt = existing?.lastTriggeredAt ?: timestamps?.lastTriggeredAt ?: 0L
        )
        engine.repository.upsertProfile(profile)
        DefinitionCompiler.put(versioned)
        return profile
    }

    suspend fun fireEvent(input: JSONObject, context: CommandContext = CommandContext.LOCAL): EventCommandResult {
        val request = EventRequestParser.parse(input)
        val targetProfile = if (request.triggerType == "MANUAL" && request.targetProfileId != null) {
            getProfile(request.targetProfileId)
        } else {
            null
        }
        if (request.triggerType == "MANUAL" && request.targetProfileId != null && targetProfile == null) {
            throw ProfileNotFoundException(request.targetProfileId)
        }

        val plannedProfiles = if (targetProfile != null) {
            listOf(targetProfile)
        } else {
            engine.repository.profileDao.getEnabledProfilesForTrigger(request.triggerType)
        }

        if (request.dryRun) {
            return EventCommandResult(request, plannedProfiles, emptyList())
        }

        enforceRemoteApprovals(context.principal, plannedProfiles)
        return requestRun(request, context)
    }

    suspend fun requestRun(input: JSONObject, context: CommandContext = CommandContext.LOCAL): EventCommandResult =
        requestRun(EventRequestParser.parse(input), context)

    suspend fun requestRun(request: ParsedEventRequest, context: CommandContext = CommandContext.LOCAL): EventCommandResult {
        if (request.triggerType == "MANUAL" && request.targetProfileId != null && getProfile(request.targetProfileId) == null) {
            throw ProfileNotFoundException(request.targetProfileId)
        }
        val planned = if (request.targetProfileId != null) {
            listOfNotNull(getProfile(request.targetProfileId))
        } else {
            engine.repository.profileDao.getEnabledProfilesForTrigger(request.triggerType)
        }
        enforceRemoteApprovals(context.principal, planned)
        val dispatched = engine.dispatch(
            event = EventEnvelope.create(
                type = request.triggerType,
                payload = request.payload,
                source = request.source,
                eventId = request.eventId,
                occurredAt = request.occurredAt,
                dedupeKey = request.dedupeKey,
                correlationId = request.correlationId,
                idempotencyKey = request.idempotencyKey
            ),
            targetProfileId = request.targetProfileId
        )
        return EventCommandResult(
            request = request,
            plannedProfiles = dispatched.plannedProfiles.ifEmpty {
                if (request.targetProfileId != null) listOfNotNull(getProfile(request.targetProfileId))
                else dispatched.plannedProfiles
            },
            logs = dispatched.logs,
            eventId = dispatched.event.eventId,
            correlationId = dispatched.event.correlationId,
            runs = dispatched.runs,
            deduplicated = dispatched.deduplicated
        )
    }

    suspend fun getRun(runId: String): RunSnapshot =
        engine.getRun(runId) ?: throw RunNotFoundException(runId)

    suspend fun listRuns(limit: Int, profileId: String? = null): List<RunSnapshot> =
        engine.listRuns(limit, profileId)

    suspend fun cancelRun(runId: String): RunSnapshot = engine.cancelRun(runId)

    suspend fun retryRun(runId: String): RunSnapshot = engine.retryRun(runId)

    suspend fun resumeRun(runId: String): RunSnapshot = engine.resumeRun(runId)

    suspend fun listSchedules(): List<ScheduleRegistration> = engine.listSchedules()

    suspend fun getSchedule(profileId: String): ScheduleRegistration =
        engine.getSchedule(profileId) ?: throw ScheduleNotFoundException(profileId)

    suspend fun reconcileSchedules(reason: String = "manual"): List<ScheduleRegistration> =
        engine.reconcileSchedules(reason)

    suspend fun deliverSchedule(scheduleId: String, scheduledFor: Long): ScheduleFire? =
        engine.deliverSchedule(scheduleId, scheduledFor)

    suspend fun processEvent(event: AutomationEvent): List<ExecutionLog> = engine.processEvent(event)

    private fun enforceRemoteApprovals(principal: AccessPrincipal, profiles: List<AutomationProfile>) {
        if (principal.kind != PrincipalKind.PAIRED_CLIENT) return
        val needed = profiles.flatMap { HighRiskPolicy.requiredApprovals(it) }.distinct()
        val missing = HighRiskPolicy.missingApprovals(needed, principal.approvedActions)
        if (missing.isNotEmpty()) throw ApprovalRequiredException(missing)
    }

    suspend fun listLogs(limit: Int): List<ExecutionLog> =
        engine.repository.logDao.getLogs(limit.coerceIn(1, 500))

    suspend fun clearLogs(): Int = engine.repository.clearLogs()

    companion object {
        @Volatile
        private var instance: AutomationCommandFacade? = null

        fun getInstance(context: Context): AutomationCommandFacade {
            return instance ?: synchronized(this) {
                instance ?: AutomationCommandFacade(context).also { instance = it }
            }
        }

        fun profileToJson(profile: AutomationProfile): JSONObject {
            val json = JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("description", profile.description)
                .put("isEnabled", profile.isEnabled)
                .put("enabled", profile.isEnabled)
                .put("triggerType", profile.triggerType)
                .put("triggerConfigJson", jsonObjectOrString(profile.triggerConfigJson))
                .put("conditionsJson", jsonObjectOrString(profile.conditionsJson))
                .put("actionsJson", jsonArrayOrString(profile.actionsJson))
                .put("cooldownMs", profile.cooldownMs)
                .put("priority", profile.priority)
                .put("createdAt", profile.createdAt)
                .put("updatedAt", profile.updatedAt)
                .put("lastTriggeredAt", profile.lastTriggeredAt)
                .put("schemaVersion", profile.schemaVersion)
                .put("revision", profile.revision)
            try {
                val compiled = DefinitionCompiler.compile(profile)
                val canonical = DefinitionCodec.toCanonicalJson(compiled.definition)
                json.put("trigger", canonical.getJSONObject("trigger"))
                json.put("conditions", jsonObjectOrString(profile.conditionsJson))
                json.put("steps", canonical.getJSONArray("steps"))
                json.put("executionPolicy", canonical.getJSONObject("executionPolicy"))
                json.put("riskPolicy", canonical.getJSONObject("riskPolicy"))
            } catch (_: InvalidAutomationException) {
                json.put("trigger", JSONObject().put("type", profile.triggerType).put("config", jsonObjectOrString(profile.triggerConfigJson)))
                json.put("steps", jsonArrayOrString(profile.actionsJson))
            }
            return json
        }

        fun definitionToJson(definition: AutomationDefinition): JSONObject =
            DefinitionCodec.toCanonicalJson(definition)

        fun logToJson(log: ExecutionLog): JSONObject = JSONObject()
            .put("id", log.id)
            .put("profileId", log.profileId)
            .put("profileName", log.profileName)
            .put("triggerType", log.triggerType)
            .put("status", log.status)
            .put("skippedReason", log.skippedReason)
            .put("actionsResultJson", jsonArrayOrString(log.actionsResultJson))
            .put("durationMs", log.durationMs)
            .put("timestamp", log.timestamp)
            .put("runId", log.runId)

        fun runToJson(snapshot: RunSnapshot): JSONObject {
            val steps = JSONArray()
            snapshot.steps.forEach { step ->
                steps.put(
                    JSONObject()
                        .put("stepRunId", step.stepRunId)
                        .put("stepIndex", step.stepIndex)
                        .put("type", step.type)
                        .put("status", step.status)
                        .put("detail", step.detail)
                        .put("attempt", step.attempt)
                        .put("startedAt", step.startedAt ?: JSONObject.NULL)
                        .put("finishedAt", step.finishedAt ?: JSONObject.NULL)
                )
            }
            val run = snapshot.run
            return JSONObject()
                .put("runId", run.runId)
                .put("eventId", run.eventId)
                .put("profileId", run.profileId)
                .put("profileName", run.profileName)
                .put("profileRevision", run.profileRevision)
                .put("triggerType", run.triggerType)
                .put("correlationId", run.correlationId)
                .put("status", run.status)
                .put("currentStepIndex", run.currentStepIndex)
                .put("attempt", run.attempt)
                .put("maxAttempts", run.maxAttempts)
                .put("skippedReason", run.skippedReason)
                .put("error", run.error)
                .put("createdAt", run.createdAt)
                .put("updatedAt", run.updatedAt)
                .put("startedAt", run.startedAt ?: JSONObject.NULL)
                .put("finishedAt", run.finishedAt ?: JSONObject.NULL)
                .put("wakeAt", run.wakeAt ?: JSONObject.NULL)
                .put("retryOfRunId", run.retryOfRunId ?: JSONObject.NULL)
                .put("durationMs", run.durationMs)
                .put("steps", steps)
        }

        fun scheduleToJson(registration: ScheduleRegistration): JSONObject = JSONObject()
            .put("scheduleId", registration.scheduleId)
            .put("profileId", registration.profileId)
            .put("profileRevision", registration.profileRevision)
            .put("triggerType", registration.triggerType)
            .put("delivery", registration.delivery)
            .put("timezone", registration.timezone)
            .put("nextFireAt", registration.nextFireAt ?: JSONObject.NULL)
            .put("lastFiredAt", registration.lastFiredAt ?: JSONObject.NULL)
            .put("lastDeliveryId", registration.lastDeliveryId ?: JSONObject.NULL)
            .put("missedCount", registration.missedCount)
            .put("status", registration.status)
            .put("error", registration.error)
            .put("updatedAt", registration.updatedAt)

        fun eventResultToJson(result: EventCommandResult): JSONObject {
            val profiles = JSONArray()
            result.plannedProfiles.forEach { profile ->
                profiles.put(
                    JSONObject()
                        .put("id", profile.id)
                        .put("name", profile.name)
                        .put("triggerType", profile.triggerType)
                        .put("isEnabled", profile.isEnabled)
                )
            }
            val logs = JSONArray()
            result.logs.forEach { logs.put(logToJson(it)) }
            val runs = JSONArray()
            result.runs.forEach { runs.put(runToJson(it)) }
            return JSONObject()
                .put("status", "OK")
                .put("dryRun", result.request.dryRun)
                .put("triggerType", result.request.triggerType)
                .put("targetProfileId", result.request.targetProfileId ?: JSONObject.NULL)
                .put("profilesMatched", result.plannedProfiles.size)
                .put("logsGenerated", result.logs.size)
                .put("plannedProfiles", profiles)
                .put("results", logs)
                .put("eventId", result.eventId)
                .put("correlationId", result.correlationId)
                .put("deduplicated", result.deduplicated)
                .put("runId", result.runs.firstOrNull()?.run?.runId ?: JSONObject.NULL)
                .put("runs", runs)
        }

        private fun jsonObjectOrString(value: String): Any = try {
            JSONObject(value)
        } catch (_: Exception) {
            value
        }

        private fun jsonArrayOrString(value: String): Any = try {
            JSONArray(value)
        } catch (_: Exception) {
            value
        }
    }
}

data class EventCommandResult(
    val request: ParsedEventRequest,
    val plannedProfiles: List<AutomationProfile>,
    val logs: List<ExecutionLog>,
    val eventId: String = "",
    val correlationId: String = "",
    val runs: List<RunSnapshot> = emptyList(),
    val deduplicated: Boolean = false
)

class ProfileNotFoundException(val profileId: String) : RuntimeException("Profile not found: $profileId")
