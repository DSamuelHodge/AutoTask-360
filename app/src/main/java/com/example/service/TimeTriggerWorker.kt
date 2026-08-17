package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.application.AutomationCommandFacade
import com.example.engine.AutomationEvent
import java.util.Calendar

class TimeTriggerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val commands = AutomationCommandFacade.getInstance(applicationContext)
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        commands.processEvent(
            AutomationEvent(
                type = "TIME",
                payload = mapOf(
                    "hour" to hour,
                    "minute" to minute,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )
        return Result.success()
    }
}
