package com.nianri.app.reminder

import android.app.NotificationManager
import android.Manifest
import android.content.Context
import com.nianri.app.MainActivity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderReceiverTest {
    private lateinit var context: Context
    private lateinit var notifications: NotificationManager
    private var day: ImportantDay? = standardDay()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancelAll()
    }

    @Test
    fun `matching reminder posts dated copy and opens matching detail`() = runBlocking {
        val delivered = service(onDate = LocalDate.of(2026, 7, 30)).deliver(42, 7)

        assertTrue(delivered)
        assertEquals("important_day_reminders", shadowOf(notifications).notificationChannels.single().id)
        val notification = shadowOf(notifications).allNotifications.single()
        assertEquals("妈妈生日还有 7 天 · 8月6日", shadowOf(notification).contentText)
        val contentIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, contentIntent.component?.className)
        assertEquals(42L, contentIntent.getLongExtra(MainActivity.EXTRA_IMPORTANT_DAY_ID, 0))
        assertTrue(contentIntent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(contentIntent.flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    @Test
    fun `stale alarm whose offset no longer matches today is ignored`() = runBlocking {
        val delivered = service(onDate = LocalDate.of(2026, 7, 31)).deliver(42, 7)

        assertFalse(delivered)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun `removed reminder selection is ignored`() = runBlocking {
        day = standardDay().copy(reminders = setOf(3))

        assertFalse(service(LocalDate.of(2026, 7, 30)).deliver(42, 7))
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun `non leap year fallback explains one day adjustment`() = runBlocking {
        day = standardDay().copy(month = 2, day = 29, reminders = setOf(14))

        assertTrue(service(LocalDate.of(2027, 2, 14)).deliver(42, 14))
        assertEquals(
            "妈妈生日还有 14 天 · 2月28日 · 本次提前 1 天：当前不是闰年",
            shadowOf(shadowOf(notifications).allNotifications.single()).contentText,
        )
    }

    @Test
    fun `missing record is ignored`() = runBlocking {
        day = null

        assertFalse(service(LocalDate.of(2026, 7, 30)).deliver(42, 7))
    }

    @Test
    fun `zero day copy follows today wording`() {
        assertEquals("妈妈生日就是今天 · 8月6日", reminderCopy("妈妈生日", 0, LocalDate.of(2026, 8, 6), null))
    }

    private fun service(onDate: LocalDate) = ReminderNotificationService(
        context = context,
        loadDay = { day },
        calculator = DateOccurrenceCalculator(SolarOnlyConverter),
        clock = Clock.fixed(onDate.atStartOfDay(Zone).toInstant(), Zone),
    )

    private fun standardDay() = ImportantDay(
        id = 42,
        name = "妈妈生日",
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
        reminders = setOf(14, 7, 3),
    )

    private data object SolarOnlyConverter : CalendarConverter {
        override fun lunarFromSolar(solarDate: LocalDate): LunarDate = error("not used")
        override fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem) =
            DisplayDate(calendarSystem, solarDate.toString())
    }

    private companion object {
        val Zone: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
