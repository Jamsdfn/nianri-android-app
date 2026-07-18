# Custom Reminder Time Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let every important day choose one reminder time, defaulting to local 09:00, and use it for day-of plus enabled 14/7/3-day reminders.

**Architecture:** Persist one validated `reminderTimeMinutes` integer on each `ImportantDay` and migrate existing Room rows to `540`. The ViewModel owns the value, the stateless edit screen emits a picker request, and navigation shows Android's system time picker. The scheduler converts the stored minute to local hour/minute for every offset and for same-day catch-up.

**Tech Stack:** Kotlin, Android 26+, Jetpack Compose Material 3, Room 2.8.4, AlarmManager, Robolectric/JUnit 4, AndroidX instrumentation tests.

## Global Constraints

- Each day owns exactly one time; there is no global reminder-time setting.
- Default and Room v1 migration value: `540` (local 09:00).
- Valid range: `0..1439`, at minute precision.
- The time applies to day-of and all enabled 14/7/3-day reminders.
- The picker follows device 12/24-hour preference; saved UI copy is deterministic `HH:mm`.
- Day-of stays mandatory and `reminderMask` retains its current meaning.
- Existing day and widget data must survive Room migration 1→2.

---

## File Structure

- `domain/model/ImportantDay.kt`: domain value and default.
- `data/local/ImportantDayEntity.kt`, `NianriDatabase.kt`: persisted column and migration.
- `data/ImportantDayRepository.kt`, `AppContainer.kt`: validation, mapping, production migration registration.
- `ui/edit/EditDayViewModel.kt`: edit/load/save state.
- `ui/edit/EditDayScreen.kt`, `ui/NianriNavHost.kt`: time control and system picker.
- `reminder/AndroidReminderScheduler.kt`: per-day alarm time.
- `ui/detail/DetailViewModel.kt`: complete reminder summary.
- Existing unit/instrumentation tests plus a new migration test: regression coverage.

---

### Task 1: Persist and migrate reminder time

**Files:**
- Modify: `app/src/main/java/com/nianri/app/domain/model/ImportantDay.kt`
- Modify: `app/src/main/java/com/nianri/app/data/local/ImportantDayEntity.kt`
- Modify: `app/src/main/java/com/nianri/app/data/local/NianriDatabase.kt`
- Modify: `app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt`
- Modify: `app/src/main/java/com/nianri/app/AppContainer.kt`
- Modify: `app/src/androidTest/java/com/nianri/app/data/RepositoryTest.kt`
- Create: `app/src/androidTest/java/com/nianri/app/data/local/NianriDatabaseMigrationTest.kt`
- Create (KSP output): `app/schemas/com.nianri.app.data.local.NianriDatabase/2.json`

**Interfaces:**
- Produces: `DEFAULT_REMINDER_TIME_MINUTES: Int = 540`.
- Produces: `ImportantDay.reminderTimeMinutes: Int`.
- Produces: `NianriDatabase.MIGRATION_1_2: Migration`.

- [ ] **Step 1: Write failing Repository tests**

Update the Repository round-trip fixture and helper to use a custom value, then add invalid-value coverage:

```kotlin
val id = days.save(day(
    name = "妈妈生日",
    reminders = setOf(14, 3),
    reminderTimeMinutes = 8 * 60 + 35,
))
assertEquals(
    day(
        id = id,
        name = "妈妈生日",
        reminders = setOf(14, 3),
        reminderTimeMinutes = 8 * 60 + 35,
    ),
    days.get(id),
)

@Test
fun invalidReminderTimeIsRejected() = runBlocking {
    assertFailsWith<IllegalArgumentException> {
        days.save(day(name = "无效提醒", reminderTimeMinutes = 1440))
    }
}
```

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.data.RepositoryTest
```

Expected: Kotlin compilation fails on the missing `reminderTimeMinutes` API.

- [ ] **Step 3: Add domain, entity, validation, and mappings**

In `ImportantDay.kt`:

```kotlin
const val DEFAULT_REMINDER_TIME_MINUTES = 9 * 60

data class ImportantDay(
    val id: Long = 0,
    val name: String,
    val basis: CalendarSystem,
    val month: Int,
    val day: Int,
    val appDisplay: CalendarSystem,
    val reminders: Set<Int> = setOf(14, 7, 3),
    val reminderTimeMinutes: Int = DEFAULT_REMINDER_TIME_MINUTES,
    val isPinned: Boolean = false,
)
```

Add the Room column to `ImportantDayEntity` (and import `androidx.room.ColumnInfo`) so generated schema validation agrees with the migration default:

```kotlin
@ColumnInfo(defaultValue = "540")
val reminderTimeMinutes: Int,
```

In Repository validation and both mappings add:

```kotlin
require(day.reminderTimeMinutes in 0 until 24 * 60) {
    "Reminder time must be between 00:00 and 23:59"
}

