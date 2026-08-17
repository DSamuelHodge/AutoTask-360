package com.example.data

import com.example.domain.ScheduleRegistration
import com.example.engine.ScheduleStore

class RoomScheduleStore(database: AutoTaskDatabase) : ScheduleStore {
    private val dao = database.scheduleDao()

    override suspend fun upsert(registration: ScheduleRegistration) {
        dao.upsert(ScheduleRegistrationEntity.from(registration))
    }

    override suspend fun get(scheduleId: String): ScheduleRegistration? =
        dao.getById(scheduleId)?.toDomain()

    override suspend fun list(): List<ScheduleRegistration> = dao.list().map { it.toDomain() }

    override suspend fun delete(scheduleId: String) {
        dao.delete(scheduleId)
    }
}
