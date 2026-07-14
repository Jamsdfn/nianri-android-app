package com.nianri.app

import android.app.AlarmManager
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.WidgetUpdateUnavailableException
import com.nianri.app.reminder.ReminderPermissionController
import com.nianri.app.reminder.ReminderPermissionState
import com.nianri.app.ui.edit.EditDayViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppContainerMutationIntegrationTest {
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        container = AppContainer(RuntimeEnvironment.getApplication())
        withContext(Dispatchers.IO) { container.database.clearAllTables() }
    }

    @After
    fun tearDown() {
        container.database.close()
        ShadowAlarmManager.reset()
    }

    @Test
    fun `real container save and delete expose successful navigation states before widget providers exist`() {
        val create = viewModel(dayId = 0)
        create.setName("妈妈生日")
        create.setMonth(8)
        create.setDay(6)
        listOf(14, 7, 3).forEach(create::toggleReminder)

        create.save()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedId = create.uiState.value.savedId
        assertNotNull(savedId)
        assertNull(create.uiState.value.operationError)
        assertNotNull(runBlocking { container.importantDays.get(requireNotNull(savedId)) })

        val edit = viewModel(dayId = requireNotNull(savedId))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        edit.delete()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(edit.uiState.value.deleted)
        assertNull(edit.uiState.value.operationError)
        assertNull(runBlocking { container.importantDays.get(savedId) })
    }

    @Test
    fun `pre provider bridge blocks referenced widget mutation before data or alarm side effects`() = runBlocking {
        val id = container.importantDays.save(
            day(name = "原名称").copy(reminders = setOf(14, 7, 3)),
        )
        container.reminderScheduler.replace(id)
        val alarms = shadowOf(
            RuntimeEnvironment.getApplication()
                .getSystemService(AlarmManager::class.java),
        )
        val originalAlarmCount = alarms.scheduledAlarms.size
        container.widgets.select(101, id, CalendarSystem.SOLAR)

        val deleteFailure = assertThrows(WidgetUpdateUnavailableException::class.java) {
            runBlocking { container.dayMutationCoordinator.delete(id) }
        }
        assertTrue(deleteFailure.message.orEmpty().contains("小部件"))
        assertNotNull(container.importantDays.get(id))
        assertEquals(originalAlarmCount, alarms.scheduledAlarms.size)

        val saveFailure = assertThrows(WidgetUpdateUnavailableException::class.java) {
            runBlocking {
                container.dayMutationCoordinator.save(day(id = id, name = "新名称"))
            }
        }
        assertTrue(saveFailure.message.orEmpty().contains("小部件"))
        assertEquals("原名称", container.importantDays.get(id)?.name)
        assertEquals(originalAlarmCount, alarms.scheduledAlarms.size)
    }

    private fun viewModel(dayId: Long) = EditDayViewModel(
        dayId = dayId,
        loadDay = container.importantDays::get,
        saveDay = { day -> runBlocking { container.dayMutationCoordinator.save(day) } },
        deleteDay = { id -> runBlocking { container.dayMutationCoordinator.delete(id) } },
        countWidgetReferences = container.widgets::countReferences,
        calculator = container.occurrenceCalculator,
        converter = container.calendarConverter,
        clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC),
        permissionController = ReadyPermissionController,
    )

    private fun day(id: Long = 0, name: String) = ImportantDay(
        id = id,
        name = name,
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
        reminders = emptySet(),
    )

    private data object ReadyPermissionController : ReminderPermissionController {
        override fun state(hasReminders: Boolean): ReminderPermissionState =
            if (hasReminders) ReminderPermissionState.Ready else ReminderPermissionState.NotNeeded

        override fun notificationRequestStarted() = Unit
        override fun exactAlarmRequestStarted() = Unit
    }
}
