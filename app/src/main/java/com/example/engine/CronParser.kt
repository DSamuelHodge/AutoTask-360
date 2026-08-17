package com.example.engine

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 5-field cron used by SCHEDULE triggers:
 *
 * minute hour day-of-month month day-of-week
 *
 * - minute: 0-59
 * - hour: 0-23
 * - day-of-month: 1-31
 * - month: 1-12 or JAN-DEC
 * - day-of-week: 0-6 or 7 (Sunday) or SUN-SAT
 *
 * Tokens: star, N, N-M, comma lists, star/S, N-M/S.
 * When both day-of-month and day-of-week are restricted, a date matches if
 * either field matches (standard cron OR).
 */
data class CronExpression(
    val source: String,
    val minutes: IntSet,
    val hours: IntSet,
    val daysOfMonth: IntSet,
    val months: IntSet,
    val daysOfWeek: IntSet,
    val dayOfMonthRestricted: Boolean,
    val dayOfWeekRestricted: Boolean
) {
    fun matches(time: ZonedDateTime): Boolean {
        if (time.minute !in minutes) return false
        if (time.hour !in hours) return false
        if (time.monthValue !in months) return false
        val domOk = time.dayOfMonth in daysOfMonth
        val dowOk = cronDayOfWeek(time.dayOfWeek) in daysOfWeek
        return when {
            dayOfMonthRestricted && dayOfWeekRestricted -> domOk || dowOk
            dayOfMonthRestricted -> domOk
            dayOfWeekRestricted -> dowOk
            else -> true
        }
    }
}

data class IntSet(val values: Set<Int>) {
    operator fun contains(value: Int): Boolean = value in values
}

object CronParser {
    private val MONTHS = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )
    private val WEEKDAYS = mapOf(
        "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3, "THU" to 4, "FRI" to 5, "SAT" to 6
    )

    fun parse(expression: String): CronExpression {
        val fields = expression.trim().split(Regex("\\s+"))
        if (fields.size != 5) {
            throw IllegalArgumentException("cron must have 5 fields (minute hour day-of-month month day-of-week)")
        }
        val minutes = parseField(fields[0], 0, 59)
        val hours = parseField(fields[1], 0, 23)
        val daysOfMonth = parseField(fields[2], 1, 31)
        val months = parseField(fields[3], 1, 12, MONTHS)
        val daysOfWeek = parseField(fields[4], 0, 7, WEEKDAYS).let { set ->
            IntSet(set.values.map { if (it == 7) 0 else it }.toSet())
        }
        return CronExpression(
            source = expression.trim(),
            minutes = minutes,
            hours = hours,
            daysOfMonth = daysOfMonth,
            months = months,
            daysOfWeek = daysOfWeek,
            dayOfMonthRestricted = fields[2] != "*",
            dayOfWeekRestricted = fields[4] != "*"
        )
    }

    fun nextAfter(expression: CronExpression, afterEpochMs: Long, zone: ZoneId): Long? {
        var cursor = java.time.Instant.ofEpochMilli(afterEpochMs).atZone(zone)
            .withSecond(0)
            .withNano(0)
            .plusMinutes(1)
        // Bound search to ~2 years of minutes.
        repeat(60 * 24 * 366 * 2) {
            if (expression.matches(cursor)) {
                return cursor.toInstant().toEpochMilli()
            }
            cursor = try {
                cursor.plusMinutes(1)
            } catch (_: DateTimeException) {
                return null
            }
        }
        return null
    }

    private fun parseField(
        field: String,
        min: Int,
        max: Int,
        names: Map<String, Int> = emptyMap()
    ): IntSet {
        if (field == "*") return IntSet((min..max).toSet())
        val values = linkedSetOf<Int>()
        field.split(",").forEach { partRaw ->
            val part = partRaw.uppercase()
            val (rangePart, stepPart) = if (part.contains("/")) {
                val split = part.split("/", limit = 2)
                split[0] to split[1]
            } else {
                part to null
            }
            val step = stepPart?.toIntOrNull()
                ?: if (stepPart == null) 1 else throw IllegalArgumentException("invalid cron step '$part'")
            if (step <= 0) throw IllegalArgumentException("cron step must be > 0")
            val (start, end) = when {
                rangePart == "*" -> min to max
                rangePart.contains("-") -> {
                    val bounds = rangePart.split("-", limit = 2)
                    token(bounds[0], names) to token(bounds[1], names)
                }
                else -> {
                    val value = token(rangePart, names)
                    value to if (stepPart != null) max else value
                }
            }
            if (start < min || end > max || start > end) {
                throw IllegalArgumentException("cron field '$field' out of range $min-$max")
            }
            var current = start
            while (current <= end) {
                values += current
                current += step
            }
        }
        return IntSet(values)
    }

    private fun token(raw: String, names: Map<String, Int>): Int {
        names[raw]?.let { return it }
        return raw.toIntOrNull() ?: throw IllegalArgumentException("invalid cron token '$raw'")
    }
}

internal fun cronDayOfWeek(day: DayOfWeek): Int = day.value % 7
