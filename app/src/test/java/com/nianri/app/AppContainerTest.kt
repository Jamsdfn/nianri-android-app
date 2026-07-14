package com.nianri.app

import com.nianri.app.reminder.AndroidReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppContainerTest {
    @Test
    fun `container binds the Android reminder adapter`() {
        val container = AppContainer(RuntimeEnvironment.getApplication())

        assertTrue(container.reminderScheduler is AndroidReminderScheduler)
    }

    @Test
    fun `deferred widget binding fails fast with its adapter name`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { DeferredWidgetUpdater.updateAll() }
        }

        assertTrue(failure.message.orEmpty().contains("WidgetUpdater"))
    }
}
