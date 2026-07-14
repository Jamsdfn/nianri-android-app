package com.nianri.app.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.nianri.app.MainActivity
import com.nianri.app.NianriApplication
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.DateAdjustment
import com.nianri.app.domain.model.ImportantDay
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dayId = intent.getLongExtra(EXTRA_DAY_ID, 0L)
        val offset = intent.getIntExtra(EXTRA_OFFSET, -1)
        if (dayId <= 0L || offset !in ALLOWED_OFFSETS) return
        val pendingResult = goAsync()
        val application = context.applicationContext as NianriApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderNotificationService(
                    context = application,
                    loadDay = application.container.importantDays::get,
                    calculator = application.container.occurrenceCalculator,
                    clock = Clock.systemDefaultZone(),
                ).deliver(dayId, offset)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_DAY_ID = "dayId"
        const val EXTRA_OFFSET = "offset"
        val ALLOWED_OFFSETS = setOf(14, 7, 3)
    }
}

class ReminderNotificationService(
    context: Context,
    private val loadDay: suspend (Long) -> ImportantDay?,
    private val calculator: DateOccurrenceCalculator,
    private val clock: Clock,
) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

    suspend fun deliver(dayId: Long, offset: Int): Boolean {
        val day = loadDay(dayId) ?: return false
        if (offset !in day.reminders) return false
        val today = LocalDate.now(clock)
        val occurrence = calculator.next(day, today)
        if (occurrence.solarDate.minusDays(offset.toLong()) != today) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "重要日子提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            dayId.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_IMPORTANT_DAY_ID, dayId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(day.name)
            .setContentText(reminderCopy(day.name, occurrence.daysRemaining, occurrence.solarDate, occurrence.adjustment))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(dayId.hashCode() * 31 + offset, notification)
        return true
    }

    companion object {
        const val CHANNEL_ID = "important_day_reminders"
    }
}

fun reminderCopy(name: String, daysRemaining: Long, date: LocalDate, adjustment: DateAdjustment?): String {
    val countdown = if (daysRemaining == 0L) "${name}就是今天" else "${name}还有 $daysRemaining 天"
    val adjustmentCopy = when (adjustment) {
        DateAdjustment.NON_LEAP_YEAR -> " · 本次提前 1 天：当前不是闰年"
        DateAdjustment.SHORT_LUNAR_MONTH -> " · 本次提前 1 天：当前农历月仅有二十九天"
        null -> ""
    }
    return "$countdown · ${date.monthValue}月${date.dayOfMonth}日$adjustmentCopy"
}
