package com.example.engine

import com.example.data.AutomationProfile
import com.example.domain.DeliveryGuarantees
import com.example.domain.ScheduleFire
import com.example.domain.ScheduleStatuses
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class ScheduleManagerTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun enabledTimeProfileGetsExactNextFire() = runBlocking {
        var now = zoned(2026, 6, 15, 8, 0)
        val env = harness(now = { now })
        val profile = timeProfile("night", hour = 22, minute = 0)
        env.profiles += profile

        val saved = env.manager.syncProfile(profile)!!

        assertEquals(ScheduleStatuses.SCHEDULED, saved.status)
        assertEquals(DeliveryGuarantees.EXACT, saved.delivery)
        assertEquals(zoned(2026, 6, 15, 22, 0), saved.nextFireAt)
        assertEquals(1, env.driver.booked.size)
        assertTrue(env.driver.booking("night")!!.exact)
    }

    @Test
    fun disabledProfileCancelsAndIsObservable() = runBlocking {
        val env = harness(now = { zoned(2026, 6, 15, 8, 0) })
        val enabled = timeProfile("night", hour = 22, minute = 0)
        env.profiles += enabled
        env.manager.syncProfile(enabled)

        val disabled = enabled.copy(isEnabled = false)
        env.profiles[0] = disabled
        val saved = env.manager.syncProfile(disabled)!!

        assertEquals(ScheduleStatuses.DISABLED, saved.status)
        assertTrue(env.driver.cancelled.contains("night"))
        assertEquals("night", env.manager.get("night")!!.profileId)
    }

    @Test
    fun deleteRemovesRegistration() = runBlocking {
        val env = harness(now = { zoned(2026, 6, 15, 8, 0) })
        val profile = timeProfile("night", hour = 22, minute = 0)
        env.profiles += profile
        env.manager.syncProfile(profile)
        env.profiles.clear()
        env.manager.unschedule("night")
        assertNull(env.manager.get("night"))
        assertTrue(env.driver.cancelled.contains("night"))
    }

    @Test
    fun intervalScheduleUsesFlexibleWork() = runBlocking {
        var now = 1_000L
        val env = harness(now = { now })
        val profile = intervalProfile("poll", 60_000L)
        env.profiles += profile
        val saved = env.manager.syncProfile(profile)!!
        assertEquals(DeliveryGuarantees.FLEXIBLE, saved.delivery)
        assertEquals(61_000L, saved.nextFireAt)
        assertEquals(false, env.driver.booking("poll")!!.exact)
    }

    @Test
    fun missedWithinGraceFiresCatchUpThenReschedules() = runBlocking {
        var now = zoned(2026, 6, 15, 22, 5)
        val fired = mutableListOf<ScheduleFire>()
        val env = harness(now = { now }, onFire = { fired += it })
        val profile = timeProfile("night", hour = 22, minute = 0)
        env.profiles += profile
        env.store.upsert(
            env.manager.syncProfile(profile)!!.copy(
                nextFireAt = zoned(2026, 6, 15, 22, 0)
            )
        )

        val result = env.manager.reconcile("boot")
        assertEquals(1, fired.size)
        assertTrue(fired[0].missed)
        assertEquals(zoned(2026, 6, 15, 22, 0), fired[0].scheduledFor)
        assertEquals(zoned(2026, 6, 16, 22, 0), result.single().nextFireAt)
        assertEquals(ScheduleStatuses.SCHEDULED, result.single().status)
    }

    @Test
    fun missedOutsideGraceIncrementsCountWithoutFiring() = runBlocking {
        var now = zoned(2026, 6, 15, 23, 0)
        val fired = mutableListOf<ScheduleFire>()
        val env = harness(now = { now }, onFire = { fired += it })
        val profile = timeProfile("night", hour = 22, minute = 0)
        env.profiles += profile
        env.store.upsert(
            env.manager.syncProfile(profile)!!.copy(
                nextFireAt = zoned(2026, 6, 15, 22, 0)
            )
        )

        val result = env.manager.reconcile("boot")
        assertTrue(fired.isEmpty())
        assertEquals(1, result.single().missedCount)
        assertEquals(ScheduleStatuses.MISSED, result.single().status)
        assertEquals(zoned(2026, 6, 16, 22, 0), result.single().nextFireAt)
    }

    @Test
    fun timezoneReconcileDoesNotCatchUpOldEpoch() = runBlocking {
        var now = zoned(2026, 6, 15, 22, 5)
        val fired = mutableListOf<ScheduleFire>()
        val env = harness(now = { now }, onFire = { fired += it })
        val profile = timeProfile("night", hour = 22, minute = 0)
        env.profiles += profile
        env.store.upsert(
            env.manager.syncProfile(profile)!!.copy(
                nextFireAt = zoned(2026, 6, 15, 22, 0)
            )
        )

        val result = env.manager.reconcile("timezone")
        assertTrue(fired.isEmpty())
        assertEquals(zoned(2026, 6, 16, 22, 0), result.single().nextFireAt)
    }

    @Test
    fun rebootReschedulesEnabledProfiles() = runBlocking {
        val env = harness(now = { zoned(2026, 6, 15, 8, 0) })
        val profile = timeProfile("night", hour = 22, minute = 0)
        env.profiles += profile
        env.manager.syncProfile(profile)
        env.driver.booked.clear()

        val result = env.manager.reconcile("boot")
        assertEquals(1, result.size)
        assertEquals(zoned(2026, 6, 15, 22, 0), result.single().nextFireAt)
        assertEquals(1, env.driver.booked.size)
    }

    @Test
    fun duplicateDeliveryIsIgnored() = runBlocking {
        var now = zoned(2026, 6, 15, 22, 0)
        val fired = mutableListOf<ScheduleFire>()
        val env = harness(now = { now }, onFire = { fired += it })
        val profile = timeProfile("night", hour = 22, minute = 0)
        env.profiles += profile
        env.manager.syncProfile(profile)

        val first = env.manager.deliver("night", zoned(2026, 6, 15, 22, 0))
        now = zoned(2026, 6, 15, 22, 1)
        val second = env.manager.deliver("night", zoned(2026, 6, 15, 22, 0))

        assertEquals(1, fired.size)
        assertEquals(first!!.deliveryId, second!!.deliveryId)
        assertEquals(zoned(2026, 6, 16, 22, 0), env.manager.get("night")!!.nextFireAt)
    }

    @Test
    fun nonScheduleTriggerIsNotRegistered() = runBlocking {
        val env = harness(now = { 1L })
        val profile = AutomationProfile(
            id = "sms",
            name = "SMS",
            triggerType = "SMS",
            triggerConfigJson = "{}",
            actionsJson = """[{"type":"LOG","params":{"message":"x"}}]"""
        )
        assertNull(env.manager.syncProfile(profile))
        assertTrue(env.manager.list().isEmpty())
    }

    @Test
    fun deliveryIdIsStableForDeduping() {
        assertEquals("night:100", ScheduleFire.deliveryId("night", 100L))
        assertNotEquals(
            ScheduleFire.dedupeKey("a", 1L),
            ScheduleFire.dedupeKey("b", 1L)
        )
    }

    private fun harness(
        now: () -> Long,
        onFire: (ScheduleFire) -> Unit = {}
    ): Harness {
        val profiles = mutableListOf<AutomationProfile>()
        val store = InMemoryScheduleStore()
        val driver = RecordingScheduleDriver()
        val manager = ScheduleManager(
            store = store,
            driver = driver,
            loadProfiles = { profiles.toList() },
            clock = now,
            zoneId = { zone },
            onFire = { onFire(it) }
        )
        return Harness(manager, store, driver, profiles)
    }

    private data class Harness(
        val manager: ScheduleManager,
        val store: InMemoryScheduleStore,
        val driver: RecordingScheduleDriver,
        val profiles: MutableList<AutomationProfile>
    )

    private fun timeProfile(id: String, hour: Int, minute: Int) = AutomationProfile(
        id = id,
        name = id,
        isEnabled = true,
        triggerType = "TIME",
        triggerConfigJson = """{"hour":$hour,"minute":$minute}""",
        actionsJson = """[{"type":"LOG","params":{"message":"x"}}]""",
        revision = 1L
    )

    private fun intervalProfile(id: String, intervalMs: Long) = AutomationProfile(
        id = id,
        name = id,
        isEnabled = true,
        triggerType = "SCHEDULE",
        triggerConfigJson = """{"intervalMs":$intervalMs}""",
        actionsJson = """[{"type":"LOG","params":{"message":"x"}}]""",
        revision = 1L
    )

    private fun zoned(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
