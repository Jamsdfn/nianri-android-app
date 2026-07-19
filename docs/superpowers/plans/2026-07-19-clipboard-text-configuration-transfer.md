# Clipboard and Text Configuration Transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing configuration migration sheet so users can copy exported JSON to the Android clipboard and import JSON pasted into an editable text field while preserving file migration.

**Architecture:** Keep `TransferCodec` and `ConfigurationTransferService` unchanged; every channel carries the same version-1 JSON. Extend `TransferViewModel` with import text and channel-specific operations, keep `TransferSheet` as pure state rendering, and let `NianriNavHost` be the only layer that calls Android `ClipboardManager` or Activity Result file contracts.

**Tech Stack:** Kotlin 2.3.10, Android API 26+, Jetpack Compose Material 3, Android `ClipboardManager`, Room 2.8.4, JUnit 4, Robolectric, AndroidX Compose UI Test, Espresso Intents.

## Global Constraints

- Preserve the existing `.nianri.json` file export/import paths and version-1 JSON format.
- Clipboard export and file export must call the same `exportConfiguration()` service and produce the same JSON text.
- Pasted text and file text must call the same `importConfiguration(String)` service and retain all existing validation, name conflict, pin priority, transaction, reminder, and widget behavior.
- Never read the clipboard automatically; read it only after the user taps “从剪贴板粘贴”.
- Empty or unreadable clipboard content must not replace the current input text.
- Clear pasted text only after a successful import; retain it after every import failure.
- Disable all transfer actions while `isProcessing`; editing text alone does not enter processing state.
- Do not add storage, clipboard, network, or other manifest permissions.
- Keep the existing top-level “导出 / 导入” Tabs; do not add nested Tabs or a second transfer format.

---

## File Structure

- `app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt`: add editable import text, clipboard copy/paste operations, and channel-specific messages.
- `app/src/main/java/com/nianri/app/ui/transfer/TransferSheet.kt`: render both export channels and both import channels.
- `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`: pass the new pure UI callbacks into the sheet.
- `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`: bridge copy/paste callbacks to Android `ClipboardManager` while retaining file launchers.
- Existing ViewModel, HomeScreen, and launcher test files are extended; codec/service/repository files remain unchanged.

---

### Task 1: ViewModel Clipboard and Text State

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt`
- Modify: `app/src/test/java/com/nianri/app/ui/transfer/TransferViewModelTest.kt`

**Interfaces:**
- Consumes: existing `exportConfiguration(): String` and `importConfiguration(String): TransferImportResult` functions.
- Produces: `TransferUiState.importText: String`.
- Produces: `setImportText(String)`, `copyToClipboard(suspend (String) -> Unit)`, `pasteFromClipboard(() -> String?)`, and `importPastedText()`.
- Preserves: `exportTo`, `importFrom`, file-specific success/error copy, `isProcessing`, and `clearMessage`.

- [ ] **Step 1: Write failing ViewModel tests for text lifecycle and clipboard messages**

Add these tests to `TransferViewModelTest`:

```kotlin
@Test fun `copy and file export receive the same configuration text with distinct success copy`() {
    val fileWrites = mutableListOf<String>()
    val clipboardWrites = mutableListOf<String>()
    val viewModel = viewModel(days = MutableStateFlow(listOf(day("A"))))
    waitForState { viewModel.uiState.value.dayCount == 1 }

    viewModel.exportTo { fileWrites += it }
    waitForFinished(viewModel)
    assertEquals("已导出 1 个纪念日", viewModel.uiState.value.message?.text)

    viewModel.copyToClipboard { clipboardWrites += it }
    waitForFinished(viewModel)

    assertEquals(listOf("exported-json"), fileWrites)
    assertEquals(fileWrites, clipboardWrites)
    assertEquals("配置已复制到剪贴板", viewModel.uiState.value.message?.text)
}

@Test fun `successful pasted import uses current text then clears it`() {
    val imported = mutableListOf<String>()
    val viewModel = viewModel(importedTexts = imported)
    viewModel.setImportText("pasted-json")

    viewModel.importPastedText()
    waitForFinished(viewModel)

    assertEquals(listOf("pasted-json"), imported)
    assertEquals("", viewModel.uiState.value.importText)
}

@Test fun `failed pasted import retains text and exact parse error`() {
    val viewModel = viewModel(
        importFailure = TransferFormatException.UnsupportedVersion(2),
    )
    viewModel.setImportText("version-two-json")

    viewModel.importPastedText()
    waitForFinished(viewModel)

    assertEquals("version-two-json", viewModel.uiState.value.importText)
    assertEquals("配置版本暂不支持", viewModel.uiState.value.message?.text)
}

