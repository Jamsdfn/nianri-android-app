package com.nianri.app.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nianri.app.NianriApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isSystemRebuildAction(intent.action)) return
        val pendingResult = goAsync()
        val application = context.applicationContext as NianriApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runSystemChangeRefresh(
                    rebuildReminders = application.container.reminderScheduler::rebuildAll,
                    updateWidgets = application.container.widgetUpdater::updateAll,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

suspend fun runSystemChangeRefresh(
    rebuildReminders: suspend () -> Unit,
    updateWidgets: suspend () -> Unit,
) {
    var failure: Exception? = null
    try {
        rebuildReminders()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        failure = error
    }

    try {
        updateWidgets()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (failure == null) failure = error else failure.addSuppressed(error)
    }

    failure?.let { throw it }
}

fun isSystemRebuildAction(action: String?): Boolean = action in SYSTEM_REBUILD_ACTIONS

private val SYSTEM_REBUILD_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
)
