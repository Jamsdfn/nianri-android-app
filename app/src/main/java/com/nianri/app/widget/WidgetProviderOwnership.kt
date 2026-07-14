package com.nianri.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

class WidgetProviderOwnership(
    private val wideProvider: ComponentName,
    private val squareProvider: ComponentName,
    private val providerForId: (Int) -> ComponentName?,
) {
    constructor(context: Context) : this(
        wideProvider = ComponentName(context, NianriWideWidgetReceiver::class.java),
        squareProvider = ComponentName(context, NianriSquareWidgetReceiver::class.java),
        providerForId = {
            AppWidgetManager.getInstance(context.applicationContext).getAppWidgetInfo(it)?.provider
        },
    )

    fun owns(appWidgetId: Int): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        return providerForId(appWidgetId) in setOf(wideProvider, squareProvider)
    }
}
