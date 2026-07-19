# Configuration Transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add versioned full-configuration export and atomic merge import so users can move all important days between Android devices.

**Architecture:** A pure Kotlin codec owns the versioned JSON contract, an import planner resolves names and pin state, and the repository performs one transactional batch insert. A transfer service coordinates persistence with reminder/widget refresh; a dedicated ViewModel and Compose bottom sheet expose the workflow while `NianriNavHost` owns Android Storage Access Framework launchers and URI I/O.

**Tech Stack:** Kotlin 2.3.10, Android API 26+, Jetpack Compose Material 3, Room 2.8.4, kotlinx-serialization JSON 1.8.1 DOM API, Android Storage Access Framework, JUnit 4, Robolectric, AndroidX Compose UI Test, Espresso Intents.

## Global Constraints

- Export all important-day business fields: name, countdown calendar, month/day, app display calendar, optional reminders, reminder time, and pin state.
- Never export database IDs/timestamps, widget bindings, permissions, or device state.
- Use UTF-8 JSON with `format = "nianri-configuration"` and `version = 1`; reject unsupported versions before any write.
- Import merges with B-device data and never deletes or edits existing rows.
- Exact trimmed-name conflicts become `原名称-yyyyMMdd导入`, then `-2`, `-3`, and so on; capture the B-device local date once per import.
- Preserve B's pin when it exists; inherit A's pin only when B has no pinned row.
- Validate and plan the entire document before a single Room transaction; no partial imports.
- Rebuild reminders and refresh widgets only after commit. A refresh failure does not roll back committed data and must surface as a warning.
- Use Storage Access Framework without adding storage permissions.
- Home exposes a `迁移` text button immediately left of `+`; the sheet defaults to the `导出` Tab and switches between `导出` and `导入`.

---

## File Structure

- `app/src/main/java/com/nianri/app/data/transfer/TransferDocument.kt`: version-1 transfer model and typed transfer failures.
- `app/src/main/java/com/nianri/app/data/transfer/TransferCodec.kt`: deterministic JSON encode/decode and structural validation.
- `app/src/main/java/com/nianri/app/data/transfer/ImportPlanner.kt`: pure name and pin conflict resolution.
- `app/src/main/java/com/nianri/app/data/transfer/ConfigurationTransferService.kt`: export/import orchestration and post-commit refresh.
- `app/src/main/java/com/nianri/app/domain/model/ImportantDayValidation.kt`: shared business validation used by saves and imports.
- `app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt`: async workflow state and error-to-copy mapping.
- `app/src/main/java/com/nianri/app/ui/transfer/TransferSheet.kt`: migration bottom sheet and Tab UI.
- Existing DAO/repository/container/navigation/home files receive only the minimal integration methods and wiring.

---

### Task 1: Versioned Transfer Codec and Shared Validation

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/nianri/app/domain/model/ImportantDayValidation.kt`
- Modify: `app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt`
- Create: `app/src/main/java/com/nianri/app/data/transfer/TransferDocument.kt`
- Create: `app/src/main/java/com/nianri/app/data/transfer/TransferCodec.kt`
- Create: `app/src/test/java/com/nianri/app/data/transfer/TransferCodecTest.kt`

**Interfaces:**
- Produces: `TransferDocument(exportedAt: Instant, days: List<ImportantDay>)`.
- Produces: `TransferCodec.encode(TransferDocument): String` and `TransferCodec.decode(String): TransferDocument`.
- Produces: `requireValidImportantDay(ImportantDay): Unit`.
- Produces: `TransferFormatException.NotNianriConfiguration`, `.Corrupt`, `.UnsupportedVersion`, and `.InvalidDay` for ViewModel mapping in Task 4.

- [ ] **Step 1: Write failing codec round-trip and validation tests**

```kotlin
class TransferCodecTest {
    private val codec = TransferCodec()

