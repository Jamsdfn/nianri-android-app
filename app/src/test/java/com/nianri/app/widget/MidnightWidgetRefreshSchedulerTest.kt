package com.nianri.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
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
class MidnightWidgetRefreshSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun `next refresh is midnight of the next Shanghai date`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T15:59:59Z"),
            ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(
            Instant.parse("2026-07-18T16:00:00Z"),
            nextLocalMidnight(clock),
        )
    }

    @Test
    fun `next refresh supports a non whole hour time zone`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"),
            ZoneId.of("Asia/Kathmandu"),
        )

        assertEquals(
            Instant.parse("2026-07-18T18:15:00Z"),
            nextLocalMidnight(clock),
        )
    }

    @Test
    fun `next refresh follows daylight saving start instead of adding 24 hours`() {
        val clock = Clock.fixed(
            Instant.parse("2026-03-08T05:30:00Z"),
            ZoneId.of("America/New_York"),
        )

        assertEquals(
            Instant.parse("2026-03-09T04:00:00Z"),
            nextLocalMidnight(clock),
        )
    }

    @Test
    fun `scheduler creates one exact idle allowed RTC wakeup for the receiver`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        MidnightWidgetRefreshScheduler(context, clock).scheduleNext()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(Instant.parse("2026-07-18T16:00:00Z").toEpochMilli(), alarm.triggerAtTime)
        assertTrue(alarm.allowWhileIdle)
        assertTrue(shadowOf(alarm.operation).isImmutable)
        assertEquals(MIDNIGHT_WIDGET_REFRESH_ACTION, intent.action)
        assertEquals(MIDNIGHT_WIDGET_REFRESH_RECEIVER_CLASS, intent.component?.className)
    }

    @Test
    fun `repeated scheduling keeps one pending intent identity`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduler = MidnightWidgetRefreshScheduler(context, clock)

        scheduler.scheduleNext()
        scheduler.scheduleNext()

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `missing exact alarm access skips scheduling`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduler = MidnightWidgetRefreshScheduler(
            context,
            Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("Asia/Shanghai")),
        )

        scheduler.scheduleNext()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `permission race is contained without a fallback alarm`() {
        val attempted = mutableListOf<Long>()
        val scheduler = MidnightWidgetRefreshScheduler(
            context = context,
            clock = Clock.fixed(
                Instant.parse("2026-07-18T12:00:00Z"),
                ZoneId.of("Asia/Shanghai"),
            ),
            hasExactAlarmAccess = { true },
            setExactAlarm = { triggerAtMillis, _ ->
                attempted += triggerAtMillis
                throw SecurityException("revoked")
            },
        )

        scheduler.scheduleNext()

        assertEquals(listOf(Instant.parse("2026-07-18T16:00:00Z").toEpochMilli()), attempted)
    }
}
