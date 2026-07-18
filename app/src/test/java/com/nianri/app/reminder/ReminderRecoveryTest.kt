package com.nianri.app.reminder

import android.app.AlarmManager
import android.content.Intent
import java.time.Duration
import androidx.work.ListenableWorker
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderRecoveryTest {
    @Test
    fun `all system changes that invalidate reminder timing request a rebuild`() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        ).forEach { action -> assertTrue(action, isSystemRebuildAction(action)) }
        assertFalse(isSystemRebuildAction(Intent.ACTION_BATTERY_CHANGED))
        assertFalse(isSystemRebuildAction(null))
    }

    @Test
    fun `system date changes refresh reminders widgets and midnight scheduling`() = runBlocking {
        val calls = mutableListOf<String>()

        runSystemChangeRefresh(
            rebuildReminders = { calls += "reminders" },
            updateWidgets = { calls += "widgets" },
            scheduleNextMidnight = { calls += "midnight" },
        )

        assertEquals(listOf("reminders", "widgets", "midnight"), calls)
    }

    @Test
    fun `widget countdown refresh still runs when reminder rebuild fails`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runSystemChangeRefresh(
                    rebuildReminders = {
                        calls += "reminders"
                        error("alarm unavailable")
                    },
                    updateWidgets = { calls += "widgets" },
                    scheduleNextMidnight = { calls += "midnight" },
                )
            }
        }

        assertEquals(listOf("reminders", "widgets", "midnight"), calls)
    }

    @Test
    fun `midnight renewal runs when widget recovery fails`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runSystemChangeRefresh(
                    rebuildReminders = { calls += "reminders" },
                    updateWidgets = {
                        calls += "widgets"
                        error("widget host unavailable")
                    },
                    scheduleNextMidnight = { calls += "midnight" },
                )
            }
        }

        assertEquals(listOf("reminders", "widgets", "midnight"), calls)
    }

    @Test
    fun `audit request runs every 24 hours with 2 hour flex`() {
        val request = reminderAuditRequest()

        assertEquals(Duration.ofHours(24).toMillis(), request.workSpec.intervalDuration)
        assertEquals(Duration.ofHours(2).toMillis(), request.workSpec.flexDuration)
        assertEquals(ReminderAuditWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test
    fun `foreground audit request is immediate one time work`() {
        val request = reminderImmediateAuditRequest()

        assertEquals(0L, request.workSpec.intervalDuration)
        assertEquals(ReminderAuditWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test
    fun `daily audit redundantly refreshes reminder and widget state`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = runDailyAudit(
            rebuildReminders = { calls += "reminders" },
            updateWidgets = { calls += "widgets" },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("reminders", "widgets"), calls)
    }

    @Test
    fun `audit reports retry on failure but preserves coroutine cancellation`() {
        assertEquals(
            ListenableWorker.Result.retry(),
            runBlocking { runReminderAudit { error("database unavailable") } },
        )
        assertThrows(CancellationException::class.java) {
            runBlocking { runReminderAudit { throw CancellationException("stopped") } }
        }
    }
}