    @Test fun `version one round trip preserves every business field`() {
        val source = TransferDocument(
            exportedAt = Instant.parse("2026-07-19T09:30:00Z"),
            days = listOf(
                ImportantDay(
                    name = "结婚纪念日",
                    basis = CalendarSystem.LUNAR,
                    month = 8,
                    day = 15,
                    appDisplay = CalendarSystem.SOLAR,
                    reminders = setOf(14, 3),
                    reminderTimeMinutes = 20 * 60 + 5,
                    isPinned = true,
                ),
            ),
        )

        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)

        assertEquals(source, decoded)
        assertTrue(encoded.contains("\"format\":\"nianri-configuration\""))
        assertFalse(encoded.contains("\"id\""))
    }

    @Test fun `unknown version is rejected`() {
        assertThrows(TransferFormatException.UnsupportedVersion::class.java) {
            codec.decode("""{"format":"nianri-configuration","version":2,"exportedAt":"2026-07-19T09:30:00Z","days":[]}""")
        }
    }

    @Test fun `two pinned days are rejected`() {
        val json = validJson(
            dayJson("A", pinned = true),
            dayJson("B", pinned = true),
        )
        assertThrows(TransferFormatException.InvalidDay::class.java) { codec.decode(json) }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.data.transfer.TransferCodecTest`

Expected: compilation fails because `TransferCodec` and `TransferDocument` do not exist.

- [ ] **Step 3: Add JSON dependency and implement the model, shared validator, and codec**

Use the JSON DOM API without compiler-generated serializers. Add this dependency under the existing serialization BOM:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
```

Use these exact public types:

```kotlin
data class TransferDocument(
    val exportedAt: Instant,
    val days: List<ImportantDay>,
)

sealed class TransferFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause) {
    class NotNianriConfiguration : TransferFormatException("Not a Nianri configuration")
    class Corrupt(cause: Throwable? = null) : TransferFormatException("Corrupt configuration", cause)
    class UnsupportedVersion(val version: Int) : TransferFormatException("Unsupported version: $version")
    class InvalidDay(message: String, cause: Throwable? = null) : TransferFormatException(message, cause)
}

fun requireValidImportantDay(day: ImportantDay) {
    require(day.name.trim().isNotEmpty()) { "Name must not be blank" }
    require(day.month in 1..12) { "Month must be between 1 and 12" }
    val maxDay = if (day.basis == CalendarSystem.SOLAR) Month.of(day.month).maxLength() else 30
    require(day.day in 1..maxDay) { "Day is invalid for the selected month" }
    require(day.reminders.all { it in setOf(14, 7, 3) }) { "Unsupported reminder offset" }
    require(day.reminderTimeMinutes in 0 until 24 * 60) { "Invalid reminder time" }
}
```

`TransferCodec.encode` must build a `JsonObject` with the exact fields from the spec and sort `reminders` descending for stable output. `decode` must:

1. call `Json.parseToJsonElement(text).jsonObject`;
2. check `format` before `version`;
3. parse `exportedAt` with `Instant.parse`;
4. require each of the eight day fields and convert calendar values with `CalendarSystem.valueOf`;
5. trim names and set imported IDs to `0`;
6. call `requireValidImportantDay` for every row;
7. reject more than one pinned row;
8. translate JSON/type/time failures into the typed exceptions above without swallowing an existing `TransferFormatException`.

Replace `ImportantDayRepository.validate` with `requireValidImportantDay(day)` so normal saves and transfer decoding share the same calendar/reminder rules.

- [ ] **Step 4: Run codec tests and repository regression tests**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.data.transfer.TransferCodecTest --tests com.nianri.app.domain.DayMutationCoordinatorTest`

Expected: all selected tests pass with zero failures.

- [ ] **Step 5: Commit Task 1**

```bash
git add app/build.gradle.kts app/src/main/java/com/nianri/app/domain/model/ImportantDayValidation.kt app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt app/src/main/java/com/nianri/app/data/transfer app/src/test/java/com/nianri/app/data/transfer/TransferCodecTest.kt
git commit -m "feat: add versioned configuration codec"
```

---

### Task 2: Conflict Planner and Atomic Repository Insert

**Files:**
- Create: `app/src/main/java/com/nianri/app/data/transfer/ImportPlanner.kt`
- Modify: `app/src/main/java/com/nianri/app/data/local/ImportantDayDao.kt`
- Modify: `app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt`
- Create: `app/src/test/java/com/nianri/app/data/transfer/ImportPlannerTest.kt`
- Create: `app/src/test/java/com/nianri/app/data/ImportantDayRepositoryImportTest.kt`

**Interfaces:**
- Consumes: validated `ImportantDay` instances with `id = 0` from Task 1.
- Produces: `ImportPlan(days: List<ImportantDay>, renamedCount: Int)`.
- Produces: `ImportPlanner.plan(existing, incoming, importDate): ImportPlan`.
- Produces: `ImportantDayRepository.getAll(): List<ImportantDay>` and `importAll(List<ImportantDay>): List<Long>`.

- [ ] **Step 1: Write failing planner tests for names and pin priority**

```kotlin
@Test fun `existing name receives dated suffix and numeric fallback`() {
    val existing = listOf(day("纪念日"), day("纪念日-20260719导入"))
    val incoming = listOf(day("纪念日"), day("纪念日"))

    val plan = ImportPlanner.plan(existing, incoming, LocalDate.of(2026, 7, 19))

    assertEquals(
        listOf("纪念日-20260719导入-2", "纪念日-20260719导入-3"),
        plan.days.map(ImportantDay::name),
    )
    assertEquals(2, plan.renamedCount)
}

@Test fun `B pin wins but A pin is inherited when B has none`() {
    val aPinned = day("A", pinned = true)
    assertFalse(ImportPlanner.plan(listOf(day("B", pinned = true)), listOf(aPinned), date).days.single().isPinned)
    assertTrue(ImportPlanner.plan(listOf(day("B")), listOf(aPinned), date).days.single().isPinned)
}
```

- [ ] **Step 2: Run planner tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.data.transfer.ImportPlannerTest`

Expected: compilation fails because `ImportPlanner` does not exist.

- [ ] **Step 3: Implement the pure planner**

```kotlin
data class ImportPlan(val days: List<ImportantDay>, val renamedCount: Int)

object ImportPlanner {
    fun plan(existing: List<ImportantDay>, incoming: List<ImportantDay>, importDate: LocalDate): ImportPlan {
        val occupied = existing.mapTo(linkedSetOf()) { it.name.trim() }
        val keepIncomingPin = existing.none(ImportantDay::isPinned)
        var renamed = 0
        val planned = incoming.map { source ->
            val base = source.name.trim()
            val finalName = if (occupied.add(base)) base else {
                renamed += 1
                uniqueImportedName(base, importDate, occupied)
            }
            source.copy(id = 0, name = finalName, isPinned = keepIncomingPin && source.isPinned)
        }
        return ImportPlan(planned, renamed)
    }

    private fun uniqueImportedName(base: String, date: LocalDate, occupied: MutableSet<String>): String {
        val stem = "$base-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}导入"
        if (occupied.add(stem)) return stem
        var sequence = 2
        while (!occupied.add("$stem-$sequence")) sequence += 1
        return "$stem-$sequence"
    }
}
```

- [ ] **Step 4: Write failing repository tests for all-or-nothing batch insertion**

```kotlin
@Test fun `batch import assigns new ids and preserves all fields`() = runBlocking {
    val ids = repository.importAll(listOf(day("A"), day("B", pinned = true)))
    assertEquals(2, ids.size)
    assertTrue(ids.all { it > 0 })
    assertEquals(listOf("A", "B"), repository.getAll().map(ImportantDay::name))
}

@Test fun `invalid row prevents the entire batch`() = runBlocking {
    assertThrows(IllegalArgumentException::class.java) {
        runBlocking { repository.importAll(listOf(day("valid"), day(" "))) }
    }
    assertTrue(repository.getAll().isEmpty())
}
```

- [ ] **Step 5: Run repository tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.data.ImportantDayRepositoryImportTest`

Expected: compilation fails because `getAll` and `importAll` do not exist.

- [ ] **Step 6: Add DAO and repository batch APIs**

```kotlin
@Query("SELECT * FROM important_days")
suspend fun getAll(): List<ImportantDayEntity>

@Insert
suspend fun insertAll(entities: List<ImportantDayEntity>): List<Long>
```

Repository behavior:

```kotlin
suspend fun getAll(): List<ImportantDay> = dao.getAll().map { it.toDomain() }

suspend fun importAll(days: List<ImportantDay>): List<Long> {
    days.forEach(::requireValidImportantDay)
    require(days.count(ImportantDay::isPinned) <= 1) { "Only one day can be pinned" }
    val timestamp = now()
    return database.withTransaction {
        dao.insertAll(days.map { it.copy(id = 0).toEntity(timestamp, timestamp) })
    }
}
```

Do not call `save` in a loop: its per-row transaction and `clearPinned` behavior would violate the atomic import and B-pin rules.

- [ ] **Step 7: Run planner and repository tests**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.data.transfer.ImportPlannerTest --tests com.nianri.app.data.ImportantDayRepositoryImportTest`

Expected: all selected tests pass with zero failures.

- [ ] **Step 8: Commit Task 2**

```bash
git add app/src/main/java/com/nianri/app/data/transfer/ImportPlanner.kt app/src/main/java/com/nianri/app/data/local/ImportantDayDao.kt app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt app/src/test/java/com/nianri/app/data/transfer/ImportPlannerTest.kt app/src/test/java/com/nianri/app/data/ImportantDayRepositoryImportTest.kt
git commit -m "feat: plan and persist atomic configuration imports"
```

---

### Task 3: Transfer Service and Application Wiring

**Files:**
- Create: `app/src/main/java/com/nianri/app/data/transfer/ConfigurationTransferService.kt`
- Modify: `app/src/main/java/com/nianri/app/AppContainer.kt`
- Create: `app/src/test/java/com/nianri/app/data/transfer/ConfigurationTransferServiceTest.kt`
- Modify: `app/src/test/java/com/nianri/app/AppContainerTest.kt`

**Interfaces:**
- Consumes: codec, planner, atomic repository APIs, `ReminderScheduler.rebuildAll()`, and `WidgetUpdater`.
- Produces: `ConfigurationTransferService.exportConfiguration(): String`.
- Produces: `ConfigurationTransferService.importConfiguration(String): TransferImportResult`.
- Produces: `TransferImportResult(importedCount, renamedCount, refreshFailed)`.

- [ ] **Step 1: Write failing service tests for round trip, validation isolation, and side-effect order**

```kotlin
@Test fun `import commits before rebuilding reminders and widgets`() = runBlocking {
    val events = mutableListOf<String>()
    val service = service(
        reminders = scheduler(onRebuild = { events += "reminders:${repository.getAll().size}" }),
        widgets = WidgetUpdater { events += "widgets:${repository.getAll().size}" },
    )

    val result = service.importConfiguration(codec.encode(document(day("A"), day("B"))))

    assertEquals(2, result.importedCount)
    assertEquals(listOf("reminders:2", "widgets:2"), events)
}

@Test fun `invalid document performs no mutation or refresh`() = runBlocking {
    assertThrows(TransferFormatException.UnsupportedVersion::class.java) {
        runBlocking { service.importConfiguration(versionTwoJson) }
    }
    assertTrue(repository.getAll().isEmpty())
    assertTrue(events.isEmpty())
}

@Test fun `refresh failure returns warning without deleting imported rows`() = runBlocking {
    val result = service(reminderFailure = IOException("alarm unavailable"))
        .importConfiguration(codec.encode(document(day("A"))))
    assertEquals(1, repository.getAll().size)
    assertTrue(result.refreshFailed)
}
```

- [ ] **Step 2: Run service tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.data.transfer.ConfigurationTransferServiceTest`

Expected: compilation fails because `ConfigurationTransferService` does not exist.

- [ ] **Step 3: Implement the service**

```kotlin
data class TransferImportResult(
    val importedCount: Int,
    val renamedCount: Int,
    val refreshFailed: Boolean,
)

class ConfigurationTransferService(
    private val days: ImportantDayRepository,
    private val codec: TransferCodec,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
    private val clock: Clock,
) {
    suspend fun exportConfiguration(): String = codec.encode(
        TransferDocument(
            exportedAt = clock.instant(),
            days = days.getAll().map { it.copy(id = 0) },
        ),
    )

    suspend fun importConfiguration(text: String): TransferImportResult {
        val decoded = codec.decode(text)
        val existing = days.getAll()
        val date = LocalDate.now(clock)
        val plan = ImportPlanner.plan(existing, decoded.days, date)
        widgets.prepareMutation()
        days.importAll(plan.days)

        var refreshFailed = false
        try {
            reminders.rebuildAll()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            refreshFailed = true
        }
        try {
            widgets.updateAll()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            refreshFailed = true
        }
        return TransferImportResult(plan.days.size, plan.renamedCount, refreshFailed)
    }
}
```

Bind `transferCodec` and `configurationTransferService` lazily in `AppContainer` using the existing `importantDays`, `reminderScheduler`, `widgetUpdater`, and `CurrentSystemZoneClock` dependencies.

- [ ] **Step 4: Run service and container tests**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.data.transfer.ConfigurationTransferServiceTest --tests com.nianri.app.AppContainerTest`

Expected: all selected tests pass with zero failures.

- [ ] **Step 5: Commit Task 3**

```bash
git add app/src/main/java/com/nianri/app/data/transfer/ConfigurationTransferService.kt app/src/main/java/com/nianri/app/AppContainer.kt app/src/test/java/com/nianri/app/data/transfer/ConfigurationTransferServiceTest.kt app/src/test/java/com/nianri/app/AppContainerTest.kt
git commit -m "feat: coordinate configuration transfer"
```

---

### Task 4: Transfer ViewModel State and User-Facing Errors

**Files:**
- Create: `app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt`
- Create: `app/src/test/java/com/nianri/app/ui/transfer/TransferViewModelTest.kt`

**Interfaces:**
- Consumes: `Flow<List<ImportantDay>>` and `ConfigurationTransferService` methods.
- Produces: `TransferTab`, `TransferUiState`, `TransferMessage`, `selectTab`, `exportTo`, `importFrom`, `clearMessage`, and `defaultExportFileName`.

- [ ] **Step 1: Write failing state tests**

```kotlin
@Test fun `defaults to export and exposes current day count`() {
    val days = MutableStateFlow(listOf(day("A"), day("B")))
    val viewModel = viewModel(days = days)
    idleMainLooper()
    assertEquals(TransferTab.EXPORT, viewModel.uiState.value.selectedTab)
    assertEquals(2, viewModel.uiState.value.dayCount)
}

@Test fun `successful import reports imported and renamed counts`() {
    val viewModel = viewModel(importResult = TransferImportResult(5, 2, false))
    viewModel.importFrom { "valid json" }
    waitForState { !viewModel.uiState.value.isProcessing }
    assertEquals("已导入 5 个纪念日，其中 2 个因重名已改名", viewModel.uiState.value.message?.text)
}

@Test fun `unsupported version has dedicated copy and processing always resets`() {
    val viewModel = viewModel(importFailure = TransferFormatException.UnsupportedVersion(2))
    viewModel.importFrom { "version 2" }
    waitForState { !viewModel.uiState.value.isProcessing }
    assertEquals("配置版本暂不支持", viewModel.uiState.value.message?.text)
}
```

- [ ] **Step 2: Run ViewModel tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.ui.transfer.TransferViewModelTest`

Expected: compilation fails because transfer UI state types do not exist.

- [ ] **Step 3: Implement state, messages, and async operations**

Use these types and public operations:

```kotlin
enum class TransferTab { EXPORT, IMPORT }
enum class TransferMessageKind { SUCCESS, WARNING, ERROR }
data class TransferMessage(val kind: TransferMessageKind, val text: String)
data class TransferUiState(
    val selectedTab: TransferTab = TransferTab.EXPORT,
    val dayCount: Int = 0,
    val isProcessing: Boolean = false,
    val message: TransferMessage? = null,
)

fun defaultExportFileName(date: LocalDate): String =
    "念日配置-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.nianri.json"
```

`TransferViewModel` must collect `days.map(List<ImportantDay>::size)` into state, reject a second operation while `isProcessing`, and run file lambdas plus service work on `Dispatchers.IO`. Its APIs are:

```kotlin
class TransferViewModel(
    days: Flow<List<ImportantDay>>,
    private val exportConfiguration: suspend () -> String,
    private val importConfiguration: suspend (String) -> TransferImportResult,
    private val currentDate: () -> LocalDate = LocalDate::now,
) : ViewModel()

fun selectTab(tab: TransferTab)
fun exportTo(write: suspend (String) -> Unit)
fun importFrom(read: suspend () -> String)
fun clearMessage()
fun defaultExportFileName(): String

class Factory(private val container: AppContainer) : ViewModelProvider.Factory
```

Map errors exactly:

```kotlin
is TransferFormatException.NotNianriConfiguration -> "选择的文件不是念日配置"
is TransferFormatException.UnsupportedVersion -> "配置版本暂不支持"
is TransferFormatException.Corrupt -> "配置文件已损坏或结构不完整"
is TransferFormatException.InvalidDay -> "配置中包含无效的纪念日"
is IOException -> "文件无法读取或写入"
else -> "导入失败，请重试" // use "导出失败，请重试" in the export path
```

Success copy is `已导出 N 个纪念日`, `已导入 N 个纪念日`, or `已导入 N 个纪念日，其中 R 个因重名已改名`. When `refreshFailed` is true, use warning copy `已导入 N 个纪念日，但提醒刷新失败，请重新打开应用或检查提醒权限`.

- [ ] **Step 4: Run ViewModel tests**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.ui.transfer.TransferViewModelTest`

Expected: all selected tests pass with zero failures.

- [ ] **Step 5: Commit Task 4**

```bash
git add app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt app/src/test/java/com/nianri/app/ui/transfer/TransferViewModelTest.kt
git commit -m "feat: model configuration transfer state"
```

---

### Task 5: Migration Bottom Sheet and Home Entry

**Files:**
- Create: `app/src/main/java/com/nianri/app/ui/transfer/TransferSheet.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`
- Modify: `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `TransferUiState` and the callbacks from Task 4.
- Produces: `TransferSheet` and the `迁移` header entry immediately left of `+`.

- [ ] **Step 1: Write failing Compose tests for placement, default Tab, switching, and disabled states**

```kotlin
@Test fun migrationButtonOpensSheetWithExportSelectedByDefault() {
    composeRule.setContent {
        HomeScreen(
            state = HomeUiState(isLoading = false, showCalendarExplanation = false),
            transferState = TransferUiState(dayCount = 2),
        )
    }
    composeRule.onNodeWithText("迁移").performClick()
    composeRule.onNodeWithText("导出全部配置").assertIsDisplayed()
    composeRule.onNodeWithTag("transfer-tab-export").assertIsSelected()
}

@Test fun importTabInvokesTheImportPickerCallback() {
    var imports = 0
    composeRule.setContent {
        HomeScreen(
            state = HomeUiState(isLoading = false, showCalendarExplanation = false),
            transferState = TransferUiState(dayCount = 2),
            onRequestImport = { imports++ },
        )
    }
    composeRule.onNodeWithText("迁移").performClick()
    composeRule.onNodeWithTag("transfer-tab-import").performClick()
    composeRule.onNodeWithText("选择配置并导入").performClick()
    composeRule.runOnIdle { assertEquals(1, imports) }
}

@Test fun emptyExportAndProcessingDisableActions() {
    composeRule.setContent {
        HomeScreen(
            state = HomeUiState(isLoading = false, showCalendarExplanation = false),
            transferState = TransferUiState(dayCount = 0),
        )
    }
    composeRule.onNodeWithText("迁移").performClick()
    composeRule.onNodeWithText("暂无可导出的纪念日").assertIsDisplayed()
    composeRule.onNodeWithText("导出全部配置").assertIsNotEnabled()

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
    composeRule.onNodeWithText("选择配置并导入").assertIsNotEnabled()
}
```

- [ ] **Step 2: Run the focused connected test and verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest`

Expected: compilation fails because `transferState`, callbacks, and `TransferSheet` do not exist.

- [ ] **Step 3: Implement `TransferSheet`**

Use `ModalBottomSheet`, `TabRow`, and two `Tab` controls tagged `transfer-tab-export` and `transfer-tab-import`. Required signatures:

```kotlin
@Composable
fun TransferSheet(
    state: TransferUiState,
    onDismiss: () -> Unit,
    onSelectTab: (TransferTab) -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onMessageShown: () -> Unit,
)
```

Export content shows `将导出 ${state.dayCount} 个纪念日`; disable its button when `dayCount == 0 || isProcessing`. Import content says `导入会与本机数据合并，不会删除已有纪念日`; disable its button only while processing. Show `CircularProgressIndicator` with `正在处理…` during work. Render success/warning/error messages as text and call `onMessageShown` only when the user explicitly dismisses the message or closes the sheet, not immediately on composition.

- [ ] **Step 4: Add the home header entry and sheet state**

Extend `HomeScreen` with defaultable parameters:

```kotlin
transferState: TransferUiState = TransferUiState(),
onSelectTransferTab: (TransferTab) -> Unit = {},
onRequestExport: () -> Unit = {},
onRequestImport: () -> Unit = {},
onTransferMessageShown: () -> Unit = {},
```

Keep a `rememberSaveable` Boolean for sheet visibility. In the existing header action row, render `TextButton("迁移")` before the `+` button whenever `state.isLoading` is false. Opening/closing the sheet must not change HomeViewModel state.

- [ ] **Step 5: Run connected HomeScreen tests**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest`

Expected: all `HomeScreenTest` tests pass with zero failures.

- [ ] **Step 6: Commit Task 5**

```bash
git add app/src/main/java/com/nianri/app/ui/transfer/TransferSheet.kt app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt
git commit -m "feat: add configuration transfer sheet"
```

---

### Task 6: Storage Access Framework Integration and Documentation

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`
- Create: `app/src/androidTest/java/com/nianri/app/ui/TransferDocumentLauncherTest.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: `TransferViewModel` and `TransferSheet` callbacks.
- Produces: `CreateDocument("application/json")` export and `OpenDocument` import launchers, UTF-8 URI read/write, and user documentation.

- [ ] **Step 1: Add Espresso Intents and write the failing import-launcher test**

Add:

```kotlin
androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
```

Test the real activity wiring:

```kotlin
@get:Rule val compose = createAndroidComposeRule<MainActivity>()

@Before fun initIntents() = Intents.init()
@After fun releaseIntents() = Intents.release()

@Test fun importActionUsesOpenDocumentWithoutStoragePermission() {
    intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

    compose.waitUntilAtLeastOneExists(hasText("迁移"))
    compose.onNodeWithText("迁移").performClick()
    compose.onNodeWithTag("transfer-tab-import").performClick()
    compose.onNodeWithText("选择配置并导入").performClick()

    intended(allOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasCategory(Intent.CATEGORY_OPENABLE)))
}
```

- [ ] **Step 2: Run the launcher test and verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.TransferDocumentLauncherTest`

