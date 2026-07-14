package com.nianri.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nianri.app.R

private val PrimaryText = ColorProvider(Color.White)
private val SecondaryText = ColorProvider(Color(0xFFCBC6DF))
private val CountdownText = ColorProvider(Color(0xFFC5BBFF))

@Composable
internal fun NianriWidgetSurface(
    context: Context,
    appWidgetId: Int,
    model: WidgetModel,
    wide: Boolean,
) {
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(ImageProvider(R.drawable.widget_night_background))
        .appWidgetBackground()

    when (model) {
        WidgetModel.Unconfigured -> RecoverySurface(
            modifier = modifier,
            title = "选择一个重要日子",
            action = actionStartActivity(WidgetActionIntents.configuration(context, appWidgetId)),
            wide = wide,
        )
        WidgetModel.MissingDay -> RecoverySurface(
            modifier = modifier,
            title = WidgetTextContract.missingRows[0],
            subtitle = WidgetTextContract.missingRows[1],
            action = actionStartActivity(WidgetActionIntents.configuration(context, appWidgetId)),
            wide = wide,
        )
        is WidgetModel.DateUnavailable -> RecoverySurface(
            modifier = modifier,
            title = "日期暂不可用",
            subtitle = "点按编辑这个日子",
            action = actionStartActivity(WidgetActionIntents.edit(context, model.id)),
            wide = wide,
        )
        is WidgetModel.Content -> if (wide) {
            WideContent(context, model, modifier)
        } else {
            SquareContent(context, model, modifier)
        }
    }
}

@Composable
private fun WideContent(context: Context, model: WidgetModel.Content, modifier: GlanceModifier) {
    val text = WidgetTextContract.wide(model)
    val detailAction = actionStartActivity(WidgetActionIntents.detail(context, model.id))
    Column(
        modifier = modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(detailAction),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text.rows[0].leading,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text.rows[0].trailing,
                style = TextStyle(CountdownText, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text.rows[1].leading,
                modifier = GlanceModifier.clickable(detailAction),
                style = TextStyle(SecondaryText, fontSize = 8.5.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text.rows[1].trailing,
                modifier = GlanceModifier.clickable(actionRunCallback<ToggleWidgetCalendarAction>()),
                style = TextStyle(PrimaryText, fontSize = 8.5.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SquareContent(context: Context, model: WidgetModel.Content, modifier: GlanceModifier) {
    val text = WidgetTextContract.square(model)
    val detailAction = actionStartActivity(WidgetActionIntents.detail(context, model.id))
    Column(
        modifier = modifier.padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text.name,
            modifier = GlanceModifier.fillMaxWidth().clickable(detailAction),
            style = TextStyle(PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            text.basis,
            modifier = GlanceModifier.fillMaxWidth().clickable(detailAction),
            style = TextStyle(SecondaryText, fontSize = 9.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text.days,
            modifier = GlanceModifier.fillMaxWidth().clickable(detailAction),
            style = TextStyle(CountdownText, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.dateLabel,
                modifier = GlanceModifier.clickable(detailAction),
                style = TextStyle(SecondaryText, fontSize = 9.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text.dateControl,
                modifier = GlanceModifier.clickable(actionRunCallback<ToggleWidgetCalendarAction>()),
                style = TextStyle(PrimaryText, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecoverySurface(
    modifier: GlanceModifier,
    title: String,
    action: androidx.glance.action.Action,
    wide: Boolean,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .clickable(action)
            .padding(horizontal = if (wide) 7.dp else 10.dp, vertical = if (wide) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            title,
            style = TextStyle(PrimaryText, fontSize = if (wide) 11.sp else 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = TextStyle(CountdownText, fontSize = if (wide) 9.sp else 10.sp),
                maxLines = 1,
            )
        }
    }
}
