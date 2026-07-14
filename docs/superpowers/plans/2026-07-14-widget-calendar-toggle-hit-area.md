# Widget Calendar Toggle Hit Area Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the full bottom date row of both Nianri widgets toggle the solar/lunar display reliably while the upper content area opens the App detail.

**Architecture:** Replace the nested full-card detail action plus text-only toggle action with two sibling Glance click targets. The upper content target owns detail navigation; the full-width bottom row owns `ToggleWidgetCalendarAction`, so generated RemoteViews never need to resolve overlapping actions.

**Tech Stack:** Kotlin, Android Glance 1.1.1, RemoteViews, AndroidX instrumentation tests, Gradle, ADB.

## Global Constraints

- The switch changes display calendar only and never changes the countdown basis.
- Apply the same interaction rule to the 2×1 and 2×2 widgets.
- Keep current widget dimensions, colors, copy, and information density.
- Preserve the API 26 large-font rule that hides the 2×1 second row.
- Validate on API 26, 31, 36, and 37.1 plus Xiaomi 15 Pro.

---

### Task 1: Split Widget Detail and Toggle Hit Targets

**Files:**
- Modify: `app/src/androidTest/java/com/nianri/app/widget/WidgetLayoutTest.kt`
- Modify: `app/src/main/java/com/nianri/app/widget/NianriWidgetSurface.kt`

**Interfaces:**
- Consumes: `ToggleWidgetCalendarAction`, `WidgetActionIntents.detail(Context, Long)`, and existing `WidgetModel.Content` rendering.
- Produces: two non-overlapping RemoteViews click targets for each visible content widget: a detail target around primary content and a full-width toggle target around the bottom date row.

- [ ] **Step 1: Replace the existing click-target test with a failing sibling-target contract**

In `WidgetLayoutTest.kt`, replace `fullCardBackgroundOpensDetailWhileDateRegionKeepsItsOwnClickTarget` with a test that renders both sizes, resolves the clickable ancestors of the name/countdown and date-control text, and asserts:

```kotlin
@Test
fun primaryContentOpensDetailAndFullWidthDateRowOwnsToggle() = runBlocking {
    listOf(true to DpSize(110.dp, 40.dp), false to DpSize(110.dp, 110.dp)).forEach { (wide, size) ->
        val view = render(size, wide = wide, model = content)
        val fullBounds = Rect(0, 0, view.measuredWidth, view.measuredHeight)
        val primaryText = view.textViews().first { it.text.toString() == content.name }
        val dateText = view.textViews().first { "↻" in it.text.toString() }
        val primaryTarget = primaryText.closestClickableAncestor()
        val dateTarget = dateText.closestClickableAncestor()

        assertNotNull("primary content lacks detail target", primaryTarget)
        assertNotNull("date row lacks toggle target", dateTarget)
        assertNotEquals("detail and toggle targets must be siblings", primaryTarget, dateTarget)
        assertFalse(
            "content widgets must not retain an overlapping full-card action",
            view.allViews().any { it.hasOnClickListeners() && it.boundsIn(view) == fullBounds },
        )
        val horizontalInset = px(if (wide) 7 else 10)
        assertTrue(
            "date toggle must span the padded card width",
            dateTarget!!.boundsIn(view).width() >= view.measuredWidth - horizontalInset * 2,
        )
        assertTrue(
            "date toggle must include vertical touch padding",
            dateTarget.height > dateText.height,
        )
    }
}
```

- [ ] **Step 2: Run the focused instrumentation test and verify RED**

Run on the connected API 36 emulator:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.widget.WidgetLayoutTest#primaryContentOpensDetailAndFullWidthDateRowOwnsToggle
```

Expected: FAIL because the current full-card target still exists and the date action covers only its text node.

- [ ] **Step 3: Implement two sibling click regions in the wide widget**

In `WideContent`, remove `.clickable(detailAction)` from the outer `Column`. Bind `detailAction` to the first row and bind the callback to the entire second row:

```kotlin
val toggleAction = actionRunCallback<ToggleWidgetCalendarAction>()
Column(
    modifier = modifier.padding(horizontal = 7.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(
        modifier = (if (hideSecondRow) GlanceModifier.fillMaxSize() else GlanceModifier.fillMaxWidth())
            .clickable(detailAction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // existing name and countdown texts
    }
    if (!hideSecondRow) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(toggleAction)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // existing basis, spacer, and date-control texts
        }
    }
}
```

Remove the callback from the trailing `Text`; the row owns it. The hidden-second-row API 26 large-font branch gives its sole row the detail action across all available inner bounds.

- [ ] **Step 4: Implement two sibling click regions in the square widget**

In `SquareContent`, remove `.clickable(detailAction)` from the outer `Column`. Put name, basis, countdown, and flexible spacer inside a weighted full-width `Column` with the detail action. Bind the full-width bottom `Row` to the toggle action and remove the text-only callback:

```kotlin
val toggleAction = actionRunCallback<ToggleWidgetCalendarAction>()
Column(modifier = modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
    Column(
        modifier = GlanceModifier.defaultWeight().fillMaxWidth().clickable(detailAction),
    ) {
        // existing name, basis, spacer, and countdown texts
        Spacer(GlanceModifier.defaultWeight())
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(toggleAction)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // existing date label, spacer, and date-control texts
    }
}
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.widget.WidgetLayoutTest
```

Expected: all `WidgetLayoutTest` tests PASS, including minimum size and large-font bounds.

- [ ] **Step 6: Run the full verification matrix**

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Expected: unit tests, lint, assembly, and all applicable instrumentation tests PASS on the four connected emulators.

- [ ] **Step 7: Commit the tested implementation**

```bash
git add app/src/main/java/com/nianri/app/widget/NianriWidgetSurface.kt \
  app/src/androidTest/java/com/nianri/app/widget/WidgetLayoutTest.kt
git commit -m "fix: enlarge widget calendar toggle target"
```

- [ ] **Step 8: Reinstall and validate on Xiaomi 15 Pro**

Build and install only to the paired phone serial:

```bash
./gradlew assembleDebug
adb -s adb-94cbaaa0-SEIjCm._adb-tls-connect._tcp install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`. On both widgets, tapping the left, center, and right portions of the bottom date row toggles display; tapping the upper content opens the App detail.