Expected: test fails because the Home callbacks are not connected to an Activity Result launcher.

- [ ] **Step 3: Wire the ViewModel and SAF launchers in `NianriNavHost`**

Within the `home` destination:

```kotlin
val context = LocalContext.current
val transferViewModel: TransferViewModel = viewModel(factory = TransferViewModel.Factory(container))
val transferState by transferViewModel.uiState.collectAsStateWithLifecycle()

val createDocument = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json"),
) { uri ->
    if (uri != null) transferViewModel.exportTo { text ->
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Unable to open output")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
val openDocument = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
) { uri ->
    if (uri != null) transferViewModel.importFrom {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open input")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
```

Pass `transferState` and ViewModel callbacks to `HomeScreen`; launch export with `transferViewModel.defaultExportFileName()` and import with `arrayOf("application/json", "text/plain")`. Cancellation (`uri == null`) must do nothing and leave the sheet operable.

- [ ] **Step 4: Document migration in README**

Add a “跨设备迁移” section explaining: Home → 迁移 → 导出; move the `.nianri.json` file; B device → 迁移 → 导入; merges preserve B's pin and rename exact conflicts. Also add configuration transfer to the feature list and retain the statement that the app requests no network permission.

- [ ] **Step 5: Run launcher test, manifest permission regression, and README checks**

