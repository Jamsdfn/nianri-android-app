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
import java.time.LocalTime

class AndroidReminderScheduler(
    context: Context,
    private val loadDay: suspend (Long) -> ImportantDay?,
    private val loadAllDays: suspend () -> List<ImportantDay>,
    private val calculator: DateOccurrenceCalculator,
    private val clock: Clock,
    private val immediateDispatcher: (Intent) -> Unit = context.applicationContext::sendBroadcast,
) : ReminderScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override suspend fun replace(dayId: Long): ReminderScheduleResult {
        cancel(dayId)
        val day = loadDay(dayId) ?: return ReminderScheduleResult.Scheduled(0)
        val today = LocalDate.now(clock)
        val occurrence = calculator.next(day, today)
        val reminderTime = LocalTime.of(
            day.reminderTimeMinutes / 60,
            day.reminderTimeMinutes % 60,
        )
        val future = (day.reminders + DAY_OF_OFFSET).sortedDescending().mapNotNull { offset ->
            val trigger = occurrence.solarDate.minusDays(offset.toLong())
                .atTime(reminderTime)
                .atZone(clock.zone)
                .toInstant()
            when {
                trigger > clock.instant() -> Triple(offset, trigger, pendingIntent(dayId, offset, PendingIntent.FLAG_UPDATE_CURRENT))
                offset == DAY_OF_OFFSET && occurrence.solarDate == today -> {
                    immediateDispatcher(reminderIntent(dayId, offset))
                    null
                }
                else -> null
            }
        }
        if (future.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return ReminderScheduleResult.NeedsExactAlarmPermission
        }
        future.forEach { (_, trigger, operation) ->
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.toEpochMilli(),
                requireNotNull(operation),
            )
        }
        return ReminderScheduleResult.Scheduled(future.size)
    }

    override suspend fun cancel(dayId: Long) {
        (REMINDER_OFFSETS + DAY_OF_OFFSET).forEach { offset ->
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
            reminderIntent(dayId, offset),
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderIntent(dayId: Long, offset: Int) =
        Intent(applicationContext, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_DAY_ID, dayId)
            .putExtra(ReminderReceiver.EXTRA_OFFSET, offset)

    private fun requestCode(dayId: Long, offset: Int): Int = 31 * dayId.hashCode() + offset

    private companion object {
        val REMINDER_OFFSETS = setOf(14, 7, 3)
        const val DAY_OF_OFFSET = 0
    }
}
