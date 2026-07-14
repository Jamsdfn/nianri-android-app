package com.nianri.app.domain

import androidx.room.Room
import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.reminder.ReminderScheduler
import com.nianri.app.reminder.ReminderScheduleResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayMutationCoordinatorTest {
    private lateinit var database: NianriDatabase
    private lateinit var days: ImportantDayRepository
    private lateinit var events: MutableList<String>
    private lateinit var coordinator: DayMutationCoordinator

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NianriDatabase::class.java,
        ).build()
        days = ImportantDayRepository(database)
        events = mutableListOf()
        coordinator = DayMutationCoordinator(
            days = days,
            reminders = RecordingReminderScheduler(days, events),
            widgets = WidgetUpdater { events += "widgets" },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save persists before replacing reminders then updates widgets`() = runBlocking {
        val id = coordinator.save(day(name = "妈妈生日"))

        assertEquals("妈妈生日", days.get(id)?.name)
        assertEquals(listOf("replace:$id:妈妈生日", "widgets"), events)
    }

    @Test
    fun `delete cancels reminders before deleting data then updates widgets`() = runBlocking {
        val id = days.save(day(name = "纪念日"))

        coordinator.delete(id)

        assertNull(days.get(id))
        assertEquals(listOf("cancel:$id:纪念日", "widgets"), events)
    }

    private fun day(name: String) = ImportantDay(
        name = name,
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
    )

    private class RecordingReminderScheduler(
        private val days: ImportantDayRepository,
        private val events: MutableList<String>,
    ) : ReminderScheduler {
        override suspend fun replace(dayId: Long): ReminderScheduleResult {
            events += "replace:$dayId:${days.get(dayId)?.name}"
            return ReminderScheduleResult.Scheduled(0)
        }

        override suspend fun cancel(dayId: Long) {
            events += "cancel:$dayId:${days.get(dayId)?.name}"
        }

        override suspend fun rebuildAll() = Unit
    }
}
