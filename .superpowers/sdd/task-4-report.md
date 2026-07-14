# Task 4 report: occurrence-backed application state and mutation coordination

## Scope implemented

- Added `DayCardModel` and `DayListProjector`, backed by `ImportantDayRepository`, `DateOccurrenceCalculator`, `CalendarConverter`, and an injected `Clock`.
- Projected flows sort pinned records first, ready records before unavailable records, then by occurrence solar date and name.
- Calculation and display-conversion exceptions project to `DayCardModel.Unavailable` rather than exposing a false countdown.
- Added `DayMutationCoordinator`, `ReminderScheduler`, and `WidgetUpdater` contracts with the required save/delete side-effect ordering.
- Added manual lazy application wiring through `AppContainer` and exposed it from `NianriApplication`; no dependency-injection framework was added.

## TDD evidence

### RED

Command:

```text
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests '*DayMutationCoordinatorTest' --tests '*DayListProjectorTest'
```

Observed failure: unit-test compilation failed on unresolved `DayMutationCoordinator`, `DayListProjector`, `DayCardModel`, `ReminderScheduler`, and `WidgetUpdater`, confirming the tests exercised the missing Task 4 surface.

### GREEN

The same focused command completed successfully after implementation. Result: 5 focused tests, 0 failures.

### Full unit suite

Command:

```text
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`; 19 tests, 0 skipped, 0 failures, 0 errors.

`git diff --check` also completed without errors.

## Self-review

- Confirmed save persists before reminder replacement and widget refresh.
- Confirmed delete cancels reminders while the record still exists, then deletes it and refreshes widgets.
- Confirmed display conversion is performed only after occurrence calculation, so display choice cannot change occurrence date or days remaining.
- Narrowed unavailable fallback handling to `Exception`, avoiding accidental swallowing of JVM `Error` conditions.
- Robolectric tests are pinned to API 35 because API 36 requires Java 21 while the project test runtime is Java 17.

## Deferred wiring

The brief's later platform implementations and view models do not exist at the initial Task 4 commit. The follow-up review below supersedes the initial no-op adapter decision.

## Review follow-up

### Explicit plan resolution

The user selected Option A: Task 4 does **not** create placeholder ViewModel factories. Each factory will be wired alongside its real ViewModel in Tasks 5 and 6.

The initial silent no-op reminder and widget bindings were replaced with named fail-fast deferred bindings. They throw a clear `IllegalStateException` identifying the unwired adapter until Tasks 7-9 install the real Android adapters.

### Follow-up RED

Exact command:

```text
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests '*AppContainerTest' --tests '*DayListProjectorTest' --tests '*DayMutationCoordinatorTest'
```

Result: `BUILD FAILED` during test compilation. The expected unresolved references were `DeferredReminderScheduler`, `DeferredWidgetUpdater`, `CurrentSystemZoneClock`, `CalendarCalculationException`, and `CalendarConversionException`.

### Follow-up GREEN

Exact focused command:

```text
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests '*AppContainerTest' --tests '*DayListProjectorTest' --tests '*DayMutationCoordinatorTest'
```

Result: `BUILD SUCCESSFUL`; 9 focused tests, 0 failures.

Exact full-suite command:

```text
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`; 23 tests, 0 skipped, 0 failures, 0 errors.

### Follow-up behavior verified

- The production container's deferred reminder/widget adapters fail fast instead of silently reporting successful side effects.
- `CurrentSystemZoneClock.getZone()` resolves the current supplied system zone on every projection; one live projector changes its computed `today` after a zone change.
- Known `CalendarOperationException` and `DateTimeException` failures produce `Unavailable`.
- An unrelated `NullPointerException` propagates rather than being hidden as unavailable.
