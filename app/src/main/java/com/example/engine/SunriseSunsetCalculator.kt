package com.example.engine

import com.example.domain.GeoPoint
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * NOAA-style solar-event approximation. Accurate enough for daily sunrise
 * and sunset scheduling; not a high-precision ephemeris.
 */
object SunriseSunsetCalculator {
    fun event(
        date: LocalDate,
        point: GeoPoint,
        sunrise: Boolean,
        zone: ZoneId,
        offsetMinutes: Int = 0
    ): ZonedDateTime? {
        val zenith = 90.833
        val n = date.dayOfYear.toDouble()
        val lngHour = point.longitude / 15.0
        val t = n + ((if (sunrise) 6.0 else 18.0) - lngHour) / 24.0
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(rad(m))) + (0.020 * sin(rad(2 * m))) + 282.634
        l = normalize360(l)
        var ra = deg(kotlin.math.atan(0.91764 * tan(rad(l))))
        ra = normalize360(ra)
        val lQuad = (kotlin.math.floor(l / 90.0) * 90.0)
        val raQuad = (kotlin.math.floor(ra / 90.0) * 90.0)
        ra = (ra + (lQuad - raQuad)) / 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(zenith)) - (sinDec * sin(rad(point.latitude)))) /
            (cosDec * cos(rad(point.latitude)))
        if (cosH > 1.0 || cosH < -1.0) return null
        var h = if (sunrise) 360.0 - deg(acos(cosH)) else deg(acos(cosH))
        h /= 15.0
        val localT = h + ra - (0.06571 * t) - 6.622
        var ut = localT - lngHour
        ut = ((ut % 24.0) + 24.0) % 24.0
        val hour = ut.toInt().coerceIn(0, 23)
        val minute = ((ut - hour) * 60.0).roundToInt().let { if (it == 60) 59 else it }.coerceIn(0, 59)
        val utc = date.atTime(hour, minute).atZone(ZoneId.of("UTC")).plusMinutes(offsetMinutes.toLong())
        return utc.withZoneSameInstant(zone)
    }

    private fun rad(deg: Double): Double = deg * PI / 180.0
    private fun deg(rad: Double): Double = rad * 180.0 / PI
    private fun normalize360(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
}
