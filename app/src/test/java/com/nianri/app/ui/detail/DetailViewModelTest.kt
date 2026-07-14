package com.nianri.app.ui.detail

import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DateAdjustment
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.LunarDate
import com.nianri.app.domain.model.adjustmentCopy
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
import kotlinx.coroutines.flow.MutableStateFlow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailViewModelTest {
    private val deleted = mutableListOf<Long>()
    private lateinit var days: MutableStateFlow<ImportantDay?>
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
        days = MutableStateFlow(day())
        val viewModel = viewModel()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val state = viewModel.uiState.value
        assertEquals("2027年2月28日", state.solarDate?.text)
        assertEquals("农历正月廿三", state.lunarDate?.text)
        assertEquals("今年不是闰年，本次提前 1 天", state.adjustmentCopy)
        assertEquals(2, state.widgetReferences)
        assertEquals("提前 14、3 天", state.reminderSummary)
    }

    @Test
    fun `detail delete calls coordinator`() {
        days = MutableStateFlow(day())
        val viewModel = viewModel()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        viewModel.delete()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(listOf(42L), deleted)
        assertTrue(viewModel.uiState.value.deleted)
    }

    @Test
    fun `detail refreshes when edited record emits after returning`() {
        days = MutableStateFlow(day())
        val viewModel = viewModel()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        days.value = day().copy(name = "编辑后的纪念日", reminders = emptySet())
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("编辑后的纪念日", viewModel.uiState.value.day?.name)
        assertEquals("未开启提醒", viewModel.uiState.value.reminderSummary)
    }

    @Test
    fun `conversion failure keeps record actions and metadata available`() {
        days = MutableStateFlow(day())
        val failingConverter = object : CalendarConverter {
            override fun lunarFromSolar(solarDate: LocalDate) = LunarDate(1, 1, false)
            override fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem): DisplayDate =
                error("conversion unavailable")
        }
        val viewModel = viewModel(failingConverter)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val state = viewModel.uiState.value
        assertEquals(42L, state.day?.id)
        assertEquals("提前 14、3 天", state.reminderSummary)
        assertEquals(2, state.widgetReferences)
        assertEquals("日期暂不可用", state.error)
    }

    @Test
    fun `short lunar month adjustment says occurrence moved one day earlier`() {
        assertEquals(
            "本月只有二十九天，本次提前 1 天",
            adjustmentCopy(DateAdjustment.SHORT_LUNAR_MONTH),
        )
    }

    private fun viewModel(calendarConverter: CalendarConverter = converter) = DetailViewModel(
        dayId = 42,
        day = days,
        countWidgetReferences = { 2 },
        deleteDay = { deleted += it },
        calculator = DateOccurrenceCalculator(calendarConverter),
        converter = calendarConverter,
        clock = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC),
    )

    private fun day() = ImportantDay(
        id = 42,
        name = "纪念日",
        basis = CalendarSystem.SOLAR,
        month = 2,
        day = 29,
        appDisplay = CalendarSystem.LUNAR,
        reminders = setOf(14, 3),
    )
}
