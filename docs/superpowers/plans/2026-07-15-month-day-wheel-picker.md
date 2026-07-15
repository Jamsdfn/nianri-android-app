# Month/Day Wheel Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the new/edit screen's plus/minus date controls with a two-column Compose wheel opened only by an explicit “编辑” text button.

**Architecture:** `EditDayScreen` owns the read-only date row and dialog visibility. A new focused `WheelPicker` owns list scrolling, snapping, selection semantics, and center styling; `MonthDayDialog` combines two wheels and keeps temporary values until confirmation.

**Tech Stack:** Kotlin, Jetpack Compose Foundation/Material 3, Compose UI Test, JUnit 4, Gradle, Android API 26–36.

## Global Constraints

- Change only “新建日子” and “编辑日子”; do not change home cards, details, widgets, countdown basis, persistence, or calendar conversion.
- Date text is read-only. Only “编辑” opens the dialog; its touch target is at least 48 dp and its content description is “编辑日期”.
- Use a dependency-free Compose wheel with snap fling, center emphasis, faded neighbors, and labels such as “7 月” and “15 日”.
- Solar uses the existing month maximum, including February 29. Lunar uses months 1–12 and days 1–30.
- Cancel writes nothing. Confirm writes month and day once and refreshes the existing preview.
- Keep `minSdk = 26` and `targetSdk = 36`.

---

### Task 1: Explicit date-edit entry point

**Files:**
- Modify: `app/src/androidTest/java/com/nianri/app/ui/EditDayScreenTest.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/edit/EditDayScreen.kt`

**Interfaces:**
- Consumes: existing `EditDayScreen` callback API.
- Produces: read-only date text plus a `TextButton` labeled `编辑` with content description `编辑日期`.

- [ ] **Step 1: Write the failing test**

Add imports for `assertHasClickAction`, `assertHasNoClickAction`, and `onNodeWithContentDescription`, then add:

```kotlin
@Test
fun dateTextIsReadOnlyAndOnlyEditButtonOpensPicker() {
    compose.setContent {
        EditDayScreen(
            state = state(), widgetReferences = 0,
            onBack = {}, onNameChange = {}, onBasisChange = {},
            onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
            onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
        )
    }

    compose.onNodeWithText("新历 8 月 6 日").assertHasNoClickAction()
    compose.onNodeWithContentDescription("编辑日期")
        .assertHasClickAction()
        .performClick()
    compose.onNodeWithText("选择新历日期").assertIsDisplayed()
}
```

- [ ] **Step 2: Run and verify RED**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.EditDayScreenTest#dateTextIsReadOnlyAndOnlyEditButtonOpensPicker
```

Expected: FAIL because `编辑日期` does not exist and the date currently owns the click action.

- [ ] **Step 3: Implement the read-only date row**

Replace the date `OutlinedButton` with:

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Row(
        modifier = Modifier.padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val prefix = if (state.activePicker == CalendarSystem.SOLAR) "新历" else "农历"
        Text(
            text = prefix + " " + state.month + " 月 " + state.day + " 日",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = { showDateDialog = true },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "编辑日期" },
        ) {
            Text("编辑")
        }
    }
}
```

Import `heightIn`, `contentDescription`, and `semantics`; remove `OutlinedButton`.

- [ ] **Step 4: Update existing dialog tests**

In `solarPickerPreservesThirtyFirst` and `solarPickerPreservesFebruaryTwentyNinth`, replace clicking the date text with:

```kotlin
compose.onNodeWithContentDescription("编辑日期").performClick()
```

