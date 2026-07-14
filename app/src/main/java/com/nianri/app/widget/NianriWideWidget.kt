package com.nianri.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.nianri.app.NianriApplication

class NianriWideWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideNianriWidget(context, id, wide = true)
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val container = (context.applicationContext as NianriApplication).container
        container.widgets.remove(appWidgetId)
    }
}
