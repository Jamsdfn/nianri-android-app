package com.nianri.app.reminder

import android.app.AlarmManager
import android.content.Context
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
    fun `enabled offsets schedule unique exact alarms at local 09 00`() = runBlocking {
        val result = scheduler().replace(42)

        assertEquals(ReminderScheduleResult.Scheduled(3), result)
        val scheduled = alarms.scheduledAlarms.sortedBy { it.triggerAtTime }
        assertEquals(3, scheduled.size)
        assertEquals(
            listOf(14, 7, 3),
            scheduled.map { shadowOf(it.operation).savedIntent.getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1) },
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 8, 3),
            ).map { it.atTime(9, 0).atZone(zone).toInstant().toEpochMilli() },
            scheduled.map { it.triggerAtTime },
        )
        assertTrue(scheduled.all { it.allowWhileIdle })
        assertNotEquals(scheduled[0].operation, scheduled[1].operation)
        assertNotEquals(scheduled[1].operation, scheduled[2].operation)
    }

    @Test
    fun `past reminder dates are skipped`() = runBlocking {
        records = listOf(day(month = 7, date = 15))

        assertEquals(ReminderScheduleResult.Scheduled(0), scheduler().replace(42))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `replacing a day removes its existing requests before recreating them`() = runBlocking {
        val scheduler = scheduler()
        scheduler.replace(42)
        records = listOf(day(reminders = setOf(7)))

        scheduler.replace(42)

        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(
            7,
            shadowOf(alarms.scheduledAlarms.single().operation)
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
    fun `a day with no selected reminders schedules nothing`() = runBlocking {
        records = listOf(day(reminders = emptySet()))

        assertEquals(ReminderScheduleResult.Scheduled(0), scheduler().replace(42))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `rebuild all is idempotent`() = runBlocking {
        val scheduler = scheduler()
        scheduler.rebuildAll()
        scheduler.rebuildAll()

        assertEquals(3, alarms.scheduledAlarms.size)
    }

    private fun scheduler() = AndroidReminderScheduler(
        context = context,
        loadDay = { id -> records.singleOrNull { it.id == id } },
        loadAllDays = { records },
        calculator = DateOccurrenceCalculator(SolarOnlyConverter),
        clock = clock,
    )

    private fun day(
        month: Int = 8,
        date: Int = 6,
        reminders: Set<Int> = setOf(14, 7, 3),
    ) = ImportantDay(
        id = 42,
        name = "妈妈生日",
        basis = CalendarSystem.SOLAR,
        month = month,
        day = date,
        appDisplay = CalendarSystem.SOLAR,
        reminders = reminders,
    )

    private data object SolarOnlyConverter : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate): LunarDate = error("not used")

        override fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem) =
            DisplayDate(calendarSystem, solarDate.toString())
    }
}