@Test fun `clipboard paste replaces text only when nonblank`() {
    val viewModel = viewModel()
    viewModel.setImportText("keep-me")
    viewModel.pasteFromClipboard { "new-json" }
    assertEquals("new-json", viewModel.uiState.value.importText)

    viewModel.pasteFromClipboard { "  " }
    assertEquals("new-json", viewModel.uiState.value.importText)
    assertEquals("剪贴板中没有可粘贴的配置", viewModel.uiState.value.message?.text)
}

@Test fun `clipboard read and write failures use clipboard specific copy`() {
    val viewModel = viewModel()
    viewModel.pasteFromClipboard { error("read failed") }
    assertEquals("读取剪贴板失败，请重试", viewModel.uiState.value.message?.text)

    viewModel.copyToClipboard { error("write failed") }
    waitForFinished(viewModel)
    assertEquals("复制失败，请重试", viewModel.uiState.value.message?.text)
}
```

Update the test helper so `importConfiguration` appends its input to `importedTexts` before returning or throwing.

- [ ] **Step 2: Run the focused ViewModel test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.ui.transfer.TransferViewModelTest`

Expected: compilation fails because `importText`, `copyToClipboard`, `pasteFromClipboard`, and `importPastedText` do not exist.

- [ ] **Step 3: Add state and implement clipboard/text operations**

Extend state:

```kotlin
data class TransferUiState(
    val selectedTab: TransferTab = TransferTab.EXPORT,
    val dayCount: Int = 0,
    val isProcessing: Boolean = false,
    val importText: String = "",
    val message: TransferMessage? = null,
)
```

Add these public methods:

```kotlin
fun setImportText(text: String) {
    mutableState.update { it.copy(importText = text) }
}

fun copyToClipboard(copy: suspend (String) -> Unit) {
    startOperation(
        failureCopy = { "复制失败，请重试" },
    ) {
        copy(exportConfiguration())
        TransferMessage(TransferMessageKind.SUCCESS, "配置已复制到剪贴板")
    }
}

fun pasteFromClipboard(read: () -> String?) {
    if (mutableState.value.isProcessing) return
    try {
        val text = read()
        if (text.isNullOrBlank()) {
            mutableState.update {
                it.copy(
                    message = TransferMessage(
                        TransferMessageKind.ERROR,
                        "剪贴板中没有可粘贴的配置",
                    ),
                )
            }
        } else {
            mutableState.update { it.copy(importText = text, message = null) }
        }
    } catch (_: Exception) {
        mutableState.update {
            it.copy(
                message = TransferMessage(
                    TransferMessageKind.ERROR,
                    "读取剪贴板失败，请重试",
                ),
            )
        }
    }
}

fun importPastedText() {
    val text = mutableState.value.importText
    if (text.isBlank()) return
    startOperation(
        failureCopy = { error -> errorCopy(error, isImport = true) },
        onSuccess = { state -> state.copy(importText = "") },
    ) {
        importConfiguration(text).toMessage()
    }
}
```

Generalize the existing private helper without changing current callers:

```kotlin
private fun startOperation(
    failureCopy: (Exception) -> String,
    onSuccess: (TransferUiState) -> TransferUiState = { it },
    operation: suspend () -> TransferMessage,
)
```

Inside its coroutine, track whether `operation()` completed normally. On normal completion, apply `onSuccess` while setting `isProcessing = false` and the success message. On failure, set only `isProcessing = false` and the mapped error message; do not call `onSuccess`. Make `exportTo` pass the existing file-export mapping and make `importFrom` pass the existing import mapping.

