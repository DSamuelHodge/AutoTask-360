package com.example.data

import com.example.domain.AutomationRun
import com.example.domain.EventEnvelope
import com.example.domain.StepRun
import com.example.engine.RunStore

class RoomRunStore(database: AutoTaskDatabase) : RunStore {
    private val events = database.eventDao()
    private val runs = database.runDao()
    private val steps = database.stepDao()

    override suspend fun insertEvent(event: EventEnvelope): Boolean {
        return events.insert(EventEnvelopeEntity.from(event)) != -1L
    }

    override suspend fun getEvent(eventId: String): EventEnvelope? =
        events.getById(eventId)?.toDomain()

    override suspend fun findEventByDedupeKey(type: String, dedupeKey: String): EventEnvelope? =
        events.getByDedupeKey(type, dedupeKey)?.toDomain()

    override suspend fun findEventByIdempotencyKey(key: String): EventEnvelope? =
        events.getByIdempotencyKey(key)?.toDomain()

    override suspend fun incompleteEventCount(): Int = runs.incompleteEventCount()

    override suspend fun insertRun(run: AutomationRun) {
        runs.insert(AutomationRunEntity.from(run))
    }

    override suspend fun updateRun(run: AutomationRun) {
        runs.update(AutomationRunEntity.from(run))
    }

    override suspend fun getRun(runId: String): AutomationRun? =
        runs.getById(runId)?.toDomain()

    override suspend fun listRuns(limit: Int, profileId: String?): List<AutomationRun> {
        val rows = if (profileId.isNullOrBlank()) {
            runs.list(limit)
        } else {
            runs.listForProfile(profileId, limit)
        }
        return rows.map { it.toDomain() }
    }

    override suspend fun listRunsForEvent(eventId: String): List<AutomationRun> =
        runs.listForEvent(eventId).map { it.toDomain() }

    override suspend fun listIncompleteRuns(): List<AutomationRun> =
        runs.listIncomplete().map { it.toDomain() }

    override suspend fun upsertStep(step: StepRun) {
        val existing = steps.getByIndex(step.runId, step.stepIndex)
        val entity = StepRunEntity.from(step).let { next ->
            if (existing != null) next.copy(stepRunId = existing.stepRunId) else next
        }
        steps.upsert(entity)
    }

    override suspend fun listSteps(runId: String): List<StepRun> =
        steps.listForRun(runId).map { it.toDomain() }
}
