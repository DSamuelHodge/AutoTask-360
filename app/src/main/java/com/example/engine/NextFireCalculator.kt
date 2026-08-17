package com.example.engine

import com.example.domain.DeliveryGuarantees
import com.example.domain.GeoPoint
import com.example.domain.JsonValue
import com.example.domain.NextFire
import com.example.domain.ScheduleTriggers
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object NextFireCalculator {
    const val MIN_INTERVAL_MS = 1_000L
    const val TIME_CATCH_UP_GRACE_MS = 15L * 60L * 1000L

    fun resolveZone(config: JsonValue.ObjectValue, fallback: ZoneId): ZoneId {
        val raw = config.fields["timezone"]?.asTextOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return fallback
        return try {
            ZoneId.of(raw)
        } catch (_: Exception) {
            throw IllegalArgumentException("unknown timezone '$raw'")
        }
    }

    fun next(
        triggerType: String,
        config: JsonValue.ObjectValue,
        afterEpochMs: Long,
        zone: ZoneId,
        lastFiredAt: Long? = null,
        location: GeoPoint? = null
    ): NextFire? {
        return when (triggerType.uppercase()) {
            "TIME" -> nextTime(config, afterEpochMs, zone)
            "SCHEDULE" -> nextSchedule(config, afterEpochMs, zone, lastFiredAt)
            "SUNRISE_SUNSET" -> nextSunriseSunset(config, afterEpochMs, zone, location)
            else -> null
        }
    }

    fun deliveryFor(triggerType: String, config: JsonValue.ObjectValue): String {
        return when (triggerType.uppercase()) {
            "SCHEDULE" -> {
                val cron = config.fields["cronExpression"]?.asTextOrNull()?.trim().orEmpty()
                if (cron.isNotEmpty()) DeliveryGuarantees.EXACT else DeliveryGuarantees.FLEXIBLE
            }
            else -> DeliveryGuarantees.EXACT
        }
    }

    fun catchUpGraceMs(triggerType: String, config: JsonValue.ObjectValue): Long {
        if (triggerType.equals("SCHEDULE", ignoreCase = true)) {
            val interval = config.fields["intervalMs"]?.asLongOrNull()
            if (interval != null && interval > 0L) {
                return minOf(interval, TIME_CATCH_UP_GRACE_MS)
            }
        }
        return TIME_CATCH_UP_GRACE_MS
    }

    fun shouldCatchUp(
        scheduledFor: Long,
        now: Long,
        triggerType: String,
        config: JsonValue.ObjectValue
    ): Boolean {
        val lateness = now - scheduledFor
        return lateness in 1..catchUpGraceMs(triggerType, config)
    }

    private fun nextTime(config: JsonValue.ObjectValue, afterEpochMs: Long, zone: ZoneId): NextFire? {
        val hour = config.fields["hour"]?.asIntOrNull() ?: return null
        val minute = config.fields["minute"]?.asIntOrNull() ?: return null
        val days = weekdayFilter(config)
        var date = Instant.ofEpochMilli(afterEpochMs).atZone(zone).toLocalDate()
        repeat(400) {
            val candidate = resolveLocal(date, hour, minute, zone)
            if (candidate != null &&
                candidate.toInstant().toEpochMilli() > afterEpochMs &&
                dayMatches(candidate.dayOfWeek, days)
            ) {
                return NextFire(candidate.toInstant().toEpochMilli(), zone.id, DeliveryGuarantees.EXACT)
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun nextSchedule(
        config: JsonValue.ObjectValue,
        afterEpochMs: Long,
        zone: ZoneId,
        lastFiredAt: Long?
    ): NextFire? {
        val cronRaw = config.fields["cronExpression"]?.asTextOrNull()?.trim().orEmpty()
        if (cronRaw.isNotEmpty()) {
            val parsed = CronParser.parse(cronRaw)
            val next = CronParser.nextAfter(parsed, afterEpochMs, zone) ?: return null
            return NextFire(next, zone.id, DeliveryGuarantees.EXACT)
        }
        val interval = config.fields["intervalMs"]?.asLongOrNull() ?: return null
        if (interval < MIN_INTERVAL_MS) return null
        val base = lastFiredAt?.takeIf { it > 0L } ?: afterEpochMs
        var next = base + interval
        if (next <= afterEpochMs) {
            val skipped = ((afterEpochMs - base) / interval) + 1
            next = base + (skipped * interval)
        }
        return NextFire(next, zone.id, DeliveryGuarantees.FLEXIBLE)
    }

    private fun nextSunriseSunset(
        config: JsonValue.ObjectValue,
        afterEpochMs: Long,
        zone: ZoneId,
        location: GeoPoint?
    ): NextFire? {
        val event = config.fields["event"]?.asTextOrNull()?.trim()?.lowercase() ?: return null
        val sunrise = event == "sunrise"
        if (!sunrise && event != "sunset") return null
        val offset = config.fields["offsetMinutes"]?.asIntOrNull() ?: 0
        val point = locationFrom(config) ?: location ?: return null
        var date = Instant.ofEpochMilli(afterEpochMs).atZone(zone).toLocalDate()
        repeat(400) {
            val candidate = SunriseSunsetCalculator.event(date, point, sunrise, zone, offset)
            if (candidate != null && candidate.toInstant().toEpochMilli() > afterEpochMs) {
                return NextFire(candidate.toInstant().toEpochMilli(), zone.id, DeliveryGuarantees.EXACT)
            }
            date = date.plusDays(1)
        }
        return null
    }

    fun locationFrom(config: JsonValue.ObjectValue): GeoPoint? {
        val lat = config.fields["latitude"]?.asDoubleOrNull() ?: return null
        val lon = config.fields["longitude"]?.asDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoPoint(lat, lon)
    }

    internal fun resolveLocal(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): ZonedDateTime? {
        val local = try {
            LocalDateTime.of(date, LocalTime.of(hour, minute))
        } catch (_: DateTimeException) {
            return null
        }
        val offsets = zone.rules.getValidOffsets(local)
        return when {
            offsets.isEmpty() -> null
            offsets.size == 1 -> local.atZone(zone)
            else -> {
                val first = offsets.minBy { local.atOffset(it).toInstant() }
                ZonedDateTime.ofLocal(local, zone, first)
            }
        }
    }

    private fun weekdayFilter(config: JsonValue.ObjectValue): Set<DayOfWeek>? {
        val array = config.fields["days"] as? JsonValue.ArrayValue ?: return null
        if (array.values.isEmpty()) return null
        val days = array.values.mapNotNull { value ->
            weekday(value.asTextOrNull() ?: return@mapNotNull null)
        }.toSet()
        return days.takeIf { it.isNotEmpty() }
    }

    private fun dayMatches(day: DayOfWeek, filter: Set<DayOfWeek>?): Boolean =
        filter == null || day in filter

    internal fun weekday(raw: String): DayOfWeek? = when (raw.trim().uppercase()) {
        "MON", "MONDAY" -> DayOfWeek.MONDAY
        "TUE", "TUES", "TUESDAY" -> DayOfWeek.TUESDAY
        "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY
        "THU", "THUR", "THURS", "THURSDAY" -> DayOfWeek.THURSDAY
        "FRI", "FRIDAY" -> DayOfWeek.FRIDAY
        "SAT", "SATURDAY" -> DayOfWeek.SATURDAY
        "SUN", "SUNDAY" -> DayOfWeek.SUNDAY
        else -> null
    }

    fun isScheduledTrigger(type: String): Boolean = ScheduleTriggers.isScheduled(type)
}
