package com.nianri.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContainerTest {
    @Test
    fun `deferred reminder binding fails fast with its adapter name`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { DeferredReminderScheduler.replace(42L) }
        }

        assertTrue(failure.message.orEmpty().contains("ReminderScheduler"))
    }

    @Test
    fun `deferred widget binding fails fast with its adapter name`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { DeferredWidgetUpdater.updateAll() }
        }

        assertTrue(failure.message.orEmpty().contains("WidgetUpdater"))
    }
}
