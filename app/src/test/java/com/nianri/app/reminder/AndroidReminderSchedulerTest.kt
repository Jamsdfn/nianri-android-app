package com.nianri.app.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.LunarDate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class AndroidReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), zone)
    private lateinit var context: Context
    private lateinit var alarms: ShadowAlarmManager
    private var records = listOf(day())

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun `enabled offsets schedule unique exact alarms at the day reminder time`() = runBlocking {
        records = listOf(day(reminderTimeMinutes = 8 * 60 + 35))

        val result = scheduler().replace(42)

        assertEquals(ReminderScheduleResult.Scheduled(4), result)
        val scheduled = alarms.scheduledAlarms.sortedBy { it.triggerAtTime }
        assertEquals(4, scheduled.size)
        assertEquals(
            listOf(14, 7, 3, 0),
            scheduled.map { shadowOf(it.operation).savedIntent.getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1) },
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 6),
            ).map { it.atTime(8, 35).atZone(zone).toInstant().toEpochMilli() },
            scheduled.map { it.triggerAtTime },
        )
        assertTrue(scheduled.all { it.allowWhileIdle })
        assertNotEquals(scheduled[0].operation, scheduled[1].operation)
        assertNotEquals(scheduled[1].operation, scheduled[2].operation)
    }

    @Test
    fun `past optional reminder dates are skipped but day of remains`() = runBlocking {
        records = listOf(day(month = 7, date = 15))

        assertEquals(ReminderScheduleResult.Scheduled(1), scheduler().replace(42))
        assertEquals(0, shadowOf(alarms.scheduledAlarms.single().operation).savedIntent
            .getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1))
    }

    @Test
    fun `replacing a day removes its existing requests before recreating them`() = runBlocking {
        val scheduler = scheduler()
        scheduler.replace(42)
        records = listOf(day(reminders = setOf(7)))

        scheduler.replace(42)

        assertEquals(2, alarms.scheduledAlarms.size)
        assertEquals(
            7,
            shadowOf(alarms.scheduledAlarms.minBy { it.triggerAtTime }.operation)
                .savedIntent.getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1),
        )
    }

    @Test
    fun `missing exact alarm access is reported without inexact fallback`() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertEquals(ReminderScheduleResult.NeedsExactAlarmPermission, scheduler().replace(42))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `a day with no selected optional reminders still schedules day of`() = runBlocking {
        records = listOf(day(reminders = emptySet()))

        assertEquals(ReminderScheduleResult.Scheduled(1), scheduler().replace(42))
        assertEquals(0, shadowOf(alarms.scheduledAlarms.single().operation).savedIntent
            .getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1))
    }

    @Test
    fun `rebuilding after nine on occurrence day dispatches immediate day of reminder`() = runBlocking {
        records = listOf(day(month = 7, date = 14, reminders = emptySet()))
        val dispatched = mutableListOf<Intent>()
        val afterNine = Clock.fixed(Instant.parse("2026-07-14T02:00:00Z"), zone)

        assertEquals(ReminderScheduleResult.Scheduled(0), scheduler(afterNine, dispatched::add).replace(42))
        assertEquals(listOf(0), dispatched.map { it.getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1) })
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `same day catch up uses custom reminder time boundary`() = runBlocking {
        records = listOf(
            day(
                month = 7,
                date = 14,
                reminders = emptySet(),
                reminderTimeMinutes = 10 * 60 + 30,
            ),
        )
        val dispatched = mutableListOf<Intent>()
        val before = Clock.fixed(Instant.parse("2026-07-14T02:29:00Z"), zone)

        assertEquals(ReminderScheduleResult.Scheduled(1), scheduler(before, dispatched::add).replace(42))
        assertTrue(dispatched.isEmpty())
        assertEquals(
            LocalDate.of(2026, 7, 14).atTime(10, 30).atZone(zone).toInstant().toEpochMilli(),
            alarms.scheduledAlarms.single().triggerAtTime,
        )

        val atBoundary = Clock.fixed(Instant.parse("2026-07-14T02:30:00Z"), zone)
        assertEquals(
            ReminderScheduleResult.Scheduled(0),
            scheduler(atBoundary, dispatched::add).replace(42),
        )
        assertEquals(
            listOf(0),
            dispatched.map { it.getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1) },
        )
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `rebuild all is idempotent`() = runBlocking {
        val scheduler = scheduler()
        scheduler.rebuildAll()
        scheduler.rebuildAll()

        assertEquals(4, alarms.scheduledAlarms.size)
    }

    private fun scheduler(
        schedulerClock: Clock = clock,
        immediateDispatcher: (Intent) -> Unit = {},
    ) = AndroidReminderScheduler(
        context = context,
        loadDay = { id -> records.singleOrNull { it.id == id } },
        loadAllDays = { records },
        calculator = DateOccurrenceCalculator(SolarOnlyConverter),
        clock = schedulerClock,
        immediateDispatcher = immediateDispatcher,
    )

    private fun day(
        month: Int = 8,
        date: Int = 6,
        reminders: Set<Int> = setOf(14, 7, 3),
        reminderTimeMinutes: Int = 9 * 60,
    ) = ImportantDay(
        id = 42,
        name = "妈妈生日",
        basis = CalendarSystem.SOLAR,
        month = month,
        day = date,
        appDisplay = CalendarSystem.SOLAR,
        reminders = reminders,
        reminderTimeMinutes = reminderTimeMinutes,
    )

    private data object SolarOnlyConverter : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate): LunarDate = error("not used")

        override fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem) =
            DisplayDate(calendarSystem, solarDate.toString())
    }
}
