package com.nianri.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nianri.app.MainActivity

object WidgetActionIntents {
    fun detail(context: Context, dayId: Long): Intent = Intent(context, MainActivity::class.java)
        .setData(Uri.parse("nianri://detail/$dayId"))
        .putExtra(MainActivity.EXTRA_IMPORTANT_DAY_ID, dayId)

    fun edit(context: Context, dayId: Long): Intent = Intent(context, MainActivity::class.java)
        .setData(Uri.parse("nianri://edit/$dayId"))
        .putExtra(MainActivity.EXTRA_EDIT_IMPORTANT_DAY_ID, dayId)

    fun configuration(context: Context, appWidgetId: Int): Intent =
        Intent(context, WidgetConfigActivity::class.java)
            .setData(Uri.parse("nianri://widget/configure/$appWidgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