- [ ] **Step 4: Run ViewModel tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.ui.transfer.TransferViewModelTest`

Expected: all selected tests pass with zero failures and no new compiler warnings.

- [ ] **Step 5: Commit Task 1**

```bash
git add app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt app/src/test/java/com/nianri/app/ui/transfer/TransferViewModelTest.kt
git commit -m "feat: model clipboard configuration transfer"
```

---

### Task 2: Dual-Channel Transfer Sheet

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/transfer/TransferSheet.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`
- Modify: `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: Task 1 `TransferUiState.importText` and ViewModel callbacks.
- Produces callbacks: `onCopyExport`, `onImportTextChange`, `onPasteFromClipboard`, and `onImportPastedText`.
- Preserves callbacks: file export/import, Tab selection, message dismissal, and sheet dismissal.

- [ ] **Step 1: Write failing Compose tests for all four channels**

Add these tests to `HomeScreenTest`:

```kotlin
@Test fun exportTabShowsFileAndClipboardActionsWithPrivacyCopy() {
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

@Test fun importTabEditsPastesAndImportsText() {
    var text by mutableStateOf("")
    val events = mutableListOf<String>()
    composeRule.setContent {
        HomeScreen(
            state = HomeUiState(isLoading = false, showCalendarExplanation = false),
            transferState = TransferUiState(selectedTab = TransferTab.IMPORT, importText = text),
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

@Test fun emptyTextAndProcessingDisableTheRelevantActions() {
    composeRule.setContent {
        HomeScreen(
            state = HomeUiState(isLoading = false, showCalendarExplanation = false),
            transferState = TransferUiState(selectedTab = TransferTab.IMPORT, importText = ""),
        )
    }
    composeRule.onNodeWithText("迁移").performClick()
    composeRule.onNodeWithText("导入粘贴内容").assertIsNotEnabled()
}
```

Extend the existing processing and zero-day tests to assert clipboard export, file import, paste, and text import controls are disabled under the corresponding state.

- [ ] **Step 2: Run focused HomeScreen instrumentation tests and verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest`

Expected: compilation fails because the new state field and callbacks are not yet accepted by `HomeScreen`/`TransferSheet`, or assertions fail because the controls are absent.

- [ ] **Step 3: Extend `TransferSheet` and `HomeScreen`**

Extend `TransferSheet` with:

```kotlin
onCopyExport: () -> Unit,
onImportTextChange: (String) -> Unit,
onPasteFromClipboard: () -> Unit,
onImportPastedText: () -> Unit,
```

In export content, render:

```kotlin
Button(
    onClick = onRequestExport,
    enabled = state.dayCount > 0 && !state.isProcessing,
    modifier = Modifier.fillMaxWidth(),
) { Text("保存配置到本机") }

OutlinedButton(
    onClick = onCopyExport,
    enabled = state.dayCount > 0 && !state.isProcessing,
    modifier = Modifier.fillMaxWidth(),
) { Text("复制配置到剪贴板") }

Text("剪贴板配置包含纪念日名称，请注意隐私。", color = TextMuted)
```

In import content, keep the existing explanation and render:

```kotlin
Button(
    onClick = onRequestImport,
    enabled = !state.isProcessing,
    modifier = Modifier.fillMaxWidth(),
) { Text("选择配置文件") }

Text("或粘贴配置")

OutlinedTextField(
    value = state.importText,
    onValueChange = onImportTextChange,
    enabled = !state.isProcessing,
    placeholder = { Text("粘贴导出的念日配置") },
    minLines = 5,
    maxLines = 8,
    modifier = Modifier.fillMaxWidth().testTag("transfer-import-text"),
)

OutlinedButton(
    onClick = onPasteFromClipboard,
    enabled = !state.isProcessing,
    modifier = Modifier.fillMaxWidth(),
) { Text("从剪贴板粘贴") }

Button(
    onClick = onImportPastedText,
    enabled = state.importText.isNotBlank() && !state.isProcessing,
    modifier = Modifier.fillMaxWidth(),
) { Text("导入粘贴内容") }
```

Add the same four defaultable callbacks to `HomeScreen` and pass them unchanged into `TransferSheet`. Retain existing file callback names and behavior.

- [ ] **Step 4: Run focused HomeScreen tests and verify GREEN**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest`

Expected: all `HomeScreenTest` tests pass with zero failures.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/java/com/nianri/app/ui/transfer/TransferSheet.kt app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt
git commit -m "feat: add clipboard and text transfer controls"
```

---

### Task 3: Android Clipboard Integration and Documentation

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`
- Modify: `app/src/androidTest/java/com/nianri/app/ui/TransferDocumentLauncherTest.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1 ViewModel methods and Task 2 HomeScreen callbacks.
- Produces: explicit Android clipboard copy and paste behavior.
- Preserves: existing `CreateDocument("application/json")` and `OpenDocument()` launchers.

- [ ] **Step 1: Write failing real-activity clipboard integration tests**

Add to `TransferDocumentLauncherTest`:

```kotlin
@Test fun copyActionWritesVersionedJsonToClipboard() {
    saveDay("测试纪念日")
    compose.onNodeWithText("迁移").performClick()
    compose.waitUntil { compose.onAllNodesWithText("复制配置到剪贴板").fetchSemanticsNodes().isNotEmpty() }

    compose.onNodeWithText("复制配置到剪贴板").performClick()
    compose.waitUntil {
        clipboard().primaryClip?.getItemAt(0)?.text?.toString()?.contains("nianri-configuration") == true
    }

    assertTrue(clipboard().primaryClip!!.getItemAt(0).text.toString().contains("测试纪念日"))
}

@Test fun pasteActionReadsClipboardIntoImportField() {
    clipboard().setPrimaryClip(ClipData.newPlainText("test", "{pasted-json}"))
    compose.onNodeWithText("迁移").performClick()
    compose.onNodeWithTag("transfer-tab-import").performClick()

    compose.onNodeWithText("从剪贴板粘贴").performClick()

    compose.onNodeWithTag("transfer-import-text").assertTextContains("{pasted-json}")
}

@Test fun emptyClipboardDoesNotReplaceExistingInput() {
    clipboard().clearPrimaryClip()
    compose.onNodeWithText("迁移").performClick()
    compose.onNodeWithTag("transfer-tab-import").performClick()
    compose.onNodeWithTag("transfer-import-text").performTextInput("keep-me")

    compose.onNodeWithText("从剪贴板粘贴").performClick()

    compose.onNodeWithTag("transfer-import-text").assertTextContains("keep-me")
    compose.onNodeWithText("剪贴板中没有可粘贴的配置").assertIsDisplayed()
}
```

Add test helpers `clipboard(): ClipboardManager` and `saveDay(name: String)` using the already available real `NianriApplication` container. Clear both the database and primary clipboard in `@Before`.

- [ ] **Step 2: Run clipboard integration tests and verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.TransferDocumentLauncherTest`

Expected: compilation fails because navigation does not provide clipboard callbacks, or tests fail because the clipboard is unchanged.

- [ ] **Step 3: Wire `ClipboardManager` in `NianriNavHost`**

Inside the home destination, obtain the service once:

```kotlin
val clipboard = remember(context) {
    context.getSystemService(ClipboardManager::class.java)
}
```

Pass the callbacks:

```kotlin
onCopyExport = {
    transferViewModel.copyToClipboard { text ->
        clipboard.setPrimaryClip(ClipData.newPlainText("念日配置", text))
    }
},
onImportTextChange = transferViewModel::setImportText,
onPasteFromClipboard = {
    transferViewModel.pasteFromClipboard {
        clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
    }
},
onImportPastedText = transferViewModel::importPastedText,
```

Do not read `primaryClip` during composition. Do not change the existing file launcher callbacks.

- [ ] **Step 4: Update README migration instructions**

Change the “跨设备迁移” section so it documents both options:

```markdown
- 导出：可保存 `.nianri.json` 文件，或复制配置到剪贴板。
- 导入：可选择配置文件，或把配置粘贴到输入框后导入。
```

Retain the privacy and merge-rule explanation, and add one sentence that clipboard text contains important-day names.

- [ ] **Step 5: Run clipboard, file launcher, and permission regressions**

Run:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.TransferDocumentLauncherTest
./gradlew testDebugUnitTest --tests com.nianri.app.reminder.ReminderManifestTest
rg -n "剪贴板|粘贴|\.nianri\.json" README.md
```

Expected: all launcher/clipboard and manifest tests pass; README search returns both transfer channels; merged manifest still contains no storage or `INTERNET` permission.

- [ ] **Step 6: Commit Task 3**

```bash
git add app/src/main/java/com/nianri/app/ui/NianriNavHost.kt app/src/androidTest/java/com/nianri/app/ui/TransferDocumentLauncherTest.kt README.md
git commit -m "feat: connect clipboard configuration transfer"
```

---

### Task 4: Full Regression and Verification

**Files:**
- Verify: all files modified by Tasks 1–3.

**Interfaces:**
- Consumes: the completed file, clipboard, and pasted-text migration workflow.
- Produces: fresh evidence for unit tests, lint, APK build, instrumentation tests, manifest permissions, and clean git state.

- [ ] **Step 1: Run all JVM tests**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 2: Run lint and build the debug APK**

Run: `./gradlew lintDebug assembleDebug`

Expected: `BUILD SUCCESSFUL`; `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Run all connected Android tests**

Run: `./gradlew connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL`, zero failed instrumentation tests on the API 36 emulator.

- [ ] **Step 4: Audit permissions and diff**

Run:

```bash
rg "uses-permission" app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
! rg "android.permission.(READ_|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|INTERNET)" app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
git diff --check
git status --short
```

Expected: no storage or `INTERNET` permission, no whitespace errors, and no uncommitted files after task commits.

- [ ] **Step 5: Review every acceptance criterion against evidence**

Confirm:

- file save/open launcher tests still pass;
- clipboard JSON contains `nianri-configuration` and saved day names;
- pasted text reaches the same import service;
- successful text import clears input and failed import retains it;
- empty clipboard preserves existing text;
- all pre-existing codec, planner, repository, reminder, widget, and UI tests pass.

If verification reveals a defect, return to the owning task, add a failing regression test, apply only that fix, rerun the failing command, and commit the exact scoped files with `git commit -m "fix: complete clipboard transfer verification"`. Do not create an empty commit when no fix is needed.
