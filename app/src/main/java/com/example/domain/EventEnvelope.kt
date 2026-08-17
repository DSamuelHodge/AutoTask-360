package com.example.domain

import java.util.UUID

data class EventEnvelope(
    val eventId: String,
    val type: String,
    val source: String,
    val occurredAt: Long,
    val receivedAt: Long,
    val dedupeKey: String?,
    val correlationId: String,
    val payload: JsonValue.ObjectValue,
    val idempotencyKey: String? = null
) {
    companion object {
        fun create(
            type: String,
            payload: Map<String, Any?> = emptyMap(),
            source: String = "internal",
            eventId: String? = null,
            occurredAt: Long? = null,
            receivedAt: Long = System.currentTimeMillis(),
            dedupeKey: String? = null,
            correlationId: String? = null,
            idempotencyKey: String? = null
        ): EventEnvelope {
            val id = eventId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val occurred = occurredAt ?: receivedAt
            return EventEnvelope(
                eventId = id,
                type = type.uppercase(),
                source = source.ifBlank { "internal" },
                occurredAt = occurred,
                receivedAt = receivedAt,
                dedupeKey = dedupeKey?.takeIf { it.isNotBlank() },
                correlationId = correlationId?.takeIf { it.isNotBlank() } ?: id,
                payload = JsonValue.from(payload) as? JsonValue.ObjectValue ?: JsonValue.emptyObject,
                idempotencyKey = idempotencyKey?.takeIf { it.isNotBlank() }
            )
        }
    }
}

fun EventEnvelope.toAutomationEvent(): com.example.engine.AutomationEvent =
    com.example.engine.AutomationEvent(
        type = type,
        timestamp = occurredAt,
        payload = payload.toAnyMap()
    )
