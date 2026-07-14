package com.nianri.app

import com.nianri.app.reminder.AndroidReminderScheduler
import com.nianri.app.widget.AndroidWidgetInstanceUpdater
import com.nianri.app.widget.ConfiguredWidgetUpdater
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
}
