package com.nianri.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.nianri.app.data.WidgetRepository
import com.nianri.app.data.WidgetResolution
import com.nianri.app.domain.WidgetUpdater

fun interface WidgetInstanceUpdater {
    suspend fun update(appWidgetId: Int)
}

class AndroidWidgetInstanceUpdater(context: Context) : WidgetInstanceUpdater {
    private val applicationContext = context.applicationContext
    private val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

    override suspend fun update(appWidgetId: Int) {
        val update = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider
        if (provider == null) update.setPackage(applicationContext.packageName) else update.component = provider
        applicationContext.sendBroadcast(update)
    }
}

class ConfiguredWidgetUpdater(
    private val widgets: WidgetRepository,
    private val instanceUpdater: WidgetInstanceUpdater,
) : WidgetUpdater {
    override suspend fun updateAll() {
        widgets.configuredWidgetIds().forEach { instanceUpdater.update(it) }
    }
}

class WidgetToggleController(
    private val widgets: WidgetRepository,
    private val updater: WidgetInstanceUpdater,
) {
    suspend fun toggle(appWidgetId: Int) {
        if (widgets.resolve(appWidgetId) !is WidgetResolution.Configured) return
        widgets.toggleDisplay(appWidgetId) ?: return
        updater.update(appWidgetId)
    }
}
