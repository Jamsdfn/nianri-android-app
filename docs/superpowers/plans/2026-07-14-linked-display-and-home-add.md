# Linked Display and Home Add Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make App and every widget for the same important day share one display-calendar state, and remove the duplicate bottom-right home add button.

**Architecture:** Treat `ImportantDay.appDisplay` as the source of truth. `WidgetRepository` performs linked display writes transactionally, keeps the existing widget `display` column synchronized for upgrade compatibility, and returns affected widget IDs; a controller refreshes those instances after commit. Home uses the same controller entry point as the widget callback.

**Tech Stack:** Kotlin, Room, Coroutines/Flow, Glance RemoteViews, Jetpack Compose, AndroidX tests, Gradle, ADB.

## Global Constraints

- Calendar switching changes display only and never changes the countdown basis.
- All widgets referencing the same day switch together; widgets for other days do not.
- Keep the existing Room schema version and widget `display` column for compatibility.
- Remove `home-fab`; retain `home-add` and the empty-state create action.
- Validate on API 26, 31, 36, 37.1 and Xiaomi 15 Pro.

---

### Task 1: Add Transactional Linked Display State

**Files:**
- Modify: `app/src/androidTest/java/com/nianri/app/data/RepositoryTest.kt`
- Modify: `app/src/main/java/com/nianri/app/data/local/WidgetPreferenceDao.kt`
- Modify: `app/src/main/java/com/nianri/app/data/WidgetRepository.kt`
- Modify: `app/src/main/java/com/nianri/app/widget/WidgetUpdates.kt`
- Modify: `app/src/main/java/com/nianri/app/widget/ToggleWidgetCalendarAction.kt`
- Modify: `app/src/main/java/com/nianri/app/AppContainer.kt`

**Interfaces:**
- Produces: `LinkedDisplayChange(dayId: Long, display: CalendarSystem, appWidgetIds: List<Int>)`.
- Produces: `WidgetRepository.setLinkedDisplay(dayId, display)` and `toggleLinkedDisplay(appWidgetId)`.
- Produces: `LinkedDisplayController.set(dayId, display)` and `toggle(appWidgetId)`.

- [ ] **Step 1: Write failing repository and controller tests**

Update `RepositoryTest` to prove that resolution uses `day.appDisplay` even when a stored widget field differs; toggling one widget updates the day and all same-day records; setting from App affects only matching widget IDs; and the controller refreshes all matching instances.

Key assertions:

```kotlin
assertEquals(CalendarSystem.LUNAR, days.get(dayId)?.appDisplay)
assertEquals(CalendarSystem.LUNAR, database.widgetPreferenceDao().get(301)?.display)
assertEquals(CalendarSystem.LUNAR, database.widgetPreferenceDao().get(302)?.display)
assertEquals(CalendarSystem.SOLAR, database.widgetPreferenceDao().get(303)?.display)
assertEquals(listOf(301, 302), updated.sorted())
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.data.RepositoryTest
```

Expected: FAIL because widgets still toggle independently and only one instance refreshes.

- [ ] **Step 3: Add DAO operations for same-day widgets**

Add:

```kotlin
@Query("SELECT appWidgetId FROM widget_preferences WHERE importantDayId = :dayId ORDER BY appWidgetId")
suspend fun idsForDay(dayId: Long): List<Int>

@Query("UPDATE widget_preferences SET display = :display WHERE importantDayId = :dayId")
suspend fun updateDisplayForDay(dayId: Long, display: CalendarSystem)
```

- [ ] **Step 4: Implement linked repository transactions and source-of-truth resolution**

In `WidgetRepository`, add `LinkedDisplayChange`; implement `setLinkedDisplay` and `toggleLinkedDisplay` with `database.withTransaction`. Both update `ImportantDayDao.updateAppDisplay`, synchronize widget compatibility fields, and return `idsForDay`. Change `resolve` to return `WidgetResolution.Configured(day, day.appDisplay)`.

- [ ] **Step 5: Replace the one-widget controller with a linked controller**

Implement:

```kotlin
class LinkedDisplayController(
    private val widgets: WidgetRepository,
    private val updater: WidgetInstanceUpdater,
) {
    suspend fun set(dayId: Long, display: CalendarSystem) {
        val change = widgets.setLinkedDisplay(dayId, display) ?: return
        updateWidgetInstances(change.appWidgetIds, updater::update)
    }

    suspend fun toggle(appWidgetId: Int) {
        val change = widgets.toggleLinkedDisplay(appWidgetId) ?: return
        updateWidgetInstances(change.appWidgetIds, updater::update)
    }
}
```

Expose it from `AppContainer`; make `ToggleWidgetCalendarAction` call it.

- [ ] **Step 6: Run repository tests and verify GREEN**

Run the focused command from Step 2. Expected: all `RepositoryTest` tests PASS.

---

### Task 2: Connect Home and Remove Duplicate Add Button

**Files:**
- Modify: `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `LinkedDisplayController.set(dayId, display)`.
- Preserves: Home optimistic display override and failure snackbar behavior.

- [ ] **Step 1: Write failing home tests**

Add/adjust assertions so the ViewModel collaborator receives linked updates, `home-add` remains clickable, `home-fab` does not exist, and the safe-inset test no longer expects a FAB:

```kotlin
composeRule.onNodeWithTag("home-add").assertExists()
composeRule.onNodeWithTag("home-fab").assertDoesNotExist()
```

- [ ] **Step 2: Run focused home tests and verify RED**

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest
```

Expected: FAIL because `home-fab` still exists.

- [ ] **Step 3: Connect HomeViewModel to the linked controller**

Rename the injected callback to `updateLinkedDisplay` and have `Factory` provide `container.linkedDisplayController::set`. Keep the existing optimistic override, rollback, and error copy.

- [ ] **Step 4: Remove the bottom-right button and reclaim layout space**

Delete the `Button` tagged `home-fab`. Reduce list bottom content padding from `104.dp` to `24.dp`; reduce Snackbar bottom padding from `96.dp` to `20.dp`. Keep the title-bar `home-add` and `EmptyState(onAdd)`.

- [ ] **Step 5: Run focused home tests and verify GREEN**

Run the command from Step 2. Expected: all `HomeScreenTest` tests PASS.

- [ ] **Step 6: Run full verification and commit**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
git add app/src/main app/src/androidTest
git commit -m "feat: link app and widget display state"
```

Expected: all applicable tests PASS on API 26/31/36/37.1; build and lint succeed.

- [ ] **Step 7: Install and verify on Xiaomi 15 Pro**

```bash
adb -s adb-94cbaaa0-SEIjCm._adb-tls-connect._tcp install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`. Toggling in App updates both existing “我的生日” widgets; toggling either widget updates App and the other widget; the home bottom-right add button is absent.