reminderTimeMinutes = reminderTimeMinutes,
```

- [ ] **Step 4: Add and register migration 1→2**

Update `NianriDatabase` to version 2 and add:

```kotlin
companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE important_days " +
                    "ADD COLUMN reminderTimeMinutes INTEGER NOT NULL DEFAULT 540",
            )
        }
    }
}
```

Register it in `AppContainer`:

```kotlin
Room.databaseBuilder(
    applicationContext,
    NianriDatabase::class.java,
    "nianri.db",
)
    .addMigrations(NianriDatabase.MIGRATION_1_2)
    .build()
```

- [ ] **Step 5: Write migration preservation test**

Create `NianriDatabaseMigrationTest.kt` with a `MigrationTestHelper`. Insert a complete v1 day row, run `MIGRATION_1_2`, then assert preserved values and the default:

```kotlin
helper.createDatabase(TEST_DB, 1).apply {
    execSQL(
        "INSERT INTO important_days " +
            "(id,name,basis,month,day,appDisplay,reminderMask,isPinned,createdAt,updatedAt) " +
            "VALUES (42,'妈妈生日','SOLAR',8,6,'SOLAR',5,1,100,200)",
    )
    close()
}

helper.runMigrationsAndValidate(
    TEST_DB, 2, true, NianriDatabase.MIGRATION_1_2,
).query("SELECT * FROM important_days WHERE id = 42").use { cursor ->
    check(cursor.moveToFirst())
    assertEquals("妈妈生日", cursor.getString(cursor.getColumnIndexOrThrow("name")))
    assertEquals(5, cursor.getInt(cursor.getColumnIndexOrThrow("reminderMask")))
    assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isPinned")))
    assertEquals(540, cursor.getInt(cursor.getColumnIndexOrThrow("reminderTimeMinutes")))
}
```

Declare the exact Room 2.8.4 helper and database name as:

```kotlin
@get:Rule
val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    NianriDatabase::class.java,
)

private companion object {
    const val TEST_DB = "reminder-time-migration"
}
```

- [ ] **Step 6: Verify GREEN and schema generation**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.data.RepositoryTest,com.nianri.app.data.local.NianriDatabaseMigrationTest
```

Expected: both classes pass and `app/schemas/com.nianri.app.data.local.NianriDatabase/2.json` contains a non-null `reminderTimeMinutes` column with default `540`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/nianri/app/domain/model/ImportantDay.kt \
  app/src/main/java/com/nianri/app/data/local/ImportantDayEntity.kt \
  app/src/main/java/com/nianri/app/data/local/NianriDatabase.kt \
  app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt \
  app/src/main/java/com/nianri/app/AppContainer.kt \
  app/src/androidTest/java/com/nianri/app/data/RepositoryTest.kt \
  app/src/androidTest/java/com/nianri/app/data/local/NianriDatabaseMigrationTest.kt \
  app/schemas/com.nianri.app.data.local.NianriDatabase/2.json
git commit -m "feat: persist custom reminder times"
```

---

### Task 2: Edit, load, and save ViewModel state

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/edit/EditDayViewModel.kt`
- Modify: `app/src/test/java/com/nianri/app/ui/edit/EditDayViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1's domain field and default.
- Produces: `EditDayUiState.reminderTimeMinutes`.
- Produces: `setReminderTime(hour: Int, minute: Int)`; invalid input leaves state unchanged.

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test
fun `new day defaults to nine and selected reminder time is saved`() {
    val viewModel = viewModel()
    assertEquals(9 * 60, viewModel.uiState.value.reminderTimeMinutes)
    viewModel.setName("妈妈生日")
    viewModel.setReminderTime(8, 35)
    viewModel.save()
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertEquals(8 * 60 + 35, saved.single().reminderTimeMinutes)
}

@Test
fun `editing loads existing reminder time`() {
    val viewModel = viewModel(existing = day(
        id = 42,
        reminderTimeMinutes = 20 * 60 + 5,
    ))
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertEquals(20 * 60 + 5, viewModel.uiState.value.reminderTimeMinutes)
}

@Test
fun `invalid reminder picker values do not change state`() {
    val viewModel = viewModel()
    viewModel.setReminderTime(24, 0)
    viewModel.setReminderTime(9, 60)
    assertEquals(9 * 60, viewModel.uiState.value.reminderTimeMinutes)
}
```

Extend the local `day(...)` helper with `reminderTimeMinutes`.

- [ ] **Step 2: Verify RED**

Run `./gradlew testDebugUnitTest --tests '*EditDayViewModelTest'`.

Expected: missing state property and method compilation failures.

- [ ] **Step 3: Implement state and mappings**

Add:

