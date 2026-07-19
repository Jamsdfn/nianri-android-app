# Home Loading State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an explicit loading indicator while the home screen waits for the first local-day query result, and show the create-empty-state only after an empty result is confirmed.

**Architecture:** Wrap the projected-card flow in a private loading/loaded state so the ViewModel can distinguish “no emission yet” from an emitted empty list. Expose that distinction as `HomeUiState.isLoading`, and let the stateless Compose screen render a centered progress indicator while suppressing all loaded-only actions and content.

**Tech Stack:** Kotlin, Android 26+, Kotlin Coroutines Flow, Jetpack ViewModel, Jetpack Compose Material 3, JUnit 4/Robolectric, AndroidX Compose UI tests.

## Global Constraints

- Keep the existing dark gradient background and “念日” title visible while loading.
- Hide the add button, calendar explanation, day cards, grouping copy, and empty-state guide until the first Room emission.
- Show a purple circular progress indicator and the exact copy `正在加载日子…`.
- Do not add a fixed minimum loading duration, skeleton animation, database migration, or repository error state.
- A first emitted empty list is a loaded state and must show the existing create guide.

---

## File Structure

- `app/src/main/java/com/nianri/app/ui/home/HomeViewModel.kt`: owns the loading-versus-loaded distinction and exposes `HomeUiState.isLoading`.
- `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`: renders the progress indicator and gates loaded-only UI.
- `app/src/test/java/com/nianri/app/ui/home/HomeViewModelTest.kt`: verifies state before and after the first card-flow emission.
- `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt`: verifies loading visibility and the absence of misleading create UI.

---

### Task 1: Model the first local-data emission

**Files:**
- Create: `app/src/test/java/com/nianri/app/ui/home/HomeViewModelTest.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/home/HomeViewModel.kt:13-82`

**Interfaces:**
- Consumes: `Flow<List<DayCardModel>>` passed to `HomeViewModel`.
- Produces: `HomeUiState.isLoading: Boolean`; it is `true` before the first cards emission and `false` for every emitted list, including `emptyList()`.

- [ ] **Step 1: Write the failing ViewModel test**

Create `HomeViewModelTest.kt`:

```kotlin
package com.nianri.app.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nianri.app.data.UiPreferences
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.calendar.IcuCalendarConverter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun waitsForFirstCardsEmissionBeforeLeavingLoadingState() = runBlocking {
        val releaseFirstResult = CompletableDeferred<Unit>()
        val viewModel = HomeViewModel(
            cards = flow<List<DayCardModel>> {
                releaseFirstResult.await()
                emit(emptyList())
            },
            updateAppDisplay = { _, _ -> },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )

        assertTrue(viewModel.uiState.value.isLoading)

        releaseFirstResult.complete(Unit)
        while (viewModel.uiState.value.isLoading) yield()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.upcoming.isEmpty())
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.nianri.app.ui.home.HomeViewModelTest
```

Expected: Kotlin compilation fails because `HomeUiState` has no `isLoading` property.

- [ ] **Step 3: Add an atomic loading/loaded cards state**

In `HomeViewModel.kt`, add `kotlinx.coroutines.flow.map`, define the private state, and replace the raw empty-list `sourceCards` state:

```kotlin
private sealed interface CardsLoadState {
    data object Loading : CardsLoadState
    data class Loaded(val cards: List<DayCardModel>) : CardsLoadState
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val pinned: DayCardModel? = null,
    val upcoming: List<DayCardModel> = emptyList(),
    val showCalendarExplanation: Boolean = true,
    val displayError: String? = null,
)

private val sourceCards = cards
    .map<List<DayCardModel>, CardsLoadState> { CardsLoadState.Loaded(it) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CardsLoadState.Loading,
    )
```

Update the override collector so loading has no card list to reconcile:

```kotlin
sourceCards.collect { loadState ->
    val projectedCards = (loadState as? CardsLoadState.Loaded)?.cards
        ?: return@collect
    displayOverrides.update { overrides ->
        overrides.filterNot { (dayId, display) ->
            projectedCards.any { model ->
                model.day.id == dayId && model.day.appDisplay == display
            }
        }
    }
}
```

