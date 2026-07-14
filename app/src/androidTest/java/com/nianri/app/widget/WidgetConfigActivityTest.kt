package com.nianri.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nianri.app.MainActivity
import com.nianri.app.NianriApplication
import com.nianri.app.data.WidgetResolution
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetConfigActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val application = context as NianriApplication
    private val container get() = application.container

    @Before
    fun clearData() = runBlocking {
        withContext(Dispatchers.IO) { container.database.clearAllTables() }
    }

    @After
    fun clearAfter() = runBlocking {
        withContext(Dispatchers.IO) { container.database.clearAllTables() }
    }

    @Test
    fun backingOutReturnsCanceled() {
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(401))
        compose.onNodeWithText("选择重要日子").assertIsDisplayed()

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertEquals(401, scenario.result.resultData?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun listsEverySavedDayAndSavesInheritedDisplayWithWidgetId() = runBlocking {
        val solarId = container.importantDays.save(day("新历生日", CalendarSystem.SOLAR, CalendarSystem.LUNAR))
        container.importantDays.save(day("农历纪念日", CalendarSystem.LUNAR, CalendarSystem.SOLAR))
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(402))

        waitForText("新历生日")
        compose.onNodeWithText("新历生日").assertIsDisplayed().performClick()
        compose.onNodeWithText("按新历倒计时").assertIsDisplayed()
        compose.onNodeWithText("农历纪念日").assertIsDisplayed()
        compose.onNodeWithText("按农历倒计时").assertIsDisplayed()
        compose.onNodeWithTag("widget-config-save").performClick()

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(402, scenario.result.resultData?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(
            WidgetResolution.Configured(container.importantDays.get(solarId)!!, CalendarSystem.LUNAR),
            container.widgets.resolve(402),
        )
    }

    @Test
    fun reconfigurationReplacesSelectionWithoutDeletingWidgetPreference() = runBlocking {
        val first = container.importantDays.save(day("原日子", CalendarSystem.SOLAR, CalendarSystem.SOLAR))
        val second = container.importantDays.save(day("新日子", CalendarSystem.LUNAR, CalendarSystem.SOLAR))
        container.widgets.select(403, first, CalendarSystem.LUNAR)
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(403))

        waitForText("原日子")
        compose.onNodeWithTag("widget-day-$first").assertIsSelected()
        compose.onNodeWithText("新日子").performClick()
        compose.onNodeWithTag("widget-config-save").performClick()

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(
            WidgetResolution.Configured(container.importantDays.get(second)!!, CalendarSystem.SOLAR),
            container.widgets.resolve(403),
        )
        assertEquals(listOf(403), container.widgets.configuredWidgetIds())
    }

    @Test
    fun reconfiguringTheSameDayKeepsItsIndependentDisplay() = runBlocking {
        val dayId = container.importantDays.save(day("同一个日子", CalendarSystem.SOLAR, CalendarSystem.SOLAR))
        container.widgets.select(408, dayId, CalendarSystem.LUNAR)
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(408))

        waitForText("同一个日子")
        compose.onNodeWithTag("widget-day-$dayId").assertIsSelected()
        compose.onNodeWithTag("widget-config-save").performClick()

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(
            WidgetResolution.Configured(container.importantDays.get(dayId)!!, CalendarSystem.LUNAR),
            container.widgets.resolve(408),
        )
    }

    @Test
    fun missingSelectionCanBeReconfiguredToAnotherSavedDay() = runBlocking {
        val deleted = container.importantDays.save(day("已删除", CalendarSystem.SOLAR, CalendarSystem.SOLAR))
        container.widgets.select(404, deleted, CalendarSystem.LUNAR)
        container.importantDays.delete(deleted)
        val replacement = container.importantDays.save(day("替代日子", CalendarSystem.SOLAR, CalendarSystem.SOLAR))
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(404))

        waitForText("替代日子")
        compose.onNodeWithText("原来选择的日子已删除，请重新选择").assertIsDisplayed()
        compose.onNodeWithText("替代日子").performClick()
        compose.onNodeWithTag("widget-config-save").performClick()

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertTrue(container.widgets.resolve(404) is WidgetResolution.Configured)
        assertEquals(replacement, (container.widgets.resolve(404) as WidgetResolution.Configured).day.id)
    }

    @Test
    fun emptyStateOffersNewDayAndOpensTheAppNewDayPage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        ActivityScenario.launch<WidgetConfigActivity>(intent(405)).use {
            compose.onNodeWithText("还没有重要日子").assertIsDisplayed()
            compose.onNodeWithText("新建重要日子").performClick()
            val launched = instrumentation.waitForMonitorWithTimeout(monitor, 3_000)
            assertTrue(launched?.intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_NEW_DAY, false) == true)
            launched?.finish()
        }
        instrumentation.removeMonitor(monitor)
    }

    @Test
    fun emptyConfigurationRefreshesWhenANewDayIsCreated() = runBlocking {
        ActivityScenario.launch<WidgetConfigActivity>(intent(406)).use {
            compose.onNodeWithText("还没有重要日子").assertIsDisplayed()

            container.importantDays.save(day("刚新建的日子", CalendarSystem.LUNAR, CalendarSystem.SOLAR))

            compose.waitUntil(timeoutMillis = 3_000) {
                compose.onAllNodesWithText("刚新建的日子").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("刚新建的日子").assertIsDisplayed()
        }
        Unit
    }

    @Test
    fun selectionDeletedWhileConfiguringCannotBeSaved() = runBlocking {
        val dayId = container.importantDays.save(day("临时日子", CalendarSystem.SOLAR, CalendarSystem.SOLAR))
        container.importantDays.save(day("仍存在", CalendarSystem.LUNAR, CalendarSystem.LUNAR))
        ActivityScenario.launch<WidgetConfigActivity>(intent(407)).use {
            waitForText("临时日子")
            compose.onNodeWithText("临时日子").performClick()

            container.importantDays.delete(dayId)

            compose.waitUntil(timeoutMillis = 3_000) {
                compose.onAllNodesWithText("临时日子").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("widget-config-save").assertIsNotEnabled()
        }
        Unit
    }

    @Test
    fun invalidWidgetIdFinishesCanceledWithoutSaving() {
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(AppWidgetManager.INVALID_APPWIDGET_ID))

        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertTrue(runBlocking { container.widgets.configuredWidgetIds().isEmpty() })
    }

    private fun intent(id: Int) = Intent(context, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun day(name: String, basis: CalendarSystem, display: CalendarSystem) = ImportantDay(
        name = name,
        basis = basis,
        month = 8,
        day = 6,
        appDisplay = display,
    )
}
