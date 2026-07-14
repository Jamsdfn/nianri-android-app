package com.nianri.app

import android.content.Context
import androidx.room.Room
import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.data.WidgetRepository
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.DayListProjector
import com.nianri.app.domain.DayMutationCoordinator
import com.nianri.app.domain.WidgetUpdater
import com.nianri.app.domain.WidgetUpdateUnavailableException
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.calendar.IcuCalendarConverter
import com.nianri.app.reminder.AndroidReminderScheduler
import com.nianri.app.reminder.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext

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
    val reminderScheduler: ReminderScheduler by lazy {
        AndroidReminderScheduler(
            context = applicationContext,
            loadDay = importantDays::get,
            loadAllDays = { importantDays.observeAll().first() },
            calculator = occurrenceCalculator,
            clock = CurrentSystemZoneClock(),
        )
    }
    val widgetUpdater: WidgetUpdater by lazy {
        PreProviderWidgetUpdater(widgets::hasConfiguredWidgets)
    }
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

internal class PreProviderWidgetUpdater(
    private val hasConfiguredWidgets: suspend () -> Boolean,
) : WidgetUpdater {
    override suspend fun prepareMutation() {
        if (hasConfiguredWidgets()) {
            throw WidgetUpdateUnavailableException(
                "已有小部件配置，完成小部件更新能力前不能修改日子",
            )
        }
    }

    override suspend fun updateAll() = Unit
}
