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
    val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
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
            fontScale = fontScale,
        )
        WidgetModel.MissingDay -> RecoverySurface(
            modifier = modifier,
            title = WidgetTextContract.missingRows[0],
            subtitle = WidgetTextContract.missingRows[1],
            action = actionStartActivity(WidgetActionIntents.configuration(context, appWidgetId)),
            wide = wide,
            fontScale = fontScale,
        )
        is WidgetModel.DateUnavailable -> RecoverySurface(
            modifier = modifier,
            title = "日期暂不可用",
            subtitle = "点按编辑这个日子",
            action = actionStartActivity(WidgetActionIntents.edit(context, model.id)),
            wide = wide,
            fontScale = fontScale,
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
    val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val basisText = when {
        fontScale >= 2f -> ""
        fontScale >= 1.3f -> text.rows[1].leading.removePrefix("按")
        else -> text.rows[1].leading
    }
    Column(
        modifier = modifier
            .clickable(detailAction)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text.rows[0].leading,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(PrimaryText, fontSize = cappedSp(12f, fontScale, 1f), fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text.rows[0].trailing,
                style = TextStyle(CountdownText, fontSize = cappedSp(16f, fontScale, 1f), fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (basisText.isNotEmpty()) {
                Text(
                    basisText,
                    style = TextStyle(SecondaryText, fontSize = cappedSp(8.5f, fontScale, 1f)),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text.rows[1].trailing,
                modifier = GlanceModifier.clickable(actionRunCallback<ToggleWidgetCalendarAction>()),
                style = TextStyle(
                    PrimaryText,
                    fontSize = cappedSp(8.5f, fontScale, 1f),
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SquareContent(context: Context, model: WidgetModel.Content, modifier: GlanceModifier) {
    val text = WidgetTextContract.square(model)
    val detailAction = actionStartActivity(WidgetActionIntents.detail(context, model.id))
    val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    Column(
        modifier = modifier.clickable(detailAction).padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text.name,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(PrimaryText, fontSize = cappedSp(13f, fontScale, 1.15f), fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            text.basis,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(SecondaryText, fontSize = cappedSp(9f, fontScale, 1.15f)),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text.days,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(CountdownText, fontSize = cappedSp(28f, fontScale, 1.15f), fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (fontScale == 1f) {
                Text(
                    text.dateLabel,
                    style = TextStyle(SecondaryText, fontSize = 9.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text.dateControl,
                modifier = GlanceModifier.clickable(actionRunCallback<ToggleWidgetCalendarAction>()),
                style = TextStyle(
                    PrimaryText,
                    fontSize = cappedSp(9f, fontScale, 1.15f),
                    fontWeight = FontWeight.Medium,
                ),
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
    fontScale: Float,
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
            style = TextStyle(
                PrimaryText,
                fontSize = cappedSp(if (wide) 11f else 13f, fontScale, if (wide) 1f else 1.15f),
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = TextStyle(
                    CountdownText,
                    fontSize = cappedSp(if (wide) 9f else 10f, fontScale, if (wide) 1f else 1.15f),
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Launcher cells have fixed physical bounds, so widget text scales only to a safe per-size cap.
 * Core name/countdown/date remain visible; the wide basis helper compacts before core content.
 */
private fun cappedSp(baseSp: Float, systemFontScale: Float, maximumEffectiveScale: Float) =
    (baseSp * minOf(1f, maximumEffectiveScale / systemFontScale)).sp
