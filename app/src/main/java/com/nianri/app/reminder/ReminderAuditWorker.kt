package com.nianri.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.nianri.app.NianriApplication
import java.time.Duration
import kotlinx.coroutines.CancellationException

class ReminderAuditWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(applicationContext, workerParameters) {
    override suspend fun doWork(): Result = runReminderAudit {
        (applicationContext as NianriApplication).container.reminderScheduler.rebuildAll()
    }
}

suspend fun runReminderAudit(rebuild: suspend () -> Unit): ListenableWorker.Result = try {
    rebuild()
    ListenableWorker.Result.success()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    ListenableWorker.Result.retry()
}

fun reminderAuditRequest(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<ReminderAuditWorker>(
    repeatInterval = Duration.ofHours(24),
    flexTimeInterval = Duration.ofHours(2),
).build()
