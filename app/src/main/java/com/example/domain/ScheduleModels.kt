package com.example.domain

object ScheduleTriggers {
    val TYPES = setOf("TIME", "SCHEDULE", "SUNRISE_SUNSET")

    fun isScheduled(type: String): Boolean = type.uppercase() in TYPES
}

object DeliveryGuarantees {
    const val EXACT = "EXACT"
    const val FLEXIBLE = "FLEXIBLE"
}

object ScheduleStatuses {
    const val SCHEDULED = "SCHEDULED"
    const val DISABLED = "DISABLED"
    const val UNSUPPORTED = "UNSUPPORTED"
    const val ERROR = "ERROR"
    const val MISSED = "MISSED"
}

data class ScheduleRegistration(
    val scheduleId: String,
    val profileId: String,
    val profileRevision: Long,
    val triggerType: String,
    val delivery: String,
    val timezone: String,
    val nextFireAt: Long?,
    val lastFiredAt: Long?,
    val lastDeliveryId: String?,
    val missedCount: Int = 0,
    val status: String,
    val error: String = "",
    val configJson: String = "{}",
    val updatedAt: Long
)

data class NextFire(
    val epochMs: Long,
    val zoneId: String,
    val delivery: String
)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class ScheduleFire(
    val scheduleId: String,
    val profileId: String,
    val triggerType: String,
    val scheduledFor: Long,
    val firedAt: Long,
    val timezone: String,
    val missed: Boolean,
    val deliveryId: String,
    val dedupeKey: String,
    val payload: Map<String, Any?>
) {
    companion object {
        fun deliveryId(profileId: String, scheduledFor: Long): String =
            "$profileId:$scheduledFor"

        fun dedupeKey(profileId: String, scheduledFor: Long): String =
            "schedule:$profileId:$scheduledFor"
    }
}

class ScheduleNotFoundException(val profileId: String) :
    RuntimeException("Schedule not found: $profileId")
