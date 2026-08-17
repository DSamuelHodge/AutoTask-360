package com.example.engine

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AndroidScheduleDriver
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidScheduleDriverTest {
    @Test
    fun exactBookingRegistersAnAlarmManagerWakeup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val driver = AndroidScheduleDriver(context)
        val fireAt = System.currentTimeMillis() + 60_000L
        driver.scheduleExact("profile-exact", fireAt)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertTrue(scheduled.isNotEmpty())
        assertTrue(scheduled.any { it.triggerAtTime == fireAt || it.triggerAtTime >= fireAt })
    }

    @Test
    fun cancelRemovesTheExactAlarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val driver = AndroidScheduleDriver(context)
        val fireAt = System.currentTimeMillis() + 120_000L
        driver.scheduleExact("profile-cancel", fireAt)
        driver.cancel("profile-cancel")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val remaining = shadowOf(alarmManager).scheduledAlarms.filter {
            it.operation?.creatorPackage == context.packageName
        }
        assertTrue(remaining.none { it.triggerAtTime == fireAt })
    }
}