Run:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.TransferDocumentLauncherTest
./gradlew testDebugUnitTest --tests com.nianri.app.reminder.ReminderManifestTest
rg -n "跨设备迁移|\.nianri\.json|迁移" README.md
```

Expected: launcher and manifest tests pass; README search returns the new migration instructions; no storage permission appears in the manifest.

- [ ] **Step 6: Commit Task 6**

```bash
git add app/build.gradle.kts app/src/main/java/com/nianri/app/ui/NianriNavHost.kt app/src/androidTest/java/com/nianri/app/ui/TransferDocumentLauncherTest.kt README.md
git commit -m "feat: connect configuration files to Android picker"
```

---

### Task 7: Full Regression and Device-Facing Verification

**Files:**
- Verify: all files created or modified by Tasks 1–6.

**Interfaces:**
- Consumes: the complete transfer workflow.
- Produces: fresh evidence for unit tests, lint, build, instrumentation tests, and the user-visible import/export acceptance path.

- [ ] **Step 1: Run all JVM tests**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 2: Run lint and assemble the debug APK**

Run: `./gradlew lintDebug assembleDebug`

Expected: `BUILD SUCCESSFUL`; APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Run all connected Android tests**

Run: `./gradlew connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL`, zero failed instrumentation tests on the connected emulator/device.

- [ ] **Step 4: Verify the merged manifest still has no storage or network permission**

Run:

```bash
rg "uses-permission" app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: existing notification, exact alarm, boot, and dependency-provided network-state entries only; no `READ_*`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, or `INTERNET` permission.