```kotlin
val reminderTimeMinutes: Int = DEFAULT_REMINDER_TIME_MINUTES,

fun setReminderTime(hour: Int, minute: Int) {
    if (hour !in 0..23 || minute !in 0..59) return
    mutate { copy(reminderTimeMinutes = hour * 60 + minute) }
}
```

Pass `reminderTimeMinutes = reminderTimeMinutes` in both `toImportantDay()` and `toUiState()`.

- [ ] **Step 4: Verify GREEN**

Run `./gradlew testDebugUnitTest --tests '*EditDayViewModelTest'`.

Expected: all focused tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nianri/app/ui/edit/EditDayViewModel.kt \
  app/src/test/java/com/nianri/app/ui/edit/EditDayViewModelTest.kt
git commit -m "feat: edit per-day reminder time"
```

---

### Task 3: Schedule all offsets at the stored time

**Files:**
- Modify: `app/src/main/java/com/nianri/app/reminder/AndroidReminderScheduler.kt`
- Modify: `app/src/test/java/com/nianri/app/reminder/AndroidReminderSchedulerTest.kt`

**Interfaces:**
- Consumes: `ImportantDay.reminderTimeMinutes`.
- Preserves: `ReminderScheduler`, offsets, PendingIntent identity, and permission behavior.

- [ ] **Step 1: Write failing scheduler tests**

Change the existing four-alarm fixture to `reminderTimeMinutes = 8 * 60 + 35` and expected `atTime(8, 35)`. Add the catch-up boundary:

```kotlin
@Test
fun `same day catch up uses custom reminder time boundary`() = runBlocking {
    records = listOf(day(
        month = 7,
        date = 14,
        reminders = emptySet(),
        reminderTimeMinutes = 10 * 60 + 30,
    ))
    val dispatched = mutableListOf<Intent>()
    val before = Clock.fixed(Instant.parse("2026-07-14T02:29:00Z"), zone)
    assertEquals(ReminderScheduleResult.Scheduled(1), scheduler(before, dispatched::add).replace(42))
    assertTrue(dispatched.isEmpty())
    assertEquals(
        LocalDate.of(2026, 7, 14).atTime(10, 30).atZone(zone).toInstant().toEpochMilli(),
        alarms.scheduledAlarms.single().triggerAtTime,
    )

    val atBoundary = Clock.fixed(Instant.parse("2026-07-14T02:30:00Z"), zone)
    assertEquals(
        ReminderScheduleResult.Scheduled(0),
        scheduler(atBoundary, dispatched::add).replace(42),
    )
    assertEquals(
        listOf(0),
        dispatched.map { it.getIntExtra(ReminderReceiver.EXTRA_OFFSET, -1) },
    )
    assertTrue(alarms.scheduledAlarms.isEmpty())
}
```

Extend the test `day(...)` helper with the new value. Retain the existing default-time test as 09:00 regression coverage.

- [ ] **Step 2: Verify RED**

Run `./gradlew testDebugUnitTest --tests '*AndroidReminderSchedulerTest'`.

Expected: expected custom instants differ from fixed 09:00 actual values.

- [ ] **Step 3: Implement custom local time**

In `replace`:

```kotlin
val reminderTime = LocalTime.of(
    day.reminderTimeMinutes / 60,
    day.reminderTimeMinutes % 60,
)
```

Replace `.atTime(9, 0)` with `.atTime(reminderTime)` and add the `LocalTime` import. Do not change offset filtering or immediate-dispatch conditions.

- [ ] **Step 4: Verify GREEN**

Run `./gradlew testDebugUnitTest --tests '*AndroidReminderSchedulerTest'`.

Expected: all scheduler tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nianri/app/reminder/AndroidReminderScheduler.kt \
  app/src/test/java/com/nianri/app/reminder/AndroidReminderSchedulerTest.kt
git commit -m "feat: schedule reminders at custom time"
```

---

### Task 4: Add system picker and complete detail copy

