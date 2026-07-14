package com.nianri.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nianri.app.MainActivity
import com.nianri.app.NianriApplication
import com.nianri.app.data.WidgetResolution
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.ui.theme.NianriTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        if (!ownsWidget(appWidgetId)) {
            finish()
            return
        }

        val container = (application as NianriApplication).container
        lifecycleScope.launch {
            val resolution = container.widgets.resolve(appWidgetId)
            if (!ownsWidget(appWidgetId)) {
                finish()
                return@launch
            }
            val configured = resolution as? WidgetResolution.Configured
            setContent {
                val days by container.importantDays.observeAll()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                var selectedId by remember(configured?.day?.id) {
                    mutableLongStateOf(configured?.day?.id ?: 0L)
                }
                var saveError by remember { mutableStateOf<String?>(null) }
                NianriTheme {
                    WidgetConfigScreen(
                        days = days,
                        selectedId = selectedId,
                        missingSelection = resolution is WidgetResolution.MissingDay,
                        saveError = saveError,
                        onSelection = {
                            selectedId = it
                            saveError = null
                        },
                        onCreateDay = {
                            startActivity(
                                Intent(this@WidgetConfigActivity, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_NEW_DAY, true),
                            )
                        },
                        onSave = save@{
                            if (!ownsWidget(appWidgetId)) {
                                finish()
                                return@save
                            }
                            val selected = days.firstOrNull { it.id == selectedId }
                                ?: return@save
                            lifecycleScope.launch {
                                val display = if (configured?.day?.id == selectedId) {
                                    configured.display
                                } else {
                                    selected.appDisplay
                                }
                                val result = WidgetConfigurationCommitter(
                                    container.widgets,
                                    container.widgetInstanceUpdater,
                                ).commit(appWidgetId, selectedId, display)
                                val decision = WidgetConfigSaveDecision.from(selectedId, result)
                                selectedId = decision.selectedId
                                saveError = decision.error
                                if (!decision.completed) {
                                    return@launch
                                }
                                setResult(
                                    Activity.RESULT_OK,
                                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                                )
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun ownsWidget(appWidgetId: Int): Boolean =
        ownershipCheckOverride?.invoke(this, appWidgetId)
            ?: WidgetProviderOwnership(this).owns(appWidgetId)

    companion object {
        /** Instrumentation-only seam; production always resolves ownership through AppWidgetManager. */
        internal var ownershipCheckOverride: ((Context, Int) -> Boolean)? = null
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WidgetConfigScreen(
    days: List<ImportantDay>,
    selectedId: Long,
    missingSelection: Boolean,
    saveError: String?,
    onSelection: (Long) -> Unit,
    onCreateDay: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("选择重要日子") }) },
    ) { padding ->
        if (days.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有重要日子")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCreateDay) { Text("新建重要日子") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
            ) {
                if (missingSelection) {
                    Text("原来选择的日子已删除，请重新选择", modifier = Modifier.padding(vertical = 12.dp))
                }
                if (saveError != null) {
                    Text(saveError, modifier = Modifier.padding(vertical = 12.dp))
                }
                Text(
                    "每个小部件独立选择；日期展示切换不改变倒计时基准。",
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(days, key = ImportantDay::id) { day ->
                        val selected = day.id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("widget-day-${day.id}")
                                .semantics { this.selected = selected }
                                .clickable { onSelection(day.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelection(day.id) },
                                modifier = Modifier.semantics { this.selected = selected },
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(day.name)
                                Text(
                                    when (day.basis) {
                                        CalendarSystem.SOLAR -> "按新历倒计时"
                                        CalendarSystem.LUNAR -> "按农历倒计时"
                                    },
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = days.any { it.id == selectedId },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("widget-config-save")
                        .padding(vertical = 16.dp),
                ) {
                    Text("保存小部件")
                }
            }
        }
    }
}
