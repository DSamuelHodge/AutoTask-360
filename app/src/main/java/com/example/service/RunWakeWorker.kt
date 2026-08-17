package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.application.AutomationCommandFacade
import com.example.engine.WakeScheduler
import java.util.concurrent.TimeUnit

class RunWakeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val runId = inputData.getString(KEY_RUN_ID) ?: return Result.failure()
        return try {
            AutomationCommandFacade.getInstance(applicationContext).resumeRun(runId)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_RUN_ID = "runId"
        private fun workName(runId: String) = "autotask-run-wake-$runId"

        fun enqueue(context: Context, runId: String, wakeAtEpochMs: Long) {
            val delayMs = (wakeAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<RunWakeWorker>()
                .setInputData(workDataOf(KEY_RUN_ID to runId))
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workName(runId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, runId: String) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(runId))
        }
    }
}

class WorkManagerWakeScheduler(private val context: Context) : WakeScheduler {
    override fun schedule(runId: String, wakeAtEpochMs: Long) {
        try {
            RunWakeWorker.enqueue(context, runId, wakeAtEpochMs)
        } catch (_: Exception) {
        }
    }

    override fun cancel(runId: String) {
        try {
            RunWakeWorker.cancel(context, runId)
        } catch (_: Exception) {
        }
    }
}
