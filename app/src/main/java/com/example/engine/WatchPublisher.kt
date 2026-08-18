package com.example.engine

import com.example.domain.AutomationRun
import com.example.domain.RunStatuses
import com.example.domain.StepRun
import org.json.JSONArray
import org.json.JSONObject

object WatchPublisher {
    fun publishDispatch(result: DispatchResult) {
        WatchBus.hub.publish(
            WatchFact(
                kind = if (result.deduplicated) "event.deduped" else "event",
                occurredAt = result.event.receivedAt,
                body = eventBody(result)
            )
        )
    }

    fun publishRun(run: AutomationRun, steps: List<StepRun>) {
        WatchBus.hub.publish(
            WatchFact(
                kind = "run",
                occurredAt = run.finishedAt ?: run.updatedAt,
                body = runBody(run, steps)
            )
        )
    }

    private fun runBody(run: AutomationRun, steps: List<StepRun>): JSONObject {
        val stepArr = JSONArray()
        steps.forEach { step ->
            stepArr.put(
                JSONObject()
                    .put("stepIndex", step.stepIndex)
                    .put("type", step.type)
                    .put("status", step.status)
                    .put("detail", step.detail)
                    .put("effectId", step.effectId ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("runId", run.runId)
            .put("eventId", run.eventId)
            .put("profileId", run.profileId)
            .put("profileName", run.profileName)
            .put("triggerType", run.triggerType)
            .put("status", run.status)
            .put("error", run.error)
            .put("correlationId", run.correlationId)
            .put("steps", stepArr)
    }

    private fun eventBody(result: DispatchResult): JSONObject {
        val event = result.event
        return JSONObject()
            .put("eventId", event.eventId)
            .put("type", event.type)
            .put("source", event.source)
            .put("occurredAt", event.occurredAt)
            .put("receivedAt", event.receivedAt)
            .put("dedupeKey", event.dedupeKey ?: JSONObject.NULL)
            .put("correlationId", event.correlationId)
            .put("payload", JSONObject(event.payload.toCompactString()))
            .put("deduplicated", result.deduplicated)
            .put("runIds", org.json.JSONArray(result.runs.map { it.run.runId }))
            .put("statuses", org.json.JSONArray(result.runs.map { it.run.status }))
    }
}
