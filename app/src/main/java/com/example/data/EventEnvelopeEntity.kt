package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.EventEnvelope
import com.example.domain.JsonValue

@Entity(
    tableName = "event_envelopes",
    indices = [
        Index(value = ["type", "dedupeKey"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class EventEnvelopeEntity(
    @PrimaryKey val eventId: String,
    val type: String,
    val source: String,
    val occurredAt: Long,
    val receivedAt: Long,
    val dedupeKey: String? = null,
    val correlationId: String,
    val payloadJson: String,
    val idempotencyKey: String? = null
) {
    fun toDomain(): EventEnvelope = EventEnvelope(
        eventId = eventId,
        type = type,
        source = source,
        occurredAt = occurredAt,
        receivedAt = receivedAt,
        dedupeKey = dedupeKey,
        correlationId = correlationId,
        payload = JsonValue.parseObject(payloadJson),
        idempotencyKey = idempotencyKey
    )

    companion object {
        fun from(event: EventEnvelope) = EventEnvelopeEntity(
            eventId = event.eventId,
            type = event.type,
            source = event.source,
            occurredAt = event.occurredAt,
            receivedAt = event.receivedAt,
            dedupeKey = event.dedupeKey,
            correlationId = event.correlationId,
            payloadJson = event.payload.toCompactString(),
            idempotencyKey = event.idempotencyKey
        )
    }
}
