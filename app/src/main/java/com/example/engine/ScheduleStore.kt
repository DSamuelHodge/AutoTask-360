package com.example.engine

import com.example.domain.ScheduleRegistration
import java.util.concurrent.ConcurrentHashMap

interface ScheduleStore {
    suspend fun upsert(registration: ScheduleRegistration)
    suspend fun get(scheduleId: String): ScheduleRegistration?
    suspend fun list(): List<ScheduleRegistration>
    suspend fun delete(scheduleId: String)
}

class InMemoryScheduleStore : ScheduleStore {
    private val rows = ConcurrentHashMap<String, ScheduleRegistration>()

    override suspend fun upsert(registration: ScheduleRegistration) {
        rows[registration.scheduleId] = registration
    }

    override suspend fun get(scheduleId: String): ScheduleRegistration? = rows[scheduleId]

    override suspend fun list(): List<ScheduleRegistration> =
        rows.values.sortedBy { it.nextFireAt ?: Long.MAX_VALUE }

    override suspend fun delete(scheduleId: String) {
        rows.remove(scheduleId)
    }
}

interface ScheduleDriver {
    fun scheduleExact(scheduleId: String, fireAtEpochMs: Long)
    fun scheduleFlexible(scheduleId: String, fireAtEpochMs: Long)
    fun cancel(scheduleId: String)
}

class RecordingScheduleDriver : ScheduleDriver {
    data class Booking(val scheduleId: String, val fireAt: Long, val exact: Boolean)

    val booked = mutableListOf<Booking>()
    val cancelled = mutableListOf<String>()

    override fun scheduleExact(scheduleId: String, fireAtEpochMs: Long) {
        cancelled.removeAll { it == scheduleId }
        booked.removeAll { it.scheduleId == scheduleId }
        booked += Booking(scheduleId, fireAtEpochMs, exact = true)
    }

    override fun scheduleFlexible(scheduleId: String, fireAtEpochMs: Long) {
        cancelled.removeAll { it == scheduleId }
        booked.removeAll { it.scheduleId == scheduleId }
        booked += Booking(scheduleId, fireAtEpochMs, exact = false)
    }

    override fun cancel(scheduleId: String) {
        booked.removeAll { it.scheduleId == scheduleId }
        cancelled += scheduleId
    }

    fun booking(scheduleId: String): Booking? = booked.lastOrNull { it.scheduleId == scheduleId }
}

class NoOpScheduleDriver : ScheduleDriver {
    override fun scheduleExact(scheduleId: String, fireAtEpochMs: Long) = Unit
    override fun scheduleFlexible(scheduleId: String, fireAtEpochMs: Long) = Unit
    override fun cancel(scheduleId: String) = Unit
}
