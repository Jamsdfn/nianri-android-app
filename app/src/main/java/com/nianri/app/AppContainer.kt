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
import java.time.Instant
import java.time.ZoneId

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
    val reminderScheduler: ReminderScheduler by lazy { DeferredReminderScheduler }
    val widgetUpdater: WidgetUpdater by lazy { DeferredWidgetUpdater }
    val dayListProjector by lazy {
        DayListProjector(
            days = importantDays,
            calculator = occurrenceCalculator,
            converter = calendarConverter,
            clock = CurrentSystemZoneClock(),
        )
    }
    val dayMutationCoordinator by lazy {
        DayMutationCoordinator(
            days = importantDays,
            reminders = reminderScheduler,
            widgets = widgetUpdater,
        )
    }
}

class CurrentSystemZoneClock(
    private val instantSource: Clock = Clock.systemUTC(),
    private val zoneSupplier: () -> ZoneId = ZoneId::systemDefault,
) : Clock() {
    override fun getZone(): ZoneId = zoneSupplier()

    override fun withZone(zone: ZoneId): Clock = instantSource.withZone(zone)

    override fun instant(): Instant = instantSource.instant()
}

internal data object DeferredReminderScheduler : ReminderScheduler {
    override suspend fun replace(dayId: Long): Nothing = unwired()

    override suspend fun cancel(dayId: Long): Nothing = unwired()

    override suspend fun rebuildAll(): Nothing = unwired()

    private fun unwired(): Nothing = throw IllegalStateException(
        "ReminderScheduler adapter is not wired; Task 7 must provide the Android binding",
    )
}

internal data object DeferredWidgetUpdater : WidgetUpdater {
    override suspend fun updateAll(): Nothing = throw IllegalStateException(
        "WidgetUpdater adapter is not wired; Task 8 or 9 must provide the Android binding",
    )
}