**Files:**
- Modify: `app/src/main/java/com/nianri/app/ui/edit/EditDayScreen.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/detail/DetailViewModel.kt`
- Modify: `app/src/androidTest/java/com/nianri/app/ui/EditDayScreenTest.kt`
- Modify: `app/src/test/java/com/nianri/app/ui/detail/DetailViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 state and setter.
- Produces: `EditDayScreen(..., onPickReminderTime: () -> Unit, ...)`.
- Produces: `reminderSummary(reminders: Set<Int>, reminderTimeMinutes: Int)`.

- [ ] **Step 1: Write failing screen test**

```kotlin
@Test
fun customReminderTimeIsShownAndRequestsSystemPicker() {
    var requests = 0
    var state by mutableStateOf(
        state().copy(reminderTimeMinutes = 8 * 60 + 35),
    )
    compose.setContent {
        EditDayScreen(
            state = state,
            widgetReferences = 0,
            onBack = {}, onNameChange = {}, onBasisChange = {},
            onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
            onToggleReminder = {},
            onPickReminderTime = {
                requests += 1
                state = state.copy(reminderTimeMinutes = 20 * 60 + 5)
            },
            onPinnedChange = {}, onSave = {}, onDelete = {},
        )
    }
    compose.onNodeWithText("提醒时间 08:35 · 固定开启")
        .performScrollTo().performClick()
    compose.runOnIdle { assertEquals(1, requests) }
    compose.onNodeWithText("提醒时间 20:05 · 固定开启").assertIsDisplayed()
}
```

Change the existing mandatory reminder assertion to `提醒时间 09:00 · 固定开启`.

- [ ] **Step 2: Write failing detail-copy tests**

Update current expectations and add:

```kotlin
assertEquals("当天 09:00；提前 14、3 天", state.reminderSummary)
assertEquals("当天 08:35", reminderSummary(emptySet(), 8 * 60 + 35))
assertEquals(
    "当天 08:35；提前 14、7 天",
    reminderSummary(setOf(14, 7), 8 * 60 + 35),
)
```

- [ ] **Step 3: Verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*DetailViewModelTest'
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.EditDayScreenTest
```

Expected: detail copy differs and the screen callback/API is missing.

- [ ] **Step 4: Render a clickable zero-padded time**

Add `onPickReminderTime: () -> Unit` after `onToggleReminder` and replace fixed copy with:

```kotlin
OutlinedButton(onClick = onPickReminderTime, modifier = Modifier.fillMaxWidth()) {
    Text(
        "提醒时间 %02d:%02d · 固定开启".format(
            state.reminderTimeMinutes / 60,
            state.reminderTimeMinutes % 60,
        ),
    )
}
```

Pass an empty callback in Preview and unaffected screen tests.

- [ ] **Step 5: Show Android's time picker**

In `NianriNavHost`, import `android.app.TimePickerDialog` and `android.text.format.DateFormat`, then pass:

```kotlin
onPickReminderTime = {
    TimePickerDialog(
        context,
        { _, hour, minute -> editViewModel.setReminderTime(hour, minute) },
        state.reminderTimeMinutes / 60,
        state.reminderTimeMinutes % 60,
        DateFormat.is24HourFormat(context),
    ).show()
},
```

- [ ] **Step 6: Generate complete detail summary**

Call the helper with `currentDay.reminderTimeMinutes` and replace it with:

```kotlin
internal fun reminderSummary(
    reminders: Set<Int>,
    reminderTimeMinutes: Int,
): String {
    val time = "%02d:%02d".format(
        reminderTimeMinutes / 60,
        reminderTimeMinutes % 60,
    )
    val ordered = listOf(14, 7, 3).filter { it in reminders }
    return if (ordered.isEmpty()) "当天 $time"
    else "当天 $time；提前 ${ordered.joinToString("、")} 天"
}
```

- [ ] **Step 7: Verify GREEN**

Run the two commands from Step 3 again.

Expected: focused unit and Compose tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/nianri/app/ui/edit/EditDayScreen.kt \
  app/src/main/java/com/nianri/app/ui/NianriNavHost.kt \
  app/src/main/java/com/nianri/app/ui/detail/DetailViewModel.kt \
  app/src/androidTest/java/com/nianri/app/ui/EditDayScreenTest.kt \
  app/src/test/java/com/nianri/app/ui/detail/DetailViewModelTest.kt
git commit -m "feat: choose and show reminder time"
```

---

### Task 5: Full verification

**Files:**
- Verify: all Task 1–4 files.
- Verify: `app/schemas/com.nianri.app.data.local.NianriDatabase/2.json`.

**Interfaces:**
- Consumes: all earlier task outputs.
- Produces: a clean feature ready for completion review.

- [ ] **Step 1: Run all JVM unit tests**

Run `./gradlew testDebugUnitTest`.

Expected: `BUILD SUCCESSFUL` and no failed tests.

- [ ] **Step 2: Run all instrumentation tests**

Run `./gradlew connectedDebugAndroidTest`.

Expected: `BUILD SUCCESSFUL`. If no device is connected, report that exact limitation and do not claim instrumentation verification.

- [ ] **Step 3: Check schema and diff hygiene**

```bash
rg -n 'version|reminderTimeMinutes|defaultValue' \
  app/schemas/com.nianri.app.data.local.NianriDatabase/2.json
git diff --check
git status --short
```

Expected: schema version 2, non-null default `540`, no whitespace errors, only intentional changes.

- [ ] **Step 4: Correct any discovered regression test-first**

For each discovered defect, add one focused failing test, run it to confirm the expected failure, make the minimal production change, rerun focused and full suites, then commit the exact test and production files with:

```bash
git commit -m "fix: complete custom reminder time verification"
```

If no correction is needed, do not create an empty commit.
