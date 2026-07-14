package com.nianri.app.widget

import android.appwidget.AppWidgetManager
import com.nianri.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetActionIntentsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `card action opens the matching detail record`() {
        val intent = WidgetActionIntents.detail(context, 71)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(71, intent.getLongExtra(MainActivity.EXTRA_IMPORTANT_DAY_ID, -1))
        assertEquals("nianri://detail/71", intent.dataString)
    }

    @Test
    fun `unavailable action opens matching record directly in edit`() {
        val intent = WidgetActionIntents.edit(context, 72)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(72, intent.getLongExtra(MainActivity.EXTRA_EDIT_IMPORTANT_DAY_ID, -1))
        assertEquals("nianri://edit/72", intent.dataString)
    }

    @Test
    fun `recovery action preserves placement id for reconfiguration`() {
        val intent = WidgetActionIntents.configuration(context, 73)

        assertEquals(WidgetConfigActivity::class.java.name, intent.component?.className)
        assertEquals(73, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals("nianri://widget/configure/73", intent.dataString)
    }
}
