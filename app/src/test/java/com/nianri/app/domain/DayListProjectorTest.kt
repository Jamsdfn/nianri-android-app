package com.nianri.app.domain

import androidx.room.Room
import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.LunarDate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayListProjectorTest {
    private lateinit var database: NianriDatabase
    private lateinit var days: ImportantDayRepository
    private val clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NianriDatabase::class.java,
        ).build()
        days = ImportantDayRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `sorts pinned first then ready before unavailable then nearest date and name`() = runBlocking {
        days.save(day("同日 B", month = 7, day = 20))
        days.save(day("更远", month = 8, day = 1))
        days.save(day("同日 A", month = 7, day = 20))
        days.save(day("失效", month = 7, day = 15, display = CalendarSystem.LUNAR))
        days.save(day("置顶", month = 12, day = 31, pinned = true))
        val converter = SelectiveConverter()
        val projector = DayListProjector(days, DateOccurrenceCalculator(converter), converter, clock)

        val models = projector.observeAll().first()

        assertEquals(listOf("置顶", "同日 A", "同日 B", "更远", "失效"), models.map { it.day.name })
        assertTrue(models.last() is DayCardModel.Unavailable)
    }

    @Test
    fun `display conversion failure produces unavailable instead of a false countdown`() = runBlocking {
        days.save(day("转换失败", month = 7, day = 15, display = CalendarSystem.LUNAR))
        val converter = SelectiveConverter()
        val projector = DayListProjector(days, DateOccurrenceCalculator(converter), converter, clock)

        val model = projector.observeAll().first().single()

        assertEquals(DayCardModel.Unavailable(model.day), model)
    }

    @Test
    fun `occurrence calculation failure produces unavailable`() = runBlocking {
        days.save(
            ImportantDay(
                name = "计算失败",
                basis = CalendarSystem.LUNAR,
                month = 6,
                day = 1,
                appDisplay = CalendarSystem.SOLAR,
            ),
        )
        val converter = SelectiveConverter()
        val projector = DayListProjector(days, DateOccurrenceCalculator(converter), converter, clock)

        val model = projector.observeAll().first().single()

        assertTrue(model is DayCardModel.Unavailable)
    }

    private fun day(
        name: String,
        month: Int,
        day: Int,
        display: CalendarSystem = CalendarSystem.SOLAR,
        pinned: Boolean = false,
    ) = ImportantDay(
        name = name,
        basis = CalendarSystem.SOLAR,
        month = month,
        day = day,
        appDisplay = display,
        isPinned = pinned,
    )

    private class SelectiveConverter : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate): LunarDate =
            error("Lunar calculation is not expected")

        override fun displayDate(
            solarDate: LocalDate,
            calendarSystem: CalendarSystem,
        ): DisplayDate = when (calendarSystem) {
            CalendarSystem.SOLAR -> DisplayDate(calendarSystem, solarDate.toString())
            CalendarSystem.LUNAR -> error("Conversion unavailable")
        }
    }
}
