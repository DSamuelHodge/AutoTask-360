package com.example.engine

import com.example.domain.DeliveryGuarantees
import com.example.domain.GeoPoint
import com.example.domain.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NextFireCalculatorTest {
    private val ny = ZoneId.of("America/New_York")
    private val la = ZoneId.of("America/Los_Angeles")

    @Test
    fun timeUsesSameDayWhenStillUpcoming() {
        val after = zoned(ny, 2026, 6, 15, 8, 0)
        val next = NextFireCalculator.next("TIME", time(22, 0), after, ny)!!
        assertEquals(zoned(ny, 2026, 6, 15, 22, 0), next.epochMs)
        assertEquals(DeliveryGuarantees.EXACT, next.delivery)
    }

    @Test
    fun timeRollsToNextDayWhenAlreadyPast() {
        val after = zoned(ny, 2026, 6, 15, 22, 1)
        val next = NextFireCalculator.next("TIME", time(22, 0), after, ny)!!
        assertEquals(zoned(ny, 2026, 6, 16, 22, 0), next.epochMs)
    }

    @Test
    fun timeSkipsWeekendsWhenWeekdaysAreConfigured() {
        // Friday 22:01 → next weekday 22:00 is Monday
        val after = zoned(ny, 2026, 6, 19, 22, 1)
        val next = NextFireCalculator.next(
            "TIME",
            time(22, 0, listOf("MON", "TUE", "WED", "THU", "FRI")),
            after,
            ny
        )!!
        assertEquals(zoned(ny, 2026, 6, 22, 22, 0), next.epochMs)
    }

    @Test
    fun timeSkipsSpringForwardGap() {
        // 2026-03-08 02:00 → 03:00 in America/New_York; 02:30 does not exist.
        val after = zoned(ny, 2026, 3, 8, 1, 0)
        val next = NextFireCalculator.next("TIME", time(2, 30), after, ny)!!
        assertEquals(zoned(ny, 2026, 3, 9, 2, 30), next.epochMs)
    }

    @Test
    fun timeUsesFirstOccurrenceOnFallBackOverlap() {
        // 2026-11-01 01:30 occurs twice; first is EDT (UTC-4).
        val after = zoned(ny, 2026, 11, 1, 0, 30)
        val next = NextFireCalculator.next("TIME", time(1, 30), after, ny)!!
        assertEquals(zoned(ny, 2026, 11, 1, 1, 30), next.epochMs)
        val instant = java.time.Instant.ofEpochMilli(next.epochMs)
        assertEquals("2026-11-01T05:30:00Z", instant.toString())
    }

    @Test
    fun timezoneChangeKeepsTheSameWallClock() {
        val after = zoned(ny, 2026, 6, 15, 20, 0)
        val inNy = NextFireCalculator.next("TIME", time(22, 0), after, ny)!!
        val inLa = NextFireCalculator.next("TIME", time(22, 0), after, la)!!
        assertEquals(zoned(ny, 2026, 6, 15, 22, 0), inNy.epochMs)
        assertEquals(zoned(la, 2026, 6, 15, 22, 0), inLa.epochMs)
        assertTrue(inLa.epochMs > inNy.epochMs)
    }

    @Test
    fun intervalScheduleIsFlexibleAndAdvancesFromLastFire() {
        val after = 1_000_000L
        val first = NextFireCalculator.next("SCHEDULE", interval(60_000L), after, ny)!!
        assertEquals(1_060_000L, first.epochMs)
        assertEquals(DeliveryGuarantees.FLEXIBLE, first.delivery)

        val skipped = NextFireCalculator.next(
            "SCHEDULE",
            interval(60_000L),
            afterEpochMs = 1_200_000L,
            zone = ny,
            lastFiredAt = 1_060_000L
        )!!
        assertEquals(1_240_000L, skipped.epochMs)
    }

    @Test
    fun cronDailyIsExactAndRespectsTimezone() {
        val after = zoned(ny, 2026, 6, 15, 21, 0)
        val next = NextFireCalculator.next("SCHEDULE", cron("0 22 * * *"), after, ny)!!
        assertEquals(zoned(ny, 2026, 6, 15, 22, 0), next.epochMs)
        assertEquals(DeliveryGuarantees.EXACT, next.delivery)
    }

    @Test
    fun cronEveryFifteenMinutesFindsTheNextSlot() {
        val after = zoned(ny, 2026, 6, 15, 10, 7)
        val next = NextFireCalculator.next("SCHEDULE", cron("*/15 * * * *"), after, ny)!!
        assertEquals(zoned(ny, 2026, 6, 15, 10, 15), next.epochMs)
    }

    @Test
    fun cronWeekdayFilterSkipsSaturday() {
        val after = zoned(ny, 2026, 6, 19, 22, 1) // Friday after 09:00
        val next = NextFireCalculator.next("SCHEDULE", cron("0 9 * * MON-FRI"), after, ny)!!
        assertEquals(zoned(ny, 2026, 6, 22, 9, 0), next.epochMs)
    }

    @Test
    fun sunriseUsesConfiguredLocation() {
        val after = zoned(ny, 2026, 6, 21, 0, 0)
        val next = NextFireCalculator.next(
            "SUNRISE_SUNSET",
            JsonValue.fromObject(
                org.json.JSONObject()
                    .put("event", "sunrise")
                    .put("latitude", 40.7128)
                    .put("longitude", -74.0060)
            ),
            after,
            ny,
            location = GeoPoint(40.7128, -74.0060)
        )
        assertNotNull(next)
        val local = java.time.Instant.ofEpochMilli(next!!.epochMs).atZone(ny)
        assertEquals(2026, local.year)
        assertEquals(6, local.monthValue)
        assertEquals(21, local.dayOfMonth)
        assertTrue(local.hour in 4..7)
        assertEquals(DeliveryGuarantees.EXACT, next.delivery)
    }

    @Test
    fun sunriseWithoutLocationReturnsNull() {
        val after = zoned(ny, 2026, 6, 21, 0, 0)
        val next = NextFireCalculator.next(
            "SUNRISE_SUNSET",
            JsonValue.fromObject(org.json.JSONObject().put("event", "sunrise")),
            after,
            ny
        )
        assertNull(next)
    }

    private fun time(hour: Int, minute: Int, days: List<String>? = null): JsonValue.ObjectValue {
        val obj = org.json.JSONObject().put("hour", hour).put("minute", minute)
        if (days != null) obj.put("days", org.json.JSONArray(days))
        return JsonValue.fromObject(obj)
    }

    private fun interval(ms: Long) = JsonValue.fromObject(org.json.JSONObject().put("intervalMs", ms))

    private fun cron(expression: String) =
        JsonValue.fromObject(org.json.JSONObject().put("cronExpression", expression))

    private fun zoned(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