- [ ] **Step 5: Run the entire screen test class**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.EditDayScreenTest
```

Expected: all `EditDayScreenTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nianri/app/ui/edit/EditDayScreen.kt app/src/androidTest/java/com/nianri/app/ui/EditDayScreenTest.kt
git commit -m "ui: add explicit date edit action"
```

---

### Task 2: Reusable snapping wheel

**Files:**
- Create: `app/src/main/java/com/nianri/app/ui/edit/WheelPicker.kt`
- Create: `app/src/androidTest/java/com/nianri/app/ui/edit/WheelPickerTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable
internal fun WheelPicker(
    values: IntRange,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    valueLabel: (Int) -> String,
    pickerDescription: String,
    testTag: String,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write failing component tests**

Create `WheelPickerTest.kt` in package `com.nianri.app.ui.edit`:

```kotlin
class WheelPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectedValueIsCenteredAndAdjacentValueCanBeTapped() {
        var selected by mutableIntStateOf(5)
        compose.setContent {
            NianriTheme {
                WheelPicker(
                    values = 1..12,
                    selectedValue = selected,
                    onValueChange = { selected = it },
                    valueLabel = { it.toString() + " 月" },
                    pickerDescription = "月份选择器",
                    testTag = "test-wheel",
                )
            }
        }

        compose.onNodeWithContentDescription("月份选择器").assertIsDisplayed()
        compose.onNodeWithTag("test-wheel-item-5").assertIsSelected()
        compose.onNodeWithTag("test-wheel-item-6").performClick()
        compose.waitUntil { selected == 6 }
        compose.onNodeWithTag("test-wheel-item-6").assertIsSelected()
    }

    @Test
    fun selectedValueIsCoercedIntoRange() {
        var selected by mutableIntStateOf(31)
        compose.setContent {
            NianriTheme {
                WheelPicker(
                    values = 1..30,
                    selectedValue = selected,
                    onValueChange = { selected = it },
                    valueLabel = { it.toString() + " 日" },
                    pickerDescription = "日期选择器",
                    testTag = "test-wheel",
                )
            }
        }

        compose.waitUntil { selected == 30 }
        compose.onNodeWithTag("test-wheel-item-30").assertIsSelected()
    }
}
```

Use standard Compose test imports, `NianriTheme`, and JUnit imports.

- [ ] **Step 2: Run and verify RED**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.edit.WheelPickerTest
```

Expected: compilation FAIL because `WheelPicker` does not exist.

- [ ] **Step 3: Implement the minimal wheel**

Create `WheelPicker.kt` using `LazyColumn`, `rememberLazyListState`, `rememberSnapFlingBehavior`, five 48 dp rows, and 96 dp vertical content padding. Synchronize the nearest centered item:

```kotlin
val coercedValue = selectedValue.coerceIn(values.first, values.last)
val state = rememberLazyListState(
    initialFirstVisibleItemIndex = coercedValue - values.first,
)
val fling = rememberSnapFlingBehavior(lazyListState = state)

LaunchedEffect(values.first, values.last, coercedValue) {
    if (selectedValue != coercedValue) onValueChange(coercedValue)
    val target = coercedValue - values.first
    if (!state.isScrollInProgress && state.firstVisibleItemIndex != target) {
        state.scrollToItem(target)
    }
}
LaunchedEffect(state, values.first, values.last) {
    snapshotFlow {
        val info = state.layoutInfo
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { item ->
            abs(item.offset + item.size / 2 - center)
        }?.index
    }.distinctUntilChanged().collect { index ->
        index?.let { onValueChange(values.first + it) }
    }
}
```

Put a rounded `secondaryContainer` surface behind the central row. Each item must use:

```kotlin
val distance = abs(value - coercedValue)
Modifier
    .fillMaxWidth()
    .height(48.dp)
    .alpha(when (distance) { 0 -> 1f; 1 -> 0.55f; else -> 0.25f })
    .scale(if (distance == 0) 1.08f else 0.92f)
    .testTag(testTag + "-item-" + value)
    .semantics { selected = value == coercedValue }
    .clickable(role = Role.Button) {
        onValueChange(value)
        scope.launch { state.animateScrollToItem(value - values.first) }
    }
```

Expose `contentDescription = pickerDescription` and `stateDescription = valueLabel(coercedValue)` on the outer box. Keep the exact interface above and use a remembered coroutine scope for item taps.

- [ ] **Step 4: Run and verify GREEN**

Run the command from Step 2.

Expected: both tests pass with no recomposition loop or idle timeout.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nianri/app/ui/edit/WheelPicker.kt app/src/androidTest/java/com/nianri/app/ui/edit/WheelPickerTest.kt
git commit -m "ui: add snapping wheel picker"
```

---

### Task 3: Integrate month and day wheels

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/edit/EditDayScreen.kt`
- Modify: `app/src/androidTest/java/com/nianri/app/ui/EditDayScreenTest.kt`

**Interfaces:**
- Consumes: Task 2 `WheelPicker`.
- Produces: `month-wheel` and `day-wheel` while retaining `onConfirm(month, day)`.

- [ ] **Step 1: Write failing integration tests**

Add tests for month correction, cancel, and edit mode:

```kotlin
@Test
fun changingToShorterSolarMonthCoercesDayBeforeConfirming() {
    var month = 0
    var day = 0
    compose.setContent {
        EditDayScreen(
            state = state().copy(month = 3, day = 31), widgetReferences = 0,
            onBack = {}, onNameChange = {}, onBasisChange = {},
            onMonthChange = { month = it }, onDayChange = { day = it },
            onDisplayChange = {}, onToggleReminder = {}, onPinnedChange = {},
            onSave = {}, onDelete = {},
        )
    }

    compose.onNodeWithContentDescription("编辑日期").performClick()
    compose.onNodeWithTag("month-wheel-item-4").performClick()
    compose.onNodeWithText("确定").performClick()
    compose.runOnIdle {
        assertEquals(4, month)
        assertEquals(30, day)
    }
}

@Test
fun cancellingWheelEditsDoesNotWriteMonthOrDay() {
    val months = mutableListOf<Int>()
    val days = mutableListOf<Int>()
    compose.setContent {
        EditDayScreen(
            state = state(), widgetReferences = 0,
            onBack = {}, onNameChange = {}, onBasisChange = {},
            onMonthChange = { months += it }, onDayChange = { days += it },
            onDisplayChange = {}, onToggleReminder = {}, onPinnedChange = {},
            onSave = {}, onDelete = {},
        )
    }

    compose.onNodeWithContentDescription("编辑日期").performClick()
    compose.onNodeWithTag("month-wheel-item-9").performClick()
    compose.onNodeWithText("取消").performClick()
    compose.runOnIdle {
        assertTrue(months.isEmpty())
        assertTrue(days.isEmpty())
    }
}

@Test
fun editModeUsesTheSameWheelDialog() {
    compose.setContent {
        EditDayScreen(
            state = state().copy(id = 42), widgetReferences = 0,
            onBack = {}, onNameChange = {}, onBasisChange = {},
            onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
            onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
        )
    }

    compose.onNodeWithText("编辑日子").assertIsDisplayed()
    compose.onNodeWithContentDescription("编辑日期").performClick()
    compose.onNodeWithTag("month-wheel").assertIsDisplayed()
    compose.onNodeWithTag("day-wheel").assertIsDisplayed()
}
```

- [ ] **Step 2: Run and verify RED**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.EditDayScreenTest
```

Expected: new tests FAIL because the dialog still uses `NumberSelector`.

- [ ] **Step 3: Replace both selectors**

Inside `MonthDayDialog`:

```kotlin
val maximumDay = dayMaximum(calendar, month)
LaunchedEffect(maximumDay) {
    day = day.coerceAtMost(maximumDay)
}

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    WheelPicker(
        values = 1..12,
        selectedValue = month,
        onValueChange = { selectedMonth ->
            month = selectedMonth
            day = day.coerceAtMost(dayMaximum(calendar, selectedMonth))
        },
        valueLabel = { it.toString() + " 月" },
        pickerDescription = "月份选择器",
        testTag = "month-wheel",
        modifier = Modifier.weight(1f),
    )
    WheelPicker(
        values = 1..maximumDay,
        selectedValue = day,
        onValueChange = { day = it },
        valueLabel = { it.toString() + " 日" },
        pickerDescription = "日期选择器",
        testTag = "day-wheel",
        modifier = Modifier.weight(1f),
    )
}
```

Delete `NumberSelector`. Retain title, explanatory copy, Cancel, Confirm, `dayMaximum`, February 29, and lunar day 30.

- [ ] **Step 4: Run both UI test classes**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.edit.WheelPickerTest,com.nianri.app.ui.EditDayScreenTest
```

Expected: all component and screen tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nianri/app/ui/edit/EditDayScreen.kt app/src/androidTest/java/com/nianri/app/ui/EditDayScreenTest.kt
git commit -m "ui: use wheels for month and day"
```

---

### Task 4: Full regression, APK, and Xiaomi validation

**Files:**
- Modify only for a scoped verification fix: the two implementation and two UI test files above.
- Build: `app/build/outputs/apk/debug/app-debug.apk` (ignored).

- [ ] **Step 1: Run JVM, lint, and APK build**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`, zero failed tests, zero lint errors.

- [ ] **Step 2: Run all connected instrumentation tests**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew connectedDebugAndroidTest
```

Expected: zero failures on every reported device.

- [ ] **Step 3: Verify the APK**

```bash
APK=app/build/outputs/apk/debug/app-debug.apk
BUILD_TOOLS=/Users/alexander/Library/Android/sdk/build-tools/36.0.0
"$BUILD_TOOLS/zipalign" -c -P 16 4 "$APK"
"$BUILD_TOOLS/apksigner" verify --print-certs "$APK"
shasum -a 256 "$APK"
```

Expected: alignment and signature exit 0; SHA-256 prints.

- [ ] **Step 4: Install on Xiaomi 15 Pro**

```bash
/Users/alexander/Library/Android/sdk/platform-tools/adb devices -l
/Users/alexander/Library/Android/sdk/platform-tools/adb -s <xiaomi-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Keep app data. Verify on new and existing days: date text does nothing; “编辑” opens the correct calendar; wheels drag/fling/snap; March 31 → April 30; February 29 and lunar day 30 remain selectable; Cancel discards; Confirm updates the preview; home cards and widgets remain unchanged.

- [ ] **Step 5: Check final scope**

```bash
git status --short
git diff --check
git log --oneline --stat -5
```

Expected: no uncommitted source changes; only edit screen, wheel, tests, spec, and plan appear in the feature series.

