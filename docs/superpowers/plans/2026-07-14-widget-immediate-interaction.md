# Widget Immediate Interaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make calendar-display toggles and widget configuration visibly take effect before their user action completes.

**Architecture:** Keep Room `appDisplay` as the linked source of truth and retain the existing non-overlapping date-row action. Replace fire-and-forget provider broadcasts for bound Nianri widgets with awaited `GlanceAppWidget.update` calls selected by provider component; retain the package broadcast only for an unbound/unknown safe fallback.

**Tech Stack:** Kotlin, Android AppWidgetManager, Jetpack Glance 1.1.1, Room, Robolectric, Android instrumentation.

## Global Constraints

- Calendar switching changes display only and never changes the countdown basis.
- The date text and trailing icon share the toggle action; primary content opens the App.
- App and every widget bound to the same day use that day's `appDisplay`.
- Minimum supported Android version remains API 26.

---

### Task 1: Await Bound Glance Rendering

**Files:**
- Modify: `app/src/main/java/com/nianri/app/widget/WidgetUpdates.kt`
- Modify: `app/src/test/java/com/nianri/app/widget/WidgetUpdatesTest.kt`

**Interfaces:**
- Consumes: `AppWidgetManager.getAppWidgetInfo(id).provider` and `AppWidgetId(id)`.
- Produces: `BoundWidgetRenderer.update(appWidgetId: Int, provider: ComponentName): Boolean`; `AndroidWidgetInstanceUpdater.update` returns only after a recognized Nianri widget has rendered.

- [ ] **Step 1: Write the failing tests**

Add tests that inject a recording `BoundWidgetRenderer` for wide and square provider components, assert it is awaited, and assert no `ACTION_APPWIDGET_UPDATE` broadcast is sent for a handled bound widget.

- [ ] **Step 2: Run tests to verify RED**

Run: `ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests 'com.nianri.app.widget.WidgetUpdatesTest'`

Expected: compilation/test failure because `BoundWidgetRenderer` and the direct-render constructor dependency do not exist.

- [ ] **Step 3: Implement the minimal direct renderer**

Add a renderer whose `when (provider.className)` branches call:

```kotlin
NianriWideWidget().update(context, AppWidgetId(appWidgetId))
NianriSquareWidget().update(context, AppWidgetId(appWidgetId))
```

Return `true` for recognized receiver classes and `false` otherwise. In `AndroidWidgetInstanceUpdater`, reject foreign providers, await the renderer for recognized bound widgets, and use the existing package/explicit broadcast only when no direct renderer handles the ID.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run the Task 1 command. Expected: `WidgetUpdatesTest` passes.

### Task 2: Verify Interaction and Configuration Contracts

**Files:**
- Modify: `app/src/androidTest/java/com/nianri/app/widget/WidgetConfigActivityTest.kt`
- Verify: `app/src/androidTest/java/com/nianri/app/widget/WidgetLayoutTest.kt`
- Modify: `docs/DEVICE_QA.md`

**Interfaces:**
- Consumes: awaited `WidgetInstanceUpdater.update(appWidgetId)`.
- Produces: configuration completion only after the selected widget instance has rendered.

- [ ] **Step 1: Add a configuration regression test**

Add a test that selects a different day once, presses `widget-config-save` once, and verifies both `RESULT_OK` and the resolved target day. Keep `primaryContentOpensDetailAndFullWidthDateRowOwnsToggle` as the date/icon hit-area contract.

- [ ] **Step 2: Run focused instrumentation**

Run the config and layout test classes on API 36. Expected: all tests pass with one selection/save action.

- [ ] **Step 3: Run complete verification**

Run JVM tests, lint, debug builds, then full instrumentation on API 26/31/36/37.1. Expected: all applicable tests pass.

- [ ] **Step 4: Install on Xiaomi 15 Pro**

Use `adb install -r app-debug.apk`, launch the App, and preserve user data. Confirm package update succeeds; do not run destructive instrumentation on the phone.

- [ ] **Step 5: Commit implementation**

```bash
git add app/src docs/DEVICE_QA.md
git commit -m "fix: make widget interactions immediate"
```
