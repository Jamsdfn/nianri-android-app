package com.nianri.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.ImportantDay
import java.time.Clock
import java.time.LocalDate

class AndroidReminderScheduler(
    context: Context,
    private val loadDay: suspend (Long) -> ImportantDay?,
    private val loadAllDays: suspend () -> List<ImportantDay>,
    private val calculator: DateOccurrenceCalculator,
    private val clock: Clock,
) : ReminderScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override suspend fun replace(dayId: Long): ReminderScheduleResult {
        cancel(dayId)
        val day = loadDay(dayId) ?: return ReminderScheduleResult.Scheduled(0)
        if (day.reminders.isEmpty()) return ReminderScheduleResult.Scheduled(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return ReminderScheduleResult.NeedsExactAlarmPermission
        }
        val today = LocalDate.now(clock)
        val occurrence = calculator.next(day, today)
        var count = 0
        day.reminders.sortedDescending().forEach { offset ->
            val trigger = occurrence.solarDate.minusDays(offset.toLong())
                .atTime(9, 0)
                .atZone(clock.zone)
                .toInstant()
            if (trigger > clock.instant()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger.toEpochMilli(),
                    requireNotNull(pendingIntent(dayId, offset, PendingIntent.FLAG_UPDATE_CURRENT)),
                )
                count += 1
            }
        }
        return ReminderScheduleResult.Scheduled(count)
    }

    override suspend fun cancel(dayId: Long) {
        REMINDER_OFFSETS.forEach { offset ->
            pendingIntent(dayId, offset, PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
        }
    }

    override suspend fun rebuildAll() {
        loadAllDays().forEach { replace(it.id) }
    }

    private fun pendingIntent(dayId: Long, offset: Int, lookupFlag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            applicationContext,
            requestCode(dayId, offset),
            Intent(applicationContext, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_DAY_ID, dayId)
                .putExtra(ReminderReceiver.EXTRA_OFFSET, offset),
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(dayId: Long, offset: Int): Int = 31 * dayId.hashCode() + offset

    private companion object {
        val REMINDER_OFFSETS = setOf(14, 7, 3)
    }
}
