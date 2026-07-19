package com.nianri.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import com.nianri.app.MainActivity
import com.nianri.app.data.UiPreferences
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.calendar.CalendarConversionException
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.IcuCalendarConverter
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.LunarDate
import com.nianri.app.domain.model.Occurrence
import com.nianri.app.ui.home.HomeScreen
import com.nianri.app.ui.home.HomeUiState
import com.nianri.app.ui.home.HomeViewModel
import com.nianri.app.ui.transfer.TransferTab
import com.nianri.app.ui.transfer.TransferMessage
import com.nianri.app.ui.transfer.TransferMessageKind
import com.nianri.app.ui.transfer.TransferUiState
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun pinnedCardAppearsFirstAndTodayUsesFriendlyCopy() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    pinned = readyDay(id = 1, name = "妈妈生日", days = 0, pinned = true),
                    upcoming = listOf(readyDay(id = 2, name = "纪念日", days = 8)),
                    showCalendarExplanation = false,
                ),
            )
        }

        val pinnedTop = composeRule.onAllNodesWithText("妈妈生日")[0]
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot().top
        val upcomingTop = composeRule.onNodeWithText("纪念日")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot().top
        assertTrue(pinnedTop < upcomingTop)
        composeRule.onNodeWithText("就是今天").assertIsDisplayed()
        composeRule.onNodeWithText("接下来的日子").assertIsDisplayed()
    }

    @Test
    fun cardKeepsCountdownBasisAndDisplayLabelsVisible() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    pinned = readyDay(
                        id = 1,
                        name = "妈妈生日",
                        days = 23,
                        pinned = true,
                        basis = CalendarSystem.LUNAR,
                    ),
                    showCalendarExplanation = false,
                ),
            )
        }

        composeRule.onNodeWithText("按农历倒计时").assertIsDisplayed()
        composeRule.onNodeWithText("日期展示").assertIsDisplayed()
    }

    @Test
    fun switchingDisplayInvokesOnlyToggleCallback() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    pinned = readyDay(id = 42, name = "妈妈生日", days = 23, pinned = true),
                    showCalendarExplanation = false,
                ),
                onAdd = { events += "add" },
                onOpen = { events += "open:$it" },
                onToggleDisplay = { events += "toggle:$it" },
            )
        }

        composeRule.onNodeWithContentDescription("妈妈生日切换为农历展示").performClick()

        composeRule.runOnIdle { assertEquals(listOf("toggle:42"), events) }
    }

    @Test
    fun displayOptionsAreRadioButtonsWithAccessibleTouchBounds() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    pinned = readyDay(id = 43, name = "妈妈生日", days = 23, pinned = true),
                    showCalendarExplanation = false,
                ),
            )
        }

        val solar = composeRule.onNodeWithContentDescription("妈妈生日切换为新历展示")
            .assertIsSelectable()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        val lunar = composeRule.onNodeWithContentDescription("妈妈生日切换为农历展示")
            .assertIsSelectable()
            .assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

        val solarBounds = solar.getUnclippedBoundsInRoot()
        val lunarBounds = lunar.getUnclippedBoundsInRoot()
        assertTrue(solarBounds.bottom - solarBounds.top >= 48.dp)
        assertTrue(lunarBounds.bottom - lunarBounds.top >= 48.dp)
    }

    @Test
    fun loadingStateShowsProgressWithoutEmptyOrCreateUi() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = true,
                    showCalendarExplanation = true,
                ),
            )
        }

        composeRule.onNodeWithTag("home-title").assertIsDisplayed()
        composeRule.onNodeWithTag("home-loading-indicator").assertIsDisplayed()
        composeRule.onNodeWithText("正在加载日子…").assertIsDisplayed()
        composeRule.onNodeWithTag("home-add").assertDoesNotExist()
        composeRule.onNodeWithText("新建重要日子").assertDoesNotExist()
        composeRule.onNodeWithText("切换只改变日期怎么显示").assertDoesNotExist()
    }

    @Test
    fun emptyStateOffersCreateAction() {
        var addCount = 0
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    showCalendarExplanation = false,
                ),
                onAdd = { addCount++ },
            )
        }

        composeRule.onNodeWithText("新建重要日子").performClick()
        composeRule.runOnIdle { assertEquals(1, addCount) }
    }

    @Test
    fun unavailableCardOffersEditAction() {
        var opened: Long? = null
        val day = ImportantDay(
            id = 17,
            name = "旧历生日",
            basis = CalendarSystem.LUNAR,
            month = 8,
            day = 30,
            appDisplay = CalendarSystem.SOLAR,
        )
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    upcoming = listOf(DayCardModel.Unavailable(day)),
                    showCalendarExplanation = false,
                ),
                onOpen = { opened = it },
            )
        }

        composeRule.onNodeWithText("日期暂不可用").assertIsDisplayed()
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.runOnIdle { assertEquals(17L, opened) }
    }

    @Test
    fun unavailableCardDisplayToggleIsIndependentFromOpenAction() {
        val events = mutableListOf<String>()
        val day = ImportantDay(
            id = 18,
            name = "旧历生日",
            basis = CalendarSystem.LUNAR,
            month = 8,
            day = 30,
            appDisplay = CalendarSystem.SOLAR,
        )
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    upcoming = listOf(DayCardModel.Unavailable(day)),
                    showCalendarExplanation = false,
                ),
                onOpen = { events += "open:$it" },
                onToggleDisplay = { events += "toggle:$it" },
            )
        }

        composeRule.onNodeWithContentDescription("旧历生日切换为农历展示").performClick()

        composeRule.runOnIdle { assertEquals(listOf("toggle:18"), events) }
    }

    @Test
    fun explanationDismissalSurvivesViewModelRecreation() {
        val preferences = UiPreferences(context)
        val first = HomeViewModel(
            cards = flowOf(emptyList()),
            updateAppDisplay = { _, _ -> },
            converter = IcuCalendarConverter(),
            uiPreferences = preferences,
        )

        assertEquals(true, first.uiState.value.showCalendarExplanation)
        first.dismissCalendarExplanation()

        val recreated = HomeViewModel(
            cards = flowOf(emptyList()),
            updateAppDisplay = { _, _ -> },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )
        assertFalse(recreated.uiState.value.showCalendarExplanation)
    }

    @Test
    fun viewModelReprojectsDisplayWithoutChangingCountdown() {
        val original = readyDay(id = 8, name = "妈妈生日", days = 23, pinned = true)
        val updates = mutableListOf<Pair<Long, CalendarSystem>>()
        val viewModel = HomeViewModel(
            cards = MutableStateFlow(listOf(original)),
            updateAppDisplay = { id, display -> updates += id to display },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            HomeScreen(state = state, onToggleDisplay = viewModel::toggleDisplay)
        }
        composeRule.waitUntil { viewModel.uiState.value.pinned != null }

        composeRule.onNodeWithContentDescription("妈妈生日切换为农历展示").performClick()
        composeRule.waitForIdle()

        val updated = viewModel.uiState.value.pinned as DayCardModel.Ready
        assertEquals(CalendarSystem.LUNAR, updated.day.appDisplay)
        assertEquals(original.occurrence, updated.occurrence)
        assertEquals(original.occurrence.daysRemaining, updated.occurrence.daysRemaining)
        assertEquals(listOf(8L to CalendarSystem.LUNAR), updates)
    }

    @Test
    fun failedDisplayUpdateRollsBackAndShowsRetryMessage() {
        val original = readyDay(id = 9, name = "爸爸生日", days = 12, pinned = true)
        val cards = MutableStateFlow(listOf(original))
        val viewModel = HomeViewModel(
            cards = cards,
            updateAppDisplay = { _, _ -> error("database unavailable") },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            HomeScreen(state = state, onToggleDisplay = viewModel::toggleDisplay)
        }
        composeRule.waitUntil { viewModel.uiState.value.pinned != null }

        composeRule.onNodeWithContentDescription("爸爸生日切换为农历展示").performClick()

        composeRule.waitUntil {
            viewModel.uiState.value.pinned?.day?.appDisplay == CalendarSystem.SOLAR
        }
        composeRule.onNodeWithText("切换失败，请重试").assertIsDisplayed()
    }

    @Test
    fun acknowledgedOverrideDoesNotMaskLaterRepositoryChange() {
        val original = readyDay(id = 10, name = "纪念日", days = 30, pinned = true)
        val cards = MutableStateFlow(listOf(original))
        val viewModel = HomeViewModel(
            cards = cards,
            updateAppDisplay = { _, _ -> },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            HomeScreen(state = state, onToggleDisplay = viewModel::toggleDisplay)
        }
        composeRule.waitUntil { viewModel.uiState.value.pinned != null }

        composeRule.onNodeWithContentDescription("纪念日切换为农历展示").performClick()
        composeRule.waitUntil {
            viewModel.uiState.value.pinned?.day?.appDisplay == CalendarSystem.LUNAR
        }
        cards.value = listOf(original.withDisplay(CalendarSystem.LUNAR, "农历六月廿四"))
        composeRule.waitForIdle()
        cards.value = listOf(original.withDisplay(CalendarSystem.SOLAR, "仓库更新后的新历日期"))

        composeRule.waitUntil {
            viewModel.uiState.value.pinned?.day?.appDisplay == CalendarSystem.SOLAR
        }
        composeRule.onNodeWithText("仓库更新后的新历日期").assertIsDisplayed()
    }

    @Test
    fun converterFailureMakesCardUnavailableAndStateContinuesWithLaterSource() {
        val original = readyDay(id = 11, name = "转换边界日", days = 40, pinned = true)
        val cards = MutableStateFlow(listOf(original))
        val converter = ControllableConverter()
        val viewModel = HomeViewModel(
            cards = cards,
            updateAppDisplay = { _, _ -> },
            converter = converter,
            uiPreferences = UiPreferences(context),
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            HomeScreen(state = state, onToggleDisplay = viewModel::toggleDisplay)
        }
        composeRule.waitUntil { viewModel.uiState.value.pinned != null }

        composeRule.onNodeWithContentDescription("转换边界日切换为农历展示").performClick()

        composeRule.waitUntil {
            viewModel.uiState.value.pinned is DayCardModel.Unavailable
        }
        val unavailable = viewModel.uiState.value.pinned as DayCardModel.Unavailable
        assertEquals(CalendarSystem.LUNAR, unavailable.day.appDisplay)

        converter.failLunarDisplay = false
        cards.value = listOf(original.withDisplay(CalendarSystem.LUNAR, "仓库恢复后的农历日期"))

        composeRule.waitUntil {
            (viewModel.uiState.value.pinned as? DayCardModel.Ready)
                ?.displayedDate?.text == "仓库恢复后的农历日期"
        }
    }

    @Test
    fun unavailableCardOptimisticallySelectsDisplayAndRollsBackOnFailure() {
        val releaseFailure = CompletableDeferred<Unit>()
        val day = ImportantDay(
            id = 19,
            name = "暂不可用生日",
            basis = CalendarSystem.LUNAR,
            month = 8,
            day = 30,
            appDisplay = CalendarSystem.SOLAR,
        )
        val viewModel = HomeViewModel(
            cards = MutableStateFlow(listOf(DayCardModel.Unavailable(day))),
            updateAppDisplay = { _, _ ->
                releaseFailure.await()
                error("database unavailable")
            },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            HomeScreen(state = state, onToggleDisplay = viewModel::toggleDisplay)
        }
        composeRule.waitUntil { viewModel.uiState.value.upcoming.isNotEmpty() }

        composeRule.onNodeWithContentDescription("暂不可用生日切换为农历展示").performClick()

        composeRule.waitUntil {
            viewModel.uiState.value.upcoming.single().day.appDisplay == CalendarSystem.LUNAR
        }
        composeRule.onNodeWithContentDescription("暂不可用生日切换为农历展示")
            .assertIsSelected()

        releaseFailure.complete(Unit)
        composeRule.waitUntil {
            viewModel.uiState.value.upcoming.single().day.appDisplay == CalendarSystem.SOLAR
        }
        composeRule.onNodeWithContentDescription("暂不可用生日切换为新历展示")
            .assertIsSelected()
        composeRule.onNodeWithText("切换失败，请重试").assertIsDisplayed()
    }

    @Test
    fun appShellUsesLightSystemBarIcons() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView,
                    ).isAppearanceLightStatusBars,
                )
            }
        }
    }

    @Test
    fun mainActivityWithoutExtraStartsAtHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("念日").assertIsDisplayed()
        }
    }

    @Test
    fun homeAddActionNavigatesToEdit() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag("home-add").performClick()
            composeRule.onNodeWithText("新建日子").assertIsDisplayed()
        }
    }

    @Test
    fun importantDayExtraStartsAtDetail() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("importantDayId", 88L)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithText("重要日子").assertIsDisplayed()
        }
    }

    @Test
    fun importantDayExtraBackReturnsHome() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("importantDayId", 88L)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeRule.onNodeWithText("返回").performClick()
            composeRule.onNodeWithText("念日").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(timeoutMillis = 3_000) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun homeContentRespectsSafeDrawingInsetsWithoutDuplicateFab() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    showCalendarExplanation = false,
                ),
                safeDrawingInsets = WindowInsets(
                    left = 0.dp,
                    top = 40.dp,
                    right = 0.dp,
                    bottom = 60.dp,
                ),
            )
        }

        val root = composeRule.onNodeWithTag("home-root").getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithTag("home-title").getUnclippedBoundsInRoot()
        assertTrue(title.top >= root.top + 40.dp)
        composeRule.onNodeWithTag("home-add").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("home-fab").fetchSemanticsNodes().size)
    }

    @Test
    fun migrationEntryAppearsImmediatelyLeftOfAdd() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(dayCount = 2),
            )
        }

        val migration = composeRule.onNodeWithTag("home-transfer")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val add = composeRule.onNodeWithTag("home-add")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue(migration.right <= add.left)
    }

    @Test
    fun migrationButtonOpensSheetWithExportSelectedByDefault() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(dayCount = 2),
            )
        }

        composeRule.onNodeWithText("迁移").performClick()

        composeRule.onNodeWithText("保存配置到本机").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer-tab-export").assertIsSelected()
    }

    @Test
    fun exportTabShowsFileAndClipboardActionsWithPrivacyCopy() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(dayCount = 2),
            )
        }

        composeRule.onNodeWithText("迁移").performClick()

        composeRule.onNodeWithText("保存配置到本机").assertIsDisplayed()
        composeRule.onNodeWithText("复制配置到剪贴板").assertIsDisplayed()
        composeRule.onNodeWithText("剪贴板配置包含纪念日名称，请注意隐私。").assertIsDisplayed()
    }

    @Test
    fun importTabEditsPastesAndImportsText() {
        var text by mutableStateOf("")
        val events = mutableListOf<String>()
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(
                    selectedTab = TransferTab.IMPORT,
                    importText = text,
                ),
                onImportTextChange = { text = it },
                onPasteFromClipboard = { events += "paste" },
                onImportPastedText = { events += "import" },
            )
        }

        composeRule.onNodeWithText("迁移").performClick()
        composeRule.onNodeWithText("选择配置文件").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer-import-text").performTextInput("{json}")
        composeRule.onNodeWithText("从剪贴板粘贴").performClick()
        composeRule.onNodeWithText("导入粘贴内容").performClick()

        composeRule.runOnIdle {
            assertEquals("{json}", text)
            assertEquals(listOf("paste", "import"), events)
        }
    }

    @Test
    fun emptyTextDisablesPastedImport() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(
                    selectedTab = TransferTab.IMPORT,
                    importText = "",
                ),
            )
        }

        composeRule.onNodeWithText("迁移").performClick()

        composeRule.onNodeWithText("导入粘贴内容").assertIsNotEnabled()
    }

    @Test
    fun importTabInvokesTheImportPickerCallback() {
        var imports = 0
        var selectedTab by mutableStateOf(TransferTab.EXPORT)
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(selectedTab = selectedTab, dayCount = 2),
                onSelectTransferTab = { selectedTab = it },
                onRequestImport = { imports++ },
            )
        }

        composeRule.onNodeWithText("迁移").performClick()
        composeRule.onNodeWithTag("transfer-tab-import").performClick()
        composeRule.onNodeWithText("选择配置文件").performClick()

        composeRule.runOnIdle { assertEquals(1, imports) }
    }

    @Test
    fun emptyExportIsDisabled() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(dayCount = 0),
            )
        }

        composeRule.onNodeWithText("迁移").performClick()

        composeRule.onNodeWithText("暂无可导出的纪念日").assertIsDisplayed()
        composeRule.onNodeWithText("保存配置到本机").assertIsNotEnabled()
        composeRule.onNodeWithText("复制配置到剪贴板").assertIsNotEnabled()
    }

    @Test
    fun processingImportIsDisabled() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(
                    selectedTab = TransferTab.IMPORT,
                    dayCount = 1,
                    isProcessing = true,
                ),
            )
        }

        composeRule.onNodeWithText("迁移").performClick()

        composeRule.onNodeWithText("正在处理…").assertIsDisplayed()
        composeRule.onNodeWithText("选择配置文件").assertIsNotEnabled()
        composeRule.onNodeWithTag("transfer-import-text").assertIsNotEnabled()
        composeRule.onNodeWithText("从剪贴板粘贴").assertIsNotEnabled()
        composeRule.onNodeWithText("导入粘贴内容").assertIsNotEnabled()
    }

    @Test
    fun transferResultIsDismissedExplicitly() {
        var dismissals = 0
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(isLoading = false, showCalendarExplanation = false),
                transferState = TransferUiState(
                    dayCount = 1,
                    message = TransferMessage(
                        kind = TransferMessageKind.SUCCESS,
                        text = "已导出 1 个纪念日",
                    ),
                ),
                onTransferMessageShown = { dismissals++ },
            )
        }

        composeRule.onNodeWithText("迁移").performClick()
        composeRule.onNodeWithText("已导出 1 个纪念日").assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()

        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    private fun readyDay(
        id: Long,
        name: String,
        days: Long,
        pinned: Boolean = false,
        basis: CalendarSystem = CalendarSystem.SOLAR,
    ) = DayCardModel.Ready(
        day = ImportantDay(
            id = id,
            name = name,
            basis = basis,
            month = 8,
            day = 6,
            appDisplay = CalendarSystem.SOLAR,
            isPinned = pinned,
        ),
        occurrence = Occurrence(LocalDate.of(2026, 8, 6), days),
        displayedDate = DisplayDate(CalendarSystem.SOLAR, "2026年8月6日 · 星期四"),
    )

    private fun DayCardModel.Ready.withDisplay(
        display: CalendarSystem,
        text: String,
    ) = copy(
        day = day.copy(appDisplay = display),
        displayedDate = DisplayDate(display, text),
    )

    private class ControllableConverter : CalendarConverter {
        private val delegate = IcuCalendarConverter()
        var failLunarDisplay = true

        override fun lunarFromSolar(solarDate: LocalDate): LunarDate =
            delegate.lunarFromSolar(solarDate)

        override fun displayDate(
            solarDate: LocalDate,
            calendarSystem: CalendarSystem,
        ): DisplayDate {
            if (failLunarDisplay && calendarSystem == CalendarSystem.LUNAR) {
                throw CalendarConversionException("conversion unavailable")
            }
            return delegate.displayDate(solarDate, calendarSystem)
        }
    }
}
