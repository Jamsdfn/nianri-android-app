package com.nianri.app.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import com.nianri.app.CurrentSystemZoneClock
import com.nianri.app.NianriApplication
import com.nianri.app.domain.model.CalendarSystem

object WidgetGlanceState {
    private val typeKey = stringPreferencesKey("model_type")
    private val idKey = longPreferencesKey("day_id")
    private val nameKey = stringPreferencesKey("day_name")
    private val daysKey = longPreferencesKey("days_remaining")
    private val basisKey = stringPreferencesKey("basis_label")
    private val dateKey = stringPreferencesKey("displayed_date")
    private val displayKey = stringPreferencesKey("display_calendar")

    fun write(preferences: MutablePreferences, model: WidgetModel) {
        preferences[typeKey] = when (model) {
            WidgetModel.Unconfigured -> TYPE_UNCONFIGURED
            WidgetModel.MissingDay -> TYPE_MISSING
            is WidgetModel.DateUnavailable -> TYPE_UNAVAILABLE
            is WidgetModel.Content -> TYPE_CONTENT
        }
        when (model) {
            WidgetModel.Unconfigured, WidgetModel.MissingDay -> Unit
            is WidgetModel.DateUnavailable -> {
                preferences[idKey] = model.id
                preferences[nameKey] = model.name
                preferences[basisKey] = model.basisLabel
                preferences[displayKey] = model.display.name
            }
            is WidgetModel.Content -> {
                preferences[idKey] = model.id
                preferences[nameKey] = model.name
                preferences[daysKey] = model.days
                preferences[basisKey] = model.basisLabel
                preferences[dateKey] = model.displayedDate
                preferences[displayKey] = model.display.name
            }
        }
    }

    fun read(preferences: Preferences): WidgetModel = when (preferences[typeKey]) {
        TYPE_MISSING -> WidgetModel.MissingDay
        TYPE_UNAVAILABLE -> WidgetModel.DateUnavailable(
            id = preferences[idKey] ?: 0L,
            name = preferences[nameKey].orEmpty(),
            basisLabel = preferences[basisKey].orEmpty(),
            display = preferences.calendar(),
        )
        TYPE_CONTENT -> WidgetModel.Content(
            id = preferences[idKey] ?: 0L,
            name = preferences[nameKey].orEmpty(),
            days = preferences[daysKey] ?: 0L,
            basisLabel = preferences[basisKey].orEmpty(),
            displayedDate = preferences[dateKey].orEmpty(),
            display = preferences.calendar(),
        )
        else -> WidgetModel.Unconfigured
    }

    private fun Preferences.calendar(): CalendarSystem =
        runCatching { CalendarSystem.valueOf(this[displayKey].orEmpty()) }
            .getOrDefault(CalendarSystem.SOLAR)

    private const val TYPE_UNCONFIGURED = "unconfigured"
    private const val TYPE_MISSING = "missing"
    private const val TYPE_UNAVAILABLE = "unavailable"
    private const val TYPE_CONTENT = "content"
}

internal suspend fun GlanceAppWidget.provideNianriWidget(
    context: Context,
    glanceId: GlanceId,
    wide: Boolean,
) {
    syncWidgetGlanceState(context, glanceId)
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    provideContent {
        val model = WidgetGlanceState.read(currentState<Preferences>())
        NianriWidgetSurface(context, appWidgetId, model, wide)
    }
}

internal suspend fun GlanceAppWidget.refreshNianriWidget(
    context: Context,
    glanceId: GlanceId,
) {
    syncWidgetGlanceState(context, glanceId)
    update(context, glanceId)
}

private suspend fun syncWidgetGlanceState(context: Context, glanceId: GlanceId) {
    val applicationContext = context.applicationContext
    val container = (applicationContext as NianriApplication).container
    val appWidgetId = GlanceAppWidgetManager(applicationContext).getAppWidgetId(glanceId)
    val model = WidgetPresentationMapper(
        calculator = container.occurrenceCalculator,
        converter = container.calendarConverter,
        clock = CurrentSystemZoneClock(),
    ).map(container.widgets.resolve(appWidgetId))
    updateAppWidgetState(applicationContext, glanceId) { preferences ->
        WidgetGlanceState.write(preferences, model)
    }
}
