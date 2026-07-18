package com.nianri.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

internal const val MIDNIGHT_WIDGET_REFRESH_ACTION =
    "com.nianri.app.action.MIDNIGHT_WIDGET_REFRESH"
internal const val MIDNIGHT_WIDGET_REFRESH_RECEIVER_CLASS =
    "com.nianri.app.widget.MidnightWidgetRefreshReceiver"
private const val MIDNIGHT_WIDGET_REFRESH_REQUEST_CODE = 20_260_718

internal fun nextLocalMidnight(clock: Clock): Instant =
    LocalDate.now(clock)
        .plusDays(1)
        .atStartOfDay(clock.zone)
        .toInstant()

class MidnightWidgetRefreshScheduler internal constructor(
    context: Context,
    private val clock: Clock,
    alarmManager: AlarmManager = context.applicationContext
        .getSystemService(AlarmManager::class.java),
    private val hasExactAlarmAccess: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    },
    private val setExactAlarm: (Long, PendingIntent) -> Unit = { triggerAtMillis, operation ->
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation,
        )
    },
) {
    private val applicationContext = context.applicationContext

    fun scheduleNext() {
        if (!hasExactAlarmAccess()) return
        try {
            setExactAlarm(nextLocalMidnight(clock).toEpochMilli(), operation())
        } catch (_: SecurityException) {
            // A permission revocation can race the access check. Recovery paths retry later.
        }
    }

    private fun operation(): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        MIDNIGHT_WIDGET_REFRESH_REQUEST_CODE,
        Intent(MIDNIGHT_WIDGET_REFRESH_ACTION).setComponent(
            ComponentName(
                applicationContext.packageName,
                MIDNIGHT_WIDGET_REFRESH_RECEIVER_CLASS,
            ),
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
