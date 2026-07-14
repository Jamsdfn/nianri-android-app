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

The brief's later platform implementations and view models do not exist at this commit. `AppContainer` therefore supplies lazy no-op reminder/widget adapters for now; Tasks 7 and 8 can replace them with Android implementations. View-model factories will be added alongside the view models in Tasks 5 and 6.
