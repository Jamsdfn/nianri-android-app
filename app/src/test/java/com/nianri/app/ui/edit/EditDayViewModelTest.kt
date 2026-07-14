package com.nianri.app.ui.edit

import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.WidgetUpdateUnavailableException
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.LunarDate
import com.nianri.app.reminder.ReminderPermissionController
import com.nianri.app.reminder.ReminderPermissionState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditDayViewModelTest {
    private val saved = mutableListOf<ImportantDay>()
    private val deleted = mutableListOf<Long>()
    private val converter = PredictableConverter()
    private val clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `blank name is rejected before save`() {
        val viewModel = viewModel()

        viewModel.setName("   ")
        viewModel.save()

        assertEquals("请输入名称", viewModel.uiState.value.nameError)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `basis selection changes active picker and accepts fallback dates`() {
        val viewModel = viewModel()

        viewModel.setBasis(CalendarSystem.SOLAR)
        viewModel.setMonth(2)
        viewModel.setDay(29)
        assertEquals(CalendarSystem.SOLAR, viewModel.uiState.value.activePicker)
        assertNull(viewModel.uiState.value.dateError)

        viewModel.setBasis(CalendarSystem.LUNAR)
        viewModel.setDay(30)
        assertEquals(CalendarSystem.LUNAR, viewModel.uiState.value.activePicker)
        assertNull(viewModel.uiState.value.dateError)
    }

    @Test
    fun `invalid month and dates are rejected`() {
        val viewModel = viewModel()
        viewModel.setName("生日")

        viewModel.setMonth(13)
        viewModel.save()
        assertEquals("月份应为 1 至 12", viewModel.uiState.value.dateError)

        viewModel.setMonth(2)
        viewModel.setDay(30)
        viewModel.save()
        assertEquals("该新历日期不存在", viewModel.uiState.value.dateError)

        viewModel.setBasis(CalendarSystem.LUNAR)
        viewModel.setDay(31)
        viewModel.save()
        assertEquals("农历日期应为初一至三十", viewModel.uiState.value.dateError)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `default reminders are independent and all may be disabled`() {
        val viewModel = viewModel()

        assertEquals(setOf(14, 7, 3), viewModel.uiState.value.reminders)
        viewModel.toggleReminder(14)
        assertEquals(setOf(7, 3), viewModel.uiState.value.reminders)
        viewModel.toggleReminder(7)
        viewModel.toggleReminder(3)

        assertTrue(viewModel.uiState.value.reminders.isEmpty())
    }

    @Test
    fun `permission state follows whether any reminder is selected`() {
        val permissions = FakePermissionController(ReminderPermissionState.WaitingForNotificationPermission)
        val viewModel = viewModel(permissions = permissions)

        assertEquals(ReminderPermissionState.WaitingForNotificationPermission, viewModel.uiState.value.permissionStatus)
        listOf(14, 7, 3).forEach(viewModel::toggleReminder)
        assertEquals(ReminderPermissionState.NotNeeded, viewModel.uiState.value.permissionStatus)
    }

    @Test
    fun `permission request attempts are recorded and state refreshes`() {
        val permissions = FakePermissionController(ReminderPermissionState.WaitingForNotificationPermission)
        val viewModel = viewModel(permissions = permissions)

        viewModel.notificationPermissionRequestStarted()
        permissions.current = ReminderPermissionState.Denied
        viewModel.refreshPermissionState()

        assertEquals(1, permissions.notificationStarts)
        assertEquals(ReminderPermissionState.Denied, viewModel.uiState.value.permissionStatus)
    }

    @Test
    fun `pre widget capability block is explained instead of reported as a generic save failure`() {
        val viewModel = EditDayViewModel(
            dayId = 0,
            loadDay = { null },
            saveDay = { throw WidgetUpdateUnavailableException("已有小部件配置，暂时无法修改") },
            deleteDay = {},
            calculator = DateOccurrenceCalculator(converter),
            converter = converter,
            clock = clock,
            permissionController = FakePermissionController(ReminderPermissionState.Ready),
        )
        viewModel.setName("生日")

        viewModel.save()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("已有小部件配置，暂时无法修改", viewModel.uiState.value.operationError)
    }

    @Test
    fun `preview keeps countdown basis separate from displayed calendar`() {
        val viewModel = viewModel()
        viewModel.setName("妈妈生日")
        viewModel.setBasis(CalendarSystem.LUNAR)
        viewModel.setMonth(8)
        viewModel.setDay(6)
        viewModel.setDisplay(CalendarSystem.SOLAR)

        val preview = viewModel.uiState.value.preview
        assertNotNull(preview)
        assertEquals(CalendarSystem.LUNAR, preview?.day?.basis)
        assertEquals(CalendarSystem.SOLAR, preview?.displayedDate?.calendar)
        assertTrue(preview?.displayedDate?.text?.startsWith("新历") == true)
    }

    @Test
    fun `editing basis recalculates preview`() {
        val viewModel = viewModel()
        viewModel.setName("日子")
        viewModel.setMonth(8)
        viewModel.setDay(6)
        val solarDate = viewModel.uiState.value.preview?.occurrence?.solarDate

        viewModel.setBasis(CalendarSystem.LUNAR)
        val lunarDate = viewModel.uiState.value.preview?.occurrence?.solarDate

        assertEquals(LocalDate.of(2026, 8, 6), solarDate)
        assertEquals(LocalDate.of(2026, 8, 16), lunarDate)
    }

    @Test
    fun `editing loads existing values and save preserves id`() {
        val existing = day(id = 42, basis = CalendarSystem.LUNAR, display = CalendarSystem.SOLAR)
        val viewModel = viewModel(existing = existing)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("妈妈生日", viewModel.uiState.value.name)
        viewModel.setPinned(true)
        viewModel.save()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(42L, saved.single().id)
        assertTrue(saved.single().isPinned)
        assertEquals(42L, viewModel.uiState.value.savedId)
    }

    @Test
    fun `delete calls coordinator`() {
        val viewModel = viewModel(existing = day(id = 42))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        viewModel.delete()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(listOf(42L), deleted)
        assertTrue(viewModel.uiState.value.deleted)
    }

    private fun viewModel(
        existing: ImportantDay? = null,
        permissions: ReminderPermissionController = FakePermissionController(ReminderPermissionState.Ready),
    ) = EditDayViewModel(
        dayId = existing?.id ?: 0,
        loadDay = { existing },
        saveDay = {
            saved += it
            it.id.takeIf { id -> id != 0L } ?: 99L
        },
        deleteDay = { deleted += it },
        calculator = DateOccurrenceCalculator(converter),
        converter = converter,
        clock = clock,
        permissionController = permissions,
    )

    private fun day(
        id: Long = 0,
        basis: CalendarSystem = CalendarSystem.SOLAR,
        display: CalendarSystem = CalendarSystem.SOLAR,
    ) = ImportantDay(
        id = id,
        name = "妈妈生日",
        basis = basis,
        month = 8,
        day = 6,
        appDisplay = display,
    )

    private class PredictableConverter : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate): LunarDate =
            if (solarDate == LocalDate.of(2026, 8, 16)) LunarDate(8, 6, false)
            else LunarDate(1, 1, false)

        override fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem) =
            DisplayDate(calendarSystem, "${if (calendarSystem == CalendarSystem.SOLAR) "新历" else "农历"}$solarDate")
    }

    private class FakePermissionController(
        var current: ReminderPermissionState,
    ) : ReminderPermissionController {
        var notificationStarts = 0
        var exactStarts = 0

        override fun state(hasReminders: Boolean): ReminderPermissionState =
            if (hasReminders) current else ReminderPermissionState.NotNeeded

        override fun notificationRequestStarted() {
            notificationStarts += 1
        }

        override fun exactAlarmRequestStarted() {
            exactStarts += 1
        }
    }
}
