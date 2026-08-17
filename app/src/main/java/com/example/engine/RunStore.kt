package com.example.engine

import com.example.domain.AutomationRun
import com.example.domain.EventEnvelope
import com.example.domain.RunStatuses
import com.example.domain.StepRun
import java.util.concurrent.ConcurrentHashMap

interface RunStore {
    suspend fun insertEvent(event: EventEnvelope): Boolean
    suspend fun getEvent(eventId: String): EventEnvelope?
    suspend fun findEventByDedupeKey(type: String, dedupeKey: String): EventEnvelope?
    suspend fun findEventByIdempotencyKey(key: String): EventEnvelope?
    suspend fun incompleteEventCount(): Int

    suspend fun insertRun(run: AutomationRun)
    suspend fun updateRun(run: AutomationRun)
    suspend fun getRun(runId: String): AutomationRun?
    suspend fun listRuns(limit: Int, profileId: String? = null): List<AutomationRun>
    suspend fun listRunsForEvent(eventId: String): List<AutomationRun>
    suspend fun listIncompleteRuns(): List<AutomationRun>

    suspend fun upsertStep(step: StepRun)
    suspend fun listSteps(runId: String): List<StepRun>
}

class InMemoryRunStore : RunStore {
    private val events = ConcurrentHashMap<String, EventEnvelope>()
    private val runs = ConcurrentHashMap<String, AutomationRun>()
    private val steps = ConcurrentHashMap<String, MutableList<StepRun>>()

    override suspend fun insertEvent(event: EventEnvelope): Boolean {
        return events.putIfAbsent(event.eventId, event) == null
    }

    override suspend fun getEvent(eventId: String): EventEnvelope? = events[eventId]

    override suspend fun findEventByDedupeKey(type: String, dedupeKey: String): EventEnvelope? {
        return events.values.firstOrNull { it.type == type && it.dedupeKey == dedupeKey }
    }

    override suspend fun findEventByIdempotencyKey(key: String): EventEnvelope? {
        return events.values.firstOrNull { it.idempotencyKey == key }
    }

    override suspend fun incompleteEventCount(): Int {
        val incompleteEventIds = runs.values
            .filter { RunStatuses.isIncomplete(it.status) }
            .map { it.eventId }
            .toSet()
        return incompleteEventIds.size
    }

    override suspend fun insertRun(run: AutomationRun) {
        runs[run.runId] = run
        steps.putIfAbsent(run.runId, mutableListOf())
    }

    override suspend fun updateRun(run: AutomationRun) {
        runs[run.runId] = run
    }

    override suspend fun getRun(runId: String): AutomationRun? = runs[runId]

    override suspend fun listRuns(limit: Int, profileId: String?): List<AutomationRun> {
        return runs.values
            .filter { profileId == null || it.profileId == profileId }
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    override suspend fun listRunsForEvent(eventId: String): List<AutomationRun> {
        return runs.values.filter { it.eventId == eventId }.sortedBy { it.createdAt }
    }

    override suspend fun listIncompleteRuns(): List<AutomationRun> {
        return runs.values.filter { RunStatuses.isIncomplete(it.status) }.sortedBy { it.createdAt }
    }

    override suspend fun upsertStep(step: StepRun) {
        val list = steps.getOrPut(step.runId) { mutableListOf() }
        synchronized(list) {
            val index = list.indexOfFirst { it.stepIndex == step.stepIndex }
            if (index >= 0) list[index] = step else list.add(step)
            list.sortBy { it.stepIndex }
        }
    }

    override suspend fun listSteps(runId: String): List<StepRun> {
        val list = steps[runId] ?: return emptyList()
        synchronized(list) { return list.toList() }
    }
}
