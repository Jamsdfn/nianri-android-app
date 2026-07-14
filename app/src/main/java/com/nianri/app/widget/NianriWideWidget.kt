package com.nianri.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import com.nianri.app.CurrentSystemZoneClock
import com.nianri.app.NianriApplication

class NianriWideWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val container = (context.applicationContext as NianriApplication).container
        val model = WidgetPresentationMapper(
            calculator = container.occurrenceCalculator,
            converter = container.calendarConverter,
            clock = CurrentSystemZoneClock(),
        ).map(container.widgets.resolve(appWidgetId))
        provideContent { NianriWidgetSurface(context, appWidgetId, model, wide = true) }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val container = (context.applicationContext as NianriApplication).container
        container.widgets.remove(appWidgetId)
    }
}
