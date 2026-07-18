package com.nianri.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nianri.app.NianriApplication
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MidnightWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MIDNIGHT_WIDGET_REFRESH_ACTION) return
        val pendingResult = goAsync()
        val application = context.applicationContext as NianriApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runMidnightWidgetRefresh(
                    updateWidgets = application.container.widgetUpdater::updateAll,
                    scheduleNext = application.container.midnightWidgetRefreshScheduler::scheduleNext,
                    finish = pendingResult::finish,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Application startup, system changes, and WorkManager remain recovery paths.
            }
        }
    }
}

internal suspend fun runMidnightWidgetRefresh(
    updateWidgets: suspend () -> Unit,
    scheduleNext: () -> Unit,
    finish: () -> Unit,
) {
    var failure: Exception? = null

    try {
        updateWidgets()
    } catch (error: Exception) {
        failure = error
    }

    try {
        scheduleNext()
    } catch (error: Exception) {
        if (failure == null) failure = error else failure.addSuppressed(error)
    }

    try {
        finish()
    } catch (error: Exception) {
        if (failure == null) failure = error else failure.addSuppressed(error)
    }

    failure?.let { throw it }
}
