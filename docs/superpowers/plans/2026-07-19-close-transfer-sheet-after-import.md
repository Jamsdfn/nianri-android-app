# Close Transfer Sheet After Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the configuration transfer sheet after successful file or pasted-text import while keeping every failed import visible.

**Architecture:** Add an explicit one-shot `importCompleted` state to `TransferViewModel`, set it only when the import service returns normally, and expose `consumeImportCompletion()`. `HomeScreen` consumes that event with `LaunchedEffect`, closes the sheet, and delegates state cleanup through `NianriNavHost`.

**Tech Stack:** Kotlin 2.3.10, Jetpack ViewModel/StateFlow, Jetpack Compose Material 3, JUnit 4, Robolectric, AndroidX Compose UI Test.

## Global Constraints

- Both file and pasted-text imports close after the import service returns successfully.
- Reminder refresh warnings still close because imported data was committed.
- Import errors keep the sheet open and retain pasted text.
- Export completion never closes the sheet.
- Completion is explicit state and must not be inferred from message text or severity.

---

## File Structure

- `app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt`: owns and consumes the one-shot import completion state.
- `app/src/test/java/com/nianri/app/ui/transfer/TransferViewModelTest.kt`: verifies completion state for success, warning, and failure.
- `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`: observes completion and closes the sheet.
- `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt`: verifies sheet dismissal and failure retention.
- `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`: connects the UI consumption callback to the ViewModel.

### Task 1: Model One-Shot Import Completion

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt`
- Modify: `app/src/test/java/com/nianri/app/ui/transfer/TransferViewModelTest.kt`

**Interfaces:**
- Produces: `TransferUiState.importCompleted: Boolean`.
- Produces: `TransferViewModel.consumeImportCompletion()`.
- Preserves: existing import result messages, input clearing rules, and failure behavior.

- [ ] **Step 1: Write failing ViewModel assertions**

Extend the existing successful import and refresh-warning tests with:

```kotlin
assertTrue(viewModel.uiState.value.importCompleted)
```

Extend the unsupported-version and failed pasted-import tests with:

```kotlin
assertFalse(viewModel.uiState.value.importCompleted)
```

Add:

```kotlin
@Test
fun `consuming import completion clears event and hidden message`() {
    val viewModel = viewModel()
    viewModel.importFrom { "valid json" }
    waitForFinished(viewModel)

    viewModel.consumeImportCompletion()

    assertFalse(viewModel.uiState.value.importCompleted)
    assertEquals(null, viewModel.uiState.value.message)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.ui.transfer.TransferViewModelTest`

Expected: compilation fails because `importCompleted` and `consumeImportCompletion()` do not exist.

- [ ] **Step 3: Implement minimal completion state**

Add to `TransferUiState`:

```kotlin
val importCompleted: Boolean = false,
```

Make each import caller mark normal completion:

```kotlin
onSuccess = { state -> state.copy(importCompleted = true) }
```

For pasted import, preserve input clearing in the same transform:

```kotlin
onSuccess = { state -> state.copy(importText = "", importCompleted = true) }
```

Add:

```kotlin
fun consumeImportCompletion() {
    mutableState.update { it.copy(importCompleted = false, message = null) }
}
```

Do not set the flag from export operations or exception paths.

- [ ] **Step 4: Verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.nianri.app.ui.transfer.TransferViewModelTest`

Expected: `BUILD SUCCESSFUL`, zero failed selected tests.

- [ ] **Step 5: Commit Task 1**

```bash
git add app/src/main/java/com/nianri/app/ui/transfer/TransferViewModel.kt app/src/test/java/com/nianri/app/ui/transfer/TransferViewModelTest.kt
git commit -m "feat: signal completed configuration imports"
```

### Task 2: Consume Completion and Close the Sheet

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`
- Modify: `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `TransferUiState.importCompleted` from Task 1.
- Consumes: `TransferViewModel.consumeImportCompletion()` from Task 1.
- Produces: `HomeScreen(onImportCompletionConsumed: () -> Unit)`.

- [ ] **Step 1: Write failing Compose tests**

Add a state-driven test that opens the sheet, updates `transferState` to `importCompleted = true` with a success message, then verifies the sheet disappears and the consumption callback runs once. Repeat with `TransferMessageKind.WARNING`. Add an error test with `importCompleted = false` and verify “配置迁移” remains displayed.

Use this core pattern:

```kotlin
var transferState by mutableStateOf(
    TransferUiState(selectedTab = TransferTab.IMPORT),
)
var consumed = 0
composeRule.setContent {
    HomeScreen(
        state = HomeUiState(isLoading = false, showCalendarExplanation = false),
        transferState = transferState,
        onImportCompletionConsumed = { consumed++ },
    )
}
composeRule.onNodeWithText("迁移").performClick()
composeRule.runOnIdle {
    transferState = transferState.copy(
        importCompleted = true,
        message = TransferMessage(TransferMessageKind.SUCCESS, "已导入 1 个纪念日"),
    )
}
composeRule.waitForIdle()
composeRule.onNodeWithText("配置迁移").assertDoesNotExist()
composeRule.runOnIdle { assertEquals(1, consumed) }
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest`

Expected: compilation fails because `importCompleted` and `onImportCompletionConsumed` do not exist, or the sheet remains visible.

- [ ] **Step 3: Implement completion consumption**

Add a defaultable callback to `HomeScreen`:

```kotlin
onImportCompletionConsumed: () -> Unit = {},
```

After local state creation, add:

```kotlin
LaunchedEffect(transferState.importCompleted) {
    if (transferState.importCompleted) {
        showTransferSheet = false
        onImportCompletionConsumed()
    }
}
```

Connect it in `NianriNavHost`:

```kotlin
onImportCompletionConsumed = transferViewModel::consumeImportCompletion,
```

- [ ] **Step 4: Verify focused GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests com.nianri.app.ui.transfer.TransferViewModelTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest
```

Expected: both commands report `BUILD SUCCESSFUL` with zero failures.

- [ ] **Step 5: Run full regression and audit**

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
git diff --check
git status --short
```

Expected: all Gradle tasks succeed, the Debug APK exists, no whitespace errors, and only Task 2 files remain uncommitted.

- [ ] **Step 6: Commit Task 2**

```bash
git add app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt app/src/main/java/com/nianri/app/ui/NianriNavHost.kt app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt
git commit -m "feat: close migration sheet after import"
```
