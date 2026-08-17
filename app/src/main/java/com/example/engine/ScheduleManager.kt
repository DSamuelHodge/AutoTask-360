package com.example.engine

import com.example.data.AutomationProfile
import com.example.domain.GeoPoint
import com.example.domain.JsonValue
import com.example.domain.NextFire
import com.example.domain.ScheduleFire
import com.example.domain.ScheduleRegistration
import com.example.domain.ScheduleStatuses
import com.example.domain.ScheduleTriggers
import java.time.Instant
import java.time.ZoneId

class ScheduleManager(
    private val store: ScheduleStore,
    private val driver: ScheduleDriver,
    private val loadProfiles: suspend () -> List<AutomationProfile>,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
    private val location: () -> GeoPoint? = { null },
    private val onFire: suspend (ScheduleFire) -> Unit = {}
) {
    suspend fun syncProfile(profile: AutomationProfile): ScheduleRegistration? {
        if (!ScheduleTriggers.isScheduled(profile.triggerType)) {
            unschedule(profile.id)
            return null
        }
        if (!profile.isEnabled) {
            val existing = store.get(profile.id)
            val disabled = registration(
                profile = profile,
                next = null,
                status = ScheduleStatuses.DISABLED,
                error = "",
                existing = existing
            )
            store.upsert(disabled)
            driver.cancel(profile.id)
            return disabled
        }
        return persistNext(profile, existing = store.get(profile.id), afterEpochMs = clock(), catchUp = false)
    }

    suspend fun unschedule(profileId: String) {
        store.delete(profileId)
        driver.cancel(profileId)
    }

    suspend fun get(profileId: String): ScheduleRegistration? = store.get(profileId)

    suspend fun list(): List<ScheduleRegistration> = store.list()

    suspend fun reconcile(reason: String): List<ScheduleRegistration> {
        val catchUp = reason !in setOf("timezone", "time_changed")
        val now = clock()
        val profiles = loadProfiles().associateBy { it.id }
        val seen = linkedSetOf<String>()
        val result = mutableListOf<ScheduleRegistration>()

        for (profile in profiles.values) {
            if (!ScheduleTriggers.isScheduled(profile.triggerType)) {
                if (store.get(profile.id) != null) unschedule(profile.id)
                continue
            }
            seen += profile.id
            val existing = store.get(profile.id)
            if (!profile.isEnabled) {
                result += syncProfile(profile) ?: continue
                continue
            }
            val due = existing?.nextFireAt
            if (catchUp && due != null && due <= now) {
                val config = parseConfig(profile.triggerConfigJson)
                if (NextFireCalculator.shouldCatchUp(due, now, profile.triggerType, config)) {
                    deliverLocked(existing, profile, scheduledFor = due, missed = true)
                    result += store.get(profile.id) ?: continue
                    continue
                }
                val missed = persistNext(
                    profile = profile,
                    existing = existing.copy(missedCount = existing.missedCount + 1),
                    afterEpochMs = now,
                    catchUp = false,
                    statusOverride = ScheduleStatuses.MISSED
                )
                result += missed
                continue
            }
            result += persistNext(profile, existing, afterEpochMs = now, catchUp = false)
        }

        store.list()
            .filter { it.scheduleId !in seen }
            .forEach { stale -> unschedule(stale.scheduleId) }
        return result
    }

    suspend fun deliver(scheduleId: String, scheduledFor: Long): ScheduleFire? {
        val registration = store.get(scheduleId) ?: return null
        val profiles = loadProfiles()
        val profile = profiles.firstOrNull { it.id == registration.profileId } ?: run {
            unschedule(scheduleId)
            return null
        }
        if (!profile.isEnabled || !ScheduleTriggers.isScheduled(profile.triggerType)) {
            syncProfile(profile)
            return null
        }
        return deliverLocked(registration, profile, scheduledFor, missed = scheduledFor < clock())
    }

    private suspend fun deliverLocked(
        registration: ScheduleRegistration,
        profile: AutomationProfile,
        scheduledFor: Long,
        missed: Boolean
    ): ScheduleFire {
        val deliveryId = ScheduleFire.deliveryId(profile.id, scheduledFor)
        if (registration.lastDeliveryId == deliveryId) {
            persistNext(profile, registration, afterEpochMs = clock(), catchUp = false)
            return alreadyDelivered(registration, scheduledFor, missed)
        }
        val now = clock()
        val zone = resolveZone(profile)
        val fire = ScheduleFire(
            scheduleId = registration.scheduleId,
            profileId = profile.id,
            triggerType = profile.triggerType.uppercase(),
            scheduledFor = scheduledFor,
            firedAt = now,
            timezone = zone.id,
            missed = missed,
            deliveryId = deliveryId,
            dedupeKey = ScheduleFire.dedupeKey(profile.id, scheduledFor),
            payload = firePayload(profile, scheduledFor, now, zone.id, missed)
        )
        onFire(fire)
        val recorded = registration.copy(
            lastFiredAt = now,
            lastDeliveryId = deliveryId,
            updatedAt = now
        )
        persistNext(profile, recorded, afterEpochMs = now, catchUp = false)
        return fire
    }

    private suspend fun persistNext(
        profile: AutomationProfile,
        existing: ScheduleRegistration?,
        afterEpochMs: Long,
        catchUp: Boolean,
        statusOverride: String? = null
    ): ScheduleRegistration {
        val config = parseConfig(profile.triggerConfigJson)
        val zone = try {
            NextFireCalculator.resolveZone(config, zoneId())
        } catch (e: IllegalArgumentException) {
            return storeError(profile, existing, e.message ?: "invalid timezone")
        }
        val next = try {
            NextFireCalculator.next(
                triggerType = profile.triggerType,
                config = config,
                afterEpochMs = afterEpochMs,
                zone = zone,
                lastFiredAt = existing?.lastFiredAt,
                location = NextFireCalculator.locationFrom(config) ?: location()
            )
        } catch (e: Exception) {
            return storeError(profile, existing, e.message ?: "next-fire calculation failed")
        }
        if (next == null) {
            val error = when (profile.triggerType.uppercase()) {
                "SUNRISE_SUNSET" -> "location_unavailable"
                "SCHEDULE" -> "unsupported_or_invalid_schedule"
                else -> "no_next_occurrence"
            }
            val failed = registration(profile, null, ScheduleStatuses.ERROR, error, existing)
            store.upsert(failed)
            driver.cancel(profile.id)
            return failed
        }
        val status = statusOverride
            ?: if (catchUp) ScheduleStatuses.MISSED else ScheduleStatuses.SCHEDULED
        val saved = registration(profile, next, status, "", existing)
        store.upsert(saved)
        registerDriver(saved, next)
        return saved
    }

    private fun registerDriver(registration: ScheduleRegistration, next: NextFire) {
        if (next.delivery == com.example.domain.DeliveryGuarantees.FLEXIBLE) {
            driver.scheduleFlexible(registration.scheduleId, next.epochMs)
        } else {
            driver.scheduleExact(registration.scheduleId, next.epochMs)
        }
    }

    private suspend fun storeError(
        profile: AutomationProfile,
        existing: ScheduleRegistration?,
        message: String
    ): ScheduleRegistration {
        val failed = registration(profile, null, ScheduleStatuses.ERROR, message, existing)
        store.upsert(failed)
        driver.cancel(profile.id)
        return failed
    }

    private fun registration(
        profile: AutomationProfile,
        next: NextFire?,
        status: String,
        error: String,
        existing: ScheduleRegistration?
    ): ScheduleRegistration {
        return ScheduleRegistration(
            scheduleId = profile.id,
            profileId = profile.id,
            profileRevision = profile.revision,
            triggerType = profile.triggerType.uppercase(),
            delivery = next?.delivery
                ?: existing?.delivery
                ?: NextFireCalculator.deliveryFor(profile.triggerType, parseConfig(profile.triggerConfigJson)),
            timezone = next?.zoneId ?: existing?.timezone ?: zoneId().id,
            nextFireAt = next?.epochMs,
            lastFiredAt = existing?.lastFiredAt,
            lastDeliveryId = existing?.lastDeliveryId,
            missedCount = existing?.missedCount ?: 0,
            status = status,
            error = error,
            configJson = profile.triggerConfigJson,
            updatedAt = clock()
        )
    }

    private fun alreadyDelivered(
        registration: ScheduleRegistration,
        scheduledFor: Long,
        missed: Boolean
    ): ScheduleFire {
        return ScheduleFire(
            scheduleId = registration.scheduleId,
            profileId = registration.profileId,
            triggerType = registration.triggerType,
            scheduledFor = scheduledFor,
            firedAt = registration.lastFiredAt ?: clock(),
            timezone = registration.timezone,
            missed = missed,
            deliveryId = ScheduleFire.deliveryId(registration.profileId, scheduledFor),
            dedupeKey = ScheduleFire.dedupeKey(registration.profileId, scheduledFor),
            payload = emptyMap()
        )
    }

    private fun firePayload(
        profile: AutomationProfile,
        scheduledFor: Long,
        now: Long,
        timezone: String,
        missed: Boolean
    ): Map<String, Any?> {
        val zoned = Instant.ofEpochMilli(scheduledFor).atZone(ZoneId.of(timezone))
        val config = parseConfig(profile.triggerConfigJson)
        return mapOf(
            "profileId" to profile.id,
            "scheduleId" to profile.id,
            "scheduledFor" to scheduledFor,
            "timestamp" to now,
            "timezone" to timezone,
            "missed" to missed,
            "hour" to zoned.hour,
            "minute" to zoned.minute,
            "day" to zoned.dayOfWeek.name.take(3),
            "event" to (config.fields["event"]?.asTextOrNull() ?: ""),
            "cronExpression" to (config.fields["cronExpression"]?.asTextOrNull() ?: "")
        )
    }

    private fun resolveZone(profile: AutomationProfile): ZoneId {
        return try {
            NextFireCalculator.resolveZone(parseConfig(profile.triggerConfigJson), zoneId())
        } catch (_: Exception) {
            zoneId()
        }
    }

    private fun parseConfig(raw: String): JsonValue.ObjectValue =
        try {
            JsonValue.parseObject(raw)
        } catch (_: Exception) {
            JsonValue.emptyObject
        }

    companion object {
        val REASONS_WITHOUT_CATCH_UP = setOf("timezone", "time_changed")
    }
}
