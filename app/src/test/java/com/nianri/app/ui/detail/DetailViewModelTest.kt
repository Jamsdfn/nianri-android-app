package com.nianri.app.ui.detail

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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailViewModelTest {
    private val deleted = mutableListOf<Long>()
    private val converter = object : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate) = LunarDate(1, 23, false)
        override fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem) =
            DisplayDate(
                calendarSystem,
                if (calendarSystem == CalendarSystem.SOLAR) "2027年2月28日" else "农历正月廿三",
            )
    }

    @Test
    fun `detail shows both calendars and non leap adjustment`() {
        val viewModel = viewModel()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val state = viewModel.uiState.value
        assertEquals("2027年2月28日", state.solarDate?.text)
        assertEquals("农历正月廿三", state.lunarDate?.text)
        assertEquals("非闰年按 2 月 28 日计算", state.adjustmentCopy)
        assertEquals(2, state.widgetReferences)
        assertEquals("提前 14、3 天", state.reminderSummary)
    }

    @Test
    fun `detail delete calls coordinator`() {
        val viewModel = viewModel()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        viewModel.delete()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(listOf(42L), deleted)
        assertTrue(viewModel.uiState.value.deleted)
    }

    private fun viewModel() = DetailViewModel(
        dayId = 42,
        loadDay = {
            ImportantDay(
                id = 42,
                name = "纪念日",
                basis = CalendarSystem.SOLAR,
                month = 2,
                day = 29,
                appDisplay = CalendarSystem.LUNAR,
                reminders = setOf(14, 3),
            )
        },
        countWidgetReferences = { 2 },
        deleteDay = { deleted += it },
        calculator = DateOccurrenceCalculator(converter),
        converter = converter,
        clock = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
}