Update the `combine` transform to derive both the list and loading flag from the same atomic value:

```kotlin
) { loadState, overrides, explanationSeen, error ->
    val projectedCards = (loadState as? CardsLoadState.Loaded)?.cards.orEmpty()
    val cardsWithOverrides = projectedCards.map { model ->
        val display = overrides[model.day.id]
        if (display == null) model else model.withDisplayOverride(display)
    }
    val pinned = cardsWithOverrides.firstOrNull { it.day.isPinned }
    HomeUiState(
        isLoading = loadState is CardsLoadState.Loading,
        pinned = pinned,
        upcoming = cardsWithOverrides.filterNot { it === pinned },
        showCalendarExplanation = !explanationSeen,
        displayError = error,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.Eagerly,
    initialValue = HomeUiState(
        isLoading = true,
        showCalendarExplanation = !uiPreferences.calendarExplanationSeen.value,
    ),
)
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests com.nianri.app.ui.home.HomeViewModelTest
```

Expected: `BUILD SUCCESSFUL`, with the new test passing.

- [ ] **Step 5: Commit the state model**

```bash
git add app/src/main/java/com/nianri/app/ui/home/HomeViewModel.kt \
  app/src/test/java/com/nianri/app/ui/home/HomeViewModelTest.kt
git commit -m "feat: model home data loading state"
```

---

### Task 2: Render the loading feedback without the empty guide

**Files:**
- Modify: `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt:5-172`
- Modify: `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt:23-166`

**Interfaces:**
- Consumes: `HomeUiState.isLoading` from Task 1.
- Produces: Compose semantics tags `home-loading` and `home-loading-indicator`, plus visible copy `正在加载日子…`.

- [ ] **Step 1: Write the failing Compose test and mark existing loaded fixtures**

Import `assertDoesNotExist`, then add this test before `emptyStateOffersCreateAction`:

```kotlin
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
```

For every existing `HomeUiState(...)` fixture in this test file that represents loaded content, add `isLoading = false`; in particular, change empty-state fixtures to:

```kotlin
HomeUiState(
    isLoading = false,
    showCalendarExplanation = false,
)
```

- [ ] **Step 2: Compile the instrumentation test and verify RED**

Run:

```bash
./gradlew compileDebugAndroidTestKotlin
```

Expected: compilation succeeds, but the new loading behavior is not implemented. If a connected emulator is available, run the single class and expect `loadingStateShowsProgressWithoutEmptyOrCreateUi` to fail because `home-loading-indicator` does not exist:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest
```

- [ ] **Step 3: Gate loaded content and add the progress UI**

Import `CircularProgressIndicator`. In the header row, render the existing `TextButton` only when `!state.isLoading`:

```kotlin
if (!state.isLoading) {
    TextButton(
        onClick = onAdd,
        modifier = Modifier
            .testTag("home-add")
            .semantics { contentDescription = "新建重要日子" },
    ) {
        Text("＋", fontSize = 26.sp, color = TextPrimary)
    }
}
```

Wrap all lazy-list content after the header—calendar explanation, pinned card, upcoming cards, and `EmptyState`—inside `if (!state.isLoading)`. After the `LazyColumn`, add:

```kotlin
if (state.isLoading) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .testTag("home-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag("home-loading-indicator"),
            color = Violet300,
            strokeWidth = 3.dp,
        )
        Text(
            text = "正在加载日子…",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
```

- [ ] **Step 4: Run focused and regression verification**

Run JVM tests and compile all debug variants:

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no compilation errors. With a connected emulator, also run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest
```

Expected: all `HomeScreenTest` tests pass.

- [ ] **Step 5: Inspect the diff and commit the UI**

```bash
git diff --check
git diff -- app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt \
  app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt
git add app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt \
  app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt
git commit -m "feat: show loading while days initialize"
```
