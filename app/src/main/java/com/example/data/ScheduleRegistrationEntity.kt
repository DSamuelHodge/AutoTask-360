package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.ScheduleRegistration

@Entity(
    tableName = "schedule_registrations",
    indices = [
        Index("profileId"),
        Index("status"),
        Index("nextFireAt")
    ]
)
data class ScheduleRegistrationEntity(
    @PrimaryKey val scheduleId: String,
    val profileId: String,
    val profileRevision: Long,
    val triggerType: String,
    val delivery: String,
    val timezone: String,
    val nextFireAt: Long?,
    val lastFiredAt: Long?,
    val lastDeliveryId: String?,
    val missedCount: Int,
    val status: String,
    val error: String,
    val configJson: String,
    val updatedAt: Long
) {
    fun toDomain(): ScheduleRegistration = ScheduleRegistration(
        scheduleId = scheduleId,
        profileId = profileId,
        profileRevision = profileRevision,
        triggerType = triggerType,
        delivery = delivery,
        timezone = timezone,
        nextFireAt = nextFireAt,
        lastFiredAt = lastFiredAt,
        lastDeliveryId = lastDeliveryId,
        missedCount = missedCount,
        status = status,
        error = error,
        configJson = configJson,
        updatedAt = updatedAt
    )

    companion object {
        fun from(registration: ScheduleRegistration) = ScheduleRegistrationEntity(
            scheduleId = registration.scheduleId,
            profileId = registration.profileId,
            profileRevision = registration.profileRevision,
            triggerType = registration.triggerType,
            delivery = registration.delivery,
            timezone = registration.timezone,
            nextFireAt = registration.nextFireAt,
            lastFiredAt = registration.lastFiredAt,
            lastDeliveryId = registration.lastDeliveryId,
            missedCount = registration.missedCount,
            status = registration.status,
            error = registration.error,
            configJson = registration.configJson,
            updatedAt = registration.updatedAt
        )
    }
}
