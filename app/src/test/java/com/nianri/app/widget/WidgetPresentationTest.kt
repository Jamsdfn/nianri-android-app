package com.nianri.app.widget

import com.nianri.app.data.WidgetResolution
import com.nianri.app.domain.calendar.CalendarConversionException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPresentationTest {
    private val converter = FakeConverter()
    private val mapper = WidgetPresentationMapper(
        calculator = DateOccurrenceCalculator(converter),
        converter = converter,
        clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `display toggle changes only converted date text`() {
        val day = day()

        val solar = mapper.map(WidgetResolution.Configured(day, CalendarSystem.SOLAR))
        val lunar = mapper.map(WidgetResolution.Configured(day, CalendarSystem.LUNAR))

        assertEquals(WidgetModel.Content(7, "妈妈生日", 23, "按新历", "新历 8月6日", CalendarSystem.SOLAR), solar)
        assertEquals(WidgetModel.Content(7, "妈妈生日", 23, "按新历", "农历 六月廿四", CalendarSystem.LUNAR), lunar)
    }

    @Test
    fun `unconfigured and deleted selections stay explicit`() {
        assertEquals(WidgetModel.Unconfigured, mapper.map(WidgetResolution.Unconfigured))
        assertEquals(WidgetModel.MissingDay, mapper.map(WidgetResolution.MissingDay))
    }

    @Test
    fun `conversion failure keeps record identity and edit information`() {
        converter.failConversion = true

        val model = mapper.map(WidgetResolution.Configured(day(), CalendarSystem.LUNAR))

        assertEquals(
            WidgetModel.DateUnavailable(
                id = 7,
                name = "妈妈生日",
                basisLabel = "按新历",
                display = CalendarSystem.LUNAR,
            ),
            model,
        )
    }

    @Test
    fun `occurrence failure is unavailable instead of an incorrect countdown`() {
        val lunarDay = day().copy(basis = CalendarSystem.LUNAR, month = 1, day = 1)

        assertTrue(mapper.map(WidgetResolution.Configured(lunarDay, CalendarSystem.SOLAR)) is WidgetModel.DateUnavailable)
    }

    private fun day() = ImportantDay(
        id = 7,
        name = "妈妈生日",
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
    )

    private class FakeConverter : CalendarConverter {
        var failConversion = false

        override fun lunarFromSolar(solarDate: LocalDate): LunarDate = LunarDate(2, 2, false)

        override fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem): DisplayDate {
            if (failConversion) throw CalendarConversionException("test failure")
            return DisplayDate(
                calendarSystem,
                when (calendarSystem) {
                    CalendarSystem.SOLAR -> "新历 8月6日"
                    CalendarSystem.LUNAR -> "农历 六月廿四"
                },
            )
        }
    }
}
