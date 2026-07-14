package com.nianri.app.domain.calendar

import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IcuCalendarConverterTest {
    private val converter = IcuCalendarConverter()

    @Test
    fun `2026 Chinese New Year converts to ordinary lunar first month first day`() {
        assertEquals(
            LunarDate(month = 1, day = 1, isLeapMonth = false),
            converter.lunarFromSolar(LocalDate.of(2026, 2, 17)),
        )
    }

    @Test
    fun `2025 leap sixth month is marked as leap`() {
        assertEquals(
            LunarDate(month = 6, day = 1, isLeapMonth = true),
            converter.lunarFromSolar(LocalDate.of(2025, 7, 25)),
        )
    }

    @Test
    fun `solar display uses Chinese year month day format`() {
        assertEquals(
            DisplayDate(CalendarSystem.SOLAR, "2026年2月17日"),
            converter.displayDate(LocalDate.of(2026, 2, 17), CalendarSystem.SOLAR),
        )
    }

    @Test
    fun `lunar display uses Chinese month and day text`() {
        assertEquals(
            DisplayDate(CalendarSystem.LUNAR, "正月初一"),
            converter.displayDate(LocalDate.of(2026, 2, 17), CalendarSystem.LUNAR),
        )
        assertEquals(
            DisplayDate(CalendarSystem.LUNAR, "闰六月初一"),
            converter.displayDate(LocalDate.of(2025, 7, 25), CalendarSystem.LUNAR),
        )
    }

    @Test
    fun `ICU illegal argument becomes conversion failure with cause and date context`() {
        val cause = IllegalArgumentException("ICU unavailable")
        val failingConverter = IcuCalendarConverter { throw cause }
        val solarDate = LocalDate.of(2026, 2, 17)

        val failure = assertThrows(CalendarConversionException::class.java) {
            failingConverter.lunarFromSolar(solarDate)
        }

        assertSame(cause, failure.cause)
        assertTrue(failure.message.orEmpty().contains(solarDate.toString()))
    }

    @Test
    fun `provider programmer exception propagates`() {
        val failingConverter = IcuCalendarConverter {
            throw NullPointerException("programmer defect")
        }

        assertThrows(NullPointerException::class.java) {
            failingConverter.lunarFromSolar(LocalDate.of(2026, 2, 17))
        }
    }
}
