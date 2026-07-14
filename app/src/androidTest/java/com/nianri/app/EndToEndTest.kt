package com.nianri.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.data.WidgetRepository
import com.nianri.app.data.WidgetResolution
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.DayListProjector
import com.nianri.app.domain.DayMutationCoordinator
import com.nianri.app.domain.WidgetUpdater
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.calendar.IcuCalendarConverter
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.reminder.ReminderScheduleResult
import com.nianri.app.reminder.ReminderScheduler
import com.nianri.app.widget.WidgetModel
import com.nianri.app.widget.WidgetPresentationMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndToEndTest {
    @Test
    fun annualDaysKeepTheirBasisWhileAppAndWidgetsShareDisplayState() = runBlocking {
        EndToEndHarness().use { scenario ->
            val lunarId = scenario.createLunarDay(
                name = "妈妈生日",
                month = 6,
                day = 1,
                display = CalendarSystem.SOLAR,
            )

            val solarCard = scenario.card(lunarId)
            assertEquals(CalendarSystem.LUNAR, solarCard.day.basis)
            assertEquals(CalendarSystem.SOLAR, solarCard.displayedDate.calendar)
            assertEquals(
                "${solarCard.occurrence.solarDate.year}年" +
                    "${solarCard.occurrence.solarDate.monthValue}月" +
                    "${solarCard.occurrence.solarDate.dayOfMonth}日",
                solarCard.displayedDate.text,
            )

            scenario.changeAppDisplay(lunarId, CalendarSystem.LUNAR)
            val lunarCard = scenario.card(lunarId)
            assertEquals(solarCard.occurrence.solarDate, lunarCard.occurrence.solarDate)
            assertEquals(solarCard.occurrence.daysRemaining, lunarCard.occurrence.daysRemaining)
            assertEquals(CalendarSystem.LUNAR, lunarCard.displayedDate.calendar)
            assertNotEquals(solarCard.displayedDate.text, lunarCard.displayedDate.text)

            scenario.pin(lunarId)
            assertTrue(scenario.day(lunarId).isPinned)

            val solarId = scenario.createSolarDay(
                name = "相识纪念日",
                month = 8,
                day = 6,
                display = CalendarSystem.SOLAR,
            )
            scenario.selectWidget(
                appWidgetId = 1001,
                dayId = lunarId,
                display = CalendarSystem.SOLAR,
            )
            scenario.selectWidget(
                appWidgetId = 1002,
                dayId = solarId,
                display = CalendarSystem.LUNAR,
            )

            val firstWidget = scenario.widget(1001)
            val secondWidget = scenario.widget(1002)
            assertEquals(lunarId, firstWidget.id)
            assertEquals(CalendarSystem.LUNAR, firstWidget.display)
            assertEquals(solarId, secondWidget.id)
            assertEquals(CalendarSystem.SOLAR, secondWidget.display)

            scenario.delete(lunarId)
            assertTrue(scenario.widgetResolution(1001) is WidgetResolution.MissingDay)
            assertEquals(WidgetModel.MissingDay, scenario.widgetModel(1001))

            assertTrue(
                scenario.reconfigureWidget(
                    appWidgetId = 1001,
                    dayId = solarId,
                    display = CalendarSystem.SOLAR,
                ),
            )
            assertEquals(solarId, scenario.widget(1001).id)
            assertEquals(CalendarSystem.SOLAR, scenario.widget(1001).display)
            assertEquals(solarId, scenario.widget(1002).id)
            assertEquals(CalendarSystem.SOLAR, scenario.widget(1002).display)
        }
    }
}

private class EndToEndHarness : AutoCloseable {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, NianriDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val days = ImportantDayRepository(database)
    private val widgets = WidgetRepository(database)
    private val converter = IcuCalendarConverter()
    private val calculator = DateOccurrenceCalculator(converter)
    private val clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)
    private val coordinator = DayMutationCoordinator(
        days = days,
        reminders = NoOpReminderScheduler,
        widgets = WidgetUpdater { },
    )
    private val projector = DayListProjector(days, calculator, converter, clock)
    private val widgetMapper = WidgetPresentationMapper(calculator, converter, clock)

    suspend fun createLunarDay(
        name: String,
        month: Int,
        day: Int,
        display: CalendarSystem,
    ): Long = coordinator.save(
        ImportantDay(
            name = name,
            basis = CalendarSystem.LUNAR,
            month = month,
            day = day,
            appDisplay = display,
            reminders = emptySet(),
        ),
    )

    suspend fun createSolarDay(
        name: String,
        month: Int,
        day: Int,
        display: CalendarSystem,
    ): Long = coordinator.save(
        ImportantDay(
            name = name,
            basis = CalendarSystem.SOLAR,
            month = month,
            day = day,
            appDisplay = display,
            reminders = emptySet(),
        ),
    )

    suspend fun card(id: Long): DayCardModel.Ready = projector.observeAll()
        .first()
        .single { it.day.id == id } as DayCardModel.Ready

    suspend fun changeAppDisplay(id: Long, display: CalendarSystem) {
        days.updateAppDisplay(id, display)
    }

    suspend fun pin(id: Long) {
        coordinator.save(day(id).copy(isPinned = true))
    }

    suspend fun day(id: Long): ImportantDay = requireNotNull(days.get(id))

    suspend fun selectWidget(appWidgetId: Int, dayId: Long, display: CalendarSystem) {
        check(widgets.selectExistingDay(appWidgetId, dayId, display))
    }

    suspend fun widgetResolution(appWidgetId: Int): WidgetResolution = widgets.resolve(appWidgetId)

    suspend fun widgetModel(appWidgetId: Int): WidgetModel =
        widgetMapper.map(widgetResolution(appWidgetId))

    suspend fun widget(appWidgetId: Int): WidgetModel.Content =
        widgetModel(appWidgetId) as WidgetModel.Content

    suspend fun delete(id: Long) {
        coordinator.delete(id)
    }

    suspend fun reconfigureWidget(
        appWidgetId: Int,
        dayId: Long,
        display: CalendarSystem,
    ): Boolean = widgets.selectExistingDay(appWidgetId, dayId, display)

    override fun close() {
        database.close()
    }
}

private data object NoOpReminderScheduler : ReminderScheduler {
    override suspend fun replace(dayId: Long): ReminderScheduleResult =
        ReminderScheduleResult.Scheduled(count = 0)

    override suspend fun cancel(dayId: Long) = Unit

    override suspend fun rebuildAll() = Unit
}
