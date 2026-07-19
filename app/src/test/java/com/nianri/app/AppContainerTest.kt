package com.nianri.app

import com.nianri.app.reminder.AndroidReminderScheduler
import com.nianri.app.data.transfer.ConfigurationTransferService
import com.nianri.app.widget.AndroidWidgetInstanceUpdater
import com.nianri.app.widget.ConfiguredWidgetUpdater
import com.nianri.app.widget.MidnightWidgetRefreshScheduler
import org.junit.Assert.assertEquals
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
    fun `container binds the real per instance widget update chain`() {
        val container = AppContainer(RuntimeEnvironment.getApplication())

        assertTrue(container.widgetInstanceUpdater is AndroidWidgetInstanceUpdater)
        assertTrue(container.widgetUpdater is ConfiguredWidgetUpdater)
    }

    @Test
    fun `container binds the midnight widget refresh scheduler`() {
        val container = AppContainer(RuntimeEnvironment.getApplication())

        assertEquals(
            MidnightWidgetRefreshScheduler::class.java,
            container.midnightWidgetRefreshScheduler.javaClass,
        )
    }

    @Test
    fun `container binds the configuration transfer service`() {
        val container = AppContainer(RuntimeEnvironment.getApplication())

        assertEquals(
            ConfigurationTransferService::class.java,
            container.configurationTransferService.javaClass,
        )
    }
}
