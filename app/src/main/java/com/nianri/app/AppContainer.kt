package com.nianri.app

import android.content.Context
import androidx.room.Room
import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.data.WidgetRepository
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.DayListProjector
import com.nianri.app.domain.DayMutationCoordinator
import com.nianri.app.domain.WidgetUpdater
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.calendar.IcuCalendarConverter
import com.nianri.app.reminder.ReminderScheduler
import java.time.Clock

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: NianriDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            NianriDatabase::class.java,
            "nianri.db",
        ).build()
    }
    val calendarConverter by lazy { IcuCalendarConverter() }
    val occurrenceCalculator by lazy { DateOccurrenceCalculator(calendarConverter) }
    val importantDays by lazy { ImportantDayRepository(database) }
    val widgets by lazy { WidgetRepository(database) }
    val reminderScheduler: ReminderScheduler by lazy { NoOpReminderScheduler }
    val widgetUpdater: WidgetUpdater by lazy { WidgetUpdater { } }
    val dayListProjector by lazy {
        DayListProjector(
            days = importantDays,
            calculator = occurrenceCalculator,
            converter = calendarConverter,
            clock = Clock.systemDefaultZone(),
        )
    }
    val dayMutationCoordinator by lazy {
        DayMutationCoordinator(
            days = importantDays,
            reminders = reminderScheduler,
            widgets = widgetUpdater,
        )
    }

    private data object NoOpReminderScheduler : ReminderScheduler {
        override suspend fun replace(dayId: Long) = Unit

        override suspend fun cancel(dayId: Long) = Unit

        override suspend fun rebuildAll() = Unit
    }
}