- [ ] **Step 5: Exercise the acceptance path on two clean app data sets**

On A, create one pinned day and one ordinary day, export `念日配置-yyyyMMdd.nianri.json`, and inspect that it contains no database IDs. On B, create a same-name day and an existing pinned day, import the file, and verify:

- B's old rows are unchanged;
- the duplicate is named `原名称-yyyyMMdd导入`;
- B's original pin remains pinned and A's imported pin is not pinned;
- imported reminder time/options match A;
- relaunching the app preserves all imported rows.

Repeat with B having no pin and verify A's pin is inherited. Import the same file again on the same date and verify `-2` fallback names.

- [ ] **Step 6: Review the final diff against the design spec**

Run:

```bash
git diff HEAD~6..HEAD --check
git status --short
```

Expected: no whitespace errors and no unintended files. Compare every acceptance criterion in `docs/superpowers/specs/2026-07-19-configuration-transfer-design.md` to a passing automated or device check.

- [ ] **Step 7: Commit any verification-only fixes**

If Step 1–6 required a scoped fix, rerun the failing command and commit only that fix:

```bash
git add README.md app/build.gradle.kts app/src/main/java/com/nianri/app/AppContainer.kt app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt app/src/main/java/com/nianri/app/data/local/ImportantDayDao.kt app/src/main/java/com/nianri/app/data/transfer app/src/main/java/com/nianri/app/domain/model/ImportantDayValidation.kt app/src/main/java/com/nianri/app/ui/NianriNavHost.kt app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt app/src/main/java/com/nianri/app/ui/transfer app/src/test/java/com/nianri/app app/src/androidTest/java/com/nianri/app/ui
git commit -m "fix: complete configuration transfer verification"
```

If no fix was needed, do not create an empty commit.
