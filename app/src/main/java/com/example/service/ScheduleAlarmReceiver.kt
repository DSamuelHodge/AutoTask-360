package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.application.AutomationCommandFacade
import com.example.engine.ScheduleDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, 0L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AutomationCommandFacade.getInstance(context).deliverSchedule(scheduleId, scheduledFor)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.autotask.action.SCHEDULE_FIRE"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_SCHEDULED_FOR = "scheduledFor"

        fun pendingIntent(context: Context, scheduleId: String, scheduledFor: Long): PendingIntent {
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                data = Uri.parse("autotask://schedule/$scheduleId")
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_SCHEDULED_FOR, scheduledFor)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, requestCode(scheduleId), intent, flags)
        }

        fun cancelIntent(context: Context, scheduleId: String): PendingIntent {
            return pendingIntent(context, scheduleId, 0L)
        }

        private fun requestCode(scheduleId: String): Int = scheduleId.hashCode()
    }
}

class ScheduleWorkWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString(KEY_SCHEDULE_ID) ?: return Result.failure()
        val scheduledFor = inputData.getLong(KEY_SCHEDULED_FOR, 0L)
        return try {
            AutomationCommandFacade.getInstance(applicationContext).deliverSchedule(scheduleId, scheduledFor)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SCHEDULE_ID = "scheduleId"
        const val KEY_SCHEDULED_FOR = "scheduledFor"

        fun workName(scheduleId: String) = "autotask-schedule-$scheduleId"

        fun enqueue(context: Context, scheduleId: String, fireAtEpochMs: Long) {
            val delayMs = (fireAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ScheduleWorkWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SCHEDULE_ID to scheduleId,
                        KEY_SCHEDULED_FOR to fireAtEpochMs
                    )
                )
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workName(scheduleId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, scheduleId: String) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(scheduleId))
        }
    }
}

class AndroidScheduleDriver(private val context: Context) : ScheduleDriver {
    private val appContext = context.applicationContext

    override fun scheduleExact(scheduleId: String, fireAtEpochMs: Long) {
        cancelFlexible(scheduleId)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = ScheduleAlarmReceiver.pendingIntent(appContext, scheduleId, fireAtEpochMs)
        try {
            if (canExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtEpochMs, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtEpochMs, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtEpochMs, pending)
        }
    }

    override fun scheduleFlexible(scheduleId: String, fireAtEpochMs: Long) {
        cancelExact(scheduleId)
        try {
            ScheduleWorkWorker.enqueue(appContext, scheduleId, fireAtEpochMs)
        } catch (_: Exception) {
        }
    }

    override fun cancel(scheduleId: String) {
        cancelExact(scheduleId)
        cancelFlexible(scheduleId)
    }

    private fun cancelExact(scheduleId: String) {
        try {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(ScheduleAlarmReceiver.cancelIntent(appContext, scheduleId))
        } catch (_: Exception) {
        }
    }

    private fun cancelFlexible(scheduleId: String) {
        try {
            ScheduleWorkWorker.cancel(appContext, scheduleId)
        } catch (_: Exception) {
        }
    }

    private fun canExact(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
