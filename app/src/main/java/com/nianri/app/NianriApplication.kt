package com.nianri.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.nianri.app.reminder.reminderAuditRequest

class NianriApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val workManager = try {
            WorkManager.getInstance(this)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(this, Configuration.Builder().build())
            WorkManager.getInstance(this)
        }
        initializeBackgroundRefresh(
            enqueueAudit = {
                workManager.enqueueUniquePeriodicWork(
                    REMINDER_AUDIT_WORK,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    reminderAuditRequest(),
                )
            },
            scheduleNextMidnight = container.midnightWidgetRefreshScheduler::scheduleNext,
        )
    }

    companion object {
        const val REMINDER_AUDIT_WORK = "reminder_daily_audit"
        const val REMINDER_FOREGROUND_AUDIT_WORK = "reminder_foreground_audit"
    }
}

internal fun initializeBackgroundRefresh(
    enqueueAudit: () -> Unit,
    scheduleNextMidnight: () -> Unit,
) {
    enqueueAudit()
    scheduleNextMidnight()
}
