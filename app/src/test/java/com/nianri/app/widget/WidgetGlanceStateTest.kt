package com.nianri.app.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.nianri.app.domain.model.CalendarSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGlanceStateTest {
    @Test
    fun `every widget model survives Glance state round trip`() {
        val models = listOf(
            WidgetModel.Unconfigured,
            WidgetModel.MissingDay,
            WidgetModel.DateUnavailable(7, "妈妈生日", "按农历", CalendarSystem.SOLAR),
            WidgetModel.Content(8, "纪念日", 23, "按新历", "农历 六月廿四", CalendarSystem.LUNAR),
        )

        models.forEach { model ->
            val preferences = mutablePreferencesOf()
            WidgetGlanceState.write(preferences, model)

            assertEquals(model, WidgetGlanceState.read(preferences))
        }
    }
}
