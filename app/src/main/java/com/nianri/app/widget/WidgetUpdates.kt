package com.nianri.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.nianri.app.data.WidgetRepository
import com.nianri.app.data.WidgetResolution
import com.nianri.app.domain.WidgetUpdater
import com.nianri.app.domain.model.CalendarSystem
import kotlinx.coroutines.CancellationException

fun interface WidgetInstanceUpdater {
    suspend fun update(appWidgetId: Int)
}

class AndroidWidgetInstanceUpdater(
    context: Context,
    private val providerResolver: (Int) -> ComponentName? = {
        AppWidgetManager.getInstance(context.applicationContext).getAppWidgetInfo(it)?.provider
    },
    private val broadcastDispatcher: (Intent) -> Unit = context.applicationContext::sendBroadcast,
) : WidgetInstanceUpdater {
    private val applicationContext = context.applicationContext

    override suspend fun update(appWidgetId: Int) {
        val update = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        val provider = providerResolver(appWidgetId)
        when {
            provider == null -> update.setPackage(applicationContext.packageName)
            provider.packageName == applicationContext.packageName -> update.component = provider
            else -> return
        }
        broadcastDispatcher(update)
    }
}

sealed interface WidgetConfigurationResult {
    data object Saved : WidgetConfigurationResult
    data object MissingDay : WidgetConfigurationResult
    data object NotOwned : WidgetConfigurationResult
}

data class WidgetConfigSaveDecision(
    val completed: Boolean,
    val selectedId: Long,
    val error: String?,
    val cancelActivity: Boolean,
) {
    companion object {
        fun from(
            selectedId: Long,
            result: WidgetConfigurationResult,
        ): WidgetConfigSaveDecision = when (result) {
            WidgetConfigurationResult.Saved -> WidgetConfigSaveDecision(
                completed = true,
                selectedId = selectedId,
                error = null,
                cancelActivity = false,
            )
            WidgetConfigurationResult.MissingDay -> WidgetConfigSaveDecision(
                completed = false,
                selectedId = 0L,
                error = "这个日子刚刚被删除，请重新选择",
                cancelActivity = false,
            )
            WidgetConfigurationResult.NotOwned -> WidgetConfigSaveDecision(
                completed = false,
                selectedId = 0L,
                error = null,
                cancelActivity = true,
            )
        }
    }
}

class WidgetConfigurationCommitter(
    private val widgets: WidgetRepository,
    private val updater: WidgetInstanceUpdater,
    private val ownsWidget: (Int) -> Boolean = { true },
) {
    suspend fun commit(
        appWidgetId: Int,
        dayId: Long,
        display: CalendarSystem,
    ): WidgetConfigurationResult {
        if (!ownsWidget(appWidgetId)) return WidgetConfigurationResult.NotOwned
        if (!widgets.selectExistingDay(appWidgetId, dayId, display)) {
            return WidgetConfigurationResult.MissingDay
        }
        if (!ownsWidget(appWidgetId)) {
            widgets.remove(appWidgetId)
            return WidgetConfigurationResult.NotOwned
        }
        updater.update(appWidgetId)
        return WidgetConfigurationResult.Saved
    }
}

class ConfiguredWidgetUpdater(
    private val widgets: WidgetRepository,
    private val instanceUpdater: WidgetInstanceUpdater,
) : WidgetUpdater {
    override suspend fun updateAll() {
        updateWidgetInstances(widgets.configuredWidgetIds(), instanceUpdater::update)
    }
}

suspend fun updateWidgetInstances(
    appWidgetIds: List<Int>,
    update: suspend (Int) -> Unit,
) {
    var failure: Exception? = null
    appWidgetIds.forEach { appWidgetId ->
        try {
            update(appWidgetId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
    }
    failure?.let { throw it }
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
