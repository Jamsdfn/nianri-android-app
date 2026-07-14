package com.nianri.app.domain.calendar

import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DateAdjustment
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateOccurrenceCalculatorTest {
    private val converter = object : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate): LunarDate =
            error("Lunar conversion is not expected in a solar test")

        override fun displayDate(
            solarDate: LocalDate,
            calendarSystem: CalendarSystem,
        ): DisplayDate = error("Display conversion is not expected in an occurrence test")
    }
    private val calculator = DateOccurrenceCalculator(converter)

    @Test
    fun `solar occurrence today has zero days remaining`() {
        val today = LocalDate.of(2026, 7, 14)

        val occurrence = calculator.next(solarDay(month = 7, day = 14), today)

        assertEquals(today, occurrence.solarDate)
        assertEquals(0, occurrence.daysRemaining)
        assertEquals(null, occurrence.adjustment)
    }

    @Test
    fun `past solar date rolls to next year`() {
        val occurrence = calculator.next(
            solarDay(month = 3, day = 8),
            LocalDate.of(2026, 7, 14),
        )

        assertEquals(LocalDate.of(2027, 3, 8), occurrence.solarDate)
        assertEquals(237, occurrence.daysRemaining)
        assertEquals(null, occurrence.adjustment)
    }

    @Test
    fun `future solar date stays in current year`() {
        val occurrence = calculator.next(
            solarDay(month = 10, day = 1),
            LocalDate.of(2026, 7, 14),
        )

        assertEquals(LocalDate.of(2026, 10, 1), occurrence.solarDate)
        assertEquals(79, occurrence.daysRemaining)
        assertEquals(null, occurrence.adjustment)
    }

    @Test
    fun `February 29 stays February 29 in a leap year`() {
        val occurrence = calculator.next(
            solarDay(month = 2, day = 29),
            LocalDate.of(2028, 1, 1),
        )

        assertEquals(LocalDate.of(2028, 2, 29), occurrence.solarDate)
        assertEquals(59, occurrence.daysRemaining)
        assertEquals(null, occurrence.adjustment)
    }

    @Test
    fun `February 29 becomes February 28 in a non-leap year`() {
        val occurrence = calculator.next(
            solarDay(month = 2, day = 29),
            LocalDate.of(2026, 1, 1),
        )

        assertEquals(LocalDate.of(2026, 2, 28), occurrence.solarDate)
        assertEquals(58, occurrence.daysRemaining)
        assertEquals(DateAdjustment.NON_LEAP_YEAR, occurrence.adjustment)
    }

    @Test
    fun `ordinary lunar occurrence ignores a matching leap-month copy`() {
        val today = LocalDate.of(2025, 7, 25)
        val ordinaryOccurrence = today.plusDays(355)
        val lunarConverter = FakeCalendarConverter { date ->
            when (date) {
                today -> LunarDate(month = 6, day = 1, isLeapMonth = true)
                ordinaryOccurrence -> LunarDate(month = 6, day = 1, isLeapMonth = false)
                else -> LunarDate(month = 12, day = 1, isLeapMonth = false)
            }
        }

        val occurrence = DateOccurrenceCalculator(lunarConverter).next(
            lunarDay(month = 6, day = 1),
            today,
        )

        assertEquals(ordinaryOccurrence, occurrence.solarDate)
        assertEquals(355, occurrence.daysRemaining)
        assertEquals(null, occurrence.adjustment)
    }

    @Test
    fun `lunar day 30 falls back to day 29 at end of short ordinary month`() {
        val today = LocalDate.of(2026, 4, 16)
        val lunarConverter = FakeCalendarConverter { date ->
            when (date) {
                today -> LunarDate(month = 2, day = 29, isLeapMonth = false)
                today.plusDays(1) -> LunarDate(month = 3, day = 1, isLeapMonth = false)
                else -> LunarDate(month = 12, day = 1, isLeapMonth = false)
            }
        }

        val occurrence = DateOccurrenceCalculator(lunarConverter).next(
            lunarDay(month = 2, day = 30),
            today,
        )

        assertEquals(today, occurrence.solarDate)
        assertEquals(0, occurrence.daysRemaining)
        assertEquals(DateAdjustment.SHORT_LUNAR_MONTH, occurrence.adjustment)
    }

    @Test
    fun `lunar day 29 is not a fallback when ordinary month has day 30`() {
        val today = LocalDate.of(2026, 3, 17)
        val exactOccurrence = today.plusDays(1)
        val lunarConverter = FakeCalendarConverter { date ->
            when (date) {
                today -> LunarDate(month = 1, day = 29, isLeapMonth = false)
                exactOccurrence -> LunarDate(month = 1, day = 30, isLeapMonth = false)
                else -> LunarDate(month = 12, day = 1, isLeapMonth = false)
            }
        }

        val occurrence = DateOccurrenceCalculator(lunarConverter).next(
            lunarDay(month = 1, day = 30),
            today,
        )

        assertEquals(exactOccurrence, occurrence.solarDate)
        assertEquals(1, occurrence.daysRemaining)
        assertEquals(null, occurrence.adjustment)
    }

    @Test
    fun `lunar scan finds occurrence after solar year boundary`() {
        val today = LocalDate.of(2026, 12, 31)
        val nextYearOccurrence = LocalDate.of(2027, 1, 2)
        val lunarConverter = FakeCalendarConverter { date ->
            if (date == nextYearOccurrence) {
                LunarDate(month = 11, day = 25, isLeapMonth = false)
            } else {
                LunarDate(month = 12, day = 1, isLeapMonth = false)
            }
        }

        val occurrence = DateOccurrenceCalculator(lunarConverter).next(
            lunarDay(month = 11, day = 25),
            today,
        )

        assertEquals(nextYearOccurrence, occurrence.solarDate)
        assertEquals(2, occurrence.daysRemaining)
    }

    private fun solarDay(month: Int, day: Int) = ImportantDay(
        name = "纪念日",
        basis = CalendarSystem.SOLAR,
        month = month,
        day = day,
        appDisplay = CalendarSystem.SOLAR,
    )

    private fun lunarDay(month: Int, day: Int) = ImportantDay(
        name = "农历纪念日",
        basis = CalendarSystem.LUNAR,
        month = month,
        day = day,
        appDisplay = CalendarSystem.LUNAR,
    )

    private class FakeCalendarConverter(
        private val conversion: (LocalDate) -> LunarDate,
    ) : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate): LunarDate = conversion(solarDate)

        override fun displayDate(
            solarDate: LocalDate,
            calendarSystem: CalendarSystem,
        ): DisplayDate = error("Display conversion is not expected in an occurrence test")
    }
}
