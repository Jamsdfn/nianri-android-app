# 「念日」Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and verify the native Android “念日” app, including annual solar/lunar countdowns, local reminders, and independently configurable Xiaomi-compatible `2×1` and `2×2` widgets.

**Architecture:** Use one Android application module with focused domain, data, reminder, widget, and Compose UI packages. Pure date rules stay in testable Kotlin classes; Room owns persistent data; coordinator classes apply save/delete side effects to alarms and widgets; Glance renders two widget providers from the same presentation model.

**Tech Stack:** Kotlin 2.3.10, JDK 17, AGP 9.2.1, Gradle 9.4.1, Compose BOM 2026.06.00, Room 2.8.4, Glance 1.1.1, WorkManager 2.11.2, AlarmManager, Android ICU, JUnit, Robolectric, Compose UI tests.

## Global Constraints

- Application ID and namespace: `com.nianri.app`; user-facing name: `念日`.
- `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`; separately test forward compatibility on an Android 17/API 37 emulator.
- Use stable dependencies only; do not use Glance 1.2 RC or 1.3 alpha.
- All records repeat yearly; no login, cloud sync, network permission, sharing, or one-time dates.
- The saved basis calendar controls occurrence and reminders; every solar/lunar switch changes display text only.
- Notifications fire at local 09:00 for independently selected 14-, 7-, and 3-day offsets.
- The `2×1` layout must fit the user-provided Xiaomi 15 Pro reference density and reserve launcher space for the `念日` label.
- All implementation steps follow test-driven development and end in a focused Git commit.

---

## Planned File Structure

```text
app/src/main/java/com/nianri/app/
  NianriApplication.kt                 # App container and worker setup
  MainActivity.kt                      # Compose host and deep links
  AppContainer.kt                      # Manual dependency wiring
  domain/model/ImportantDay.kt         # Domain enums and value types
  domain/calendar/CalendarConverter.kt # Solar/lunar display conversion contract
  domain/calendar/IcuCalendarConverter.kt
  domain/calendar/DateOccurrenceCalculator.kt
  data/local/ImportantDayEntity.kt
  data/local/WidgetPreferenceEntity.kt
  data/local/ImportantDayDao.kt
  data/local/WidgetPreferenceDao.kt
  data/local/NianriDatabase.kt
  data/ImportantDayRepository.kt
  data/WidgetRepository.kt
  data/UiPreferences.kt
  domain/DayMutationCoordinator.kt     # Save/delete transaction side effects
  domain/DayListProjector.kt
  reminder/ReminderScheduler.kt
  reminder/AndroidReminderScheduler.kt
  reminder/ReminderReceiver.kt
  reminder/SystemChangeReceiver.kt
  reminder/ReminderAuditWorker.kt
  reminder/ReminderPermissionState.kt
  widget/WidgetPresentation.kt
  widget/NianriWideWidget.kt
  widget/NianriSquareWidget.kt
  widget/NianriWidgetReceivers.kt
  widget/ToggleWidgetCalendarAction.kt
  widget/WidgetConfigActivity.kt
  ui/NianriNavHost.kt
  ui/theme/Color.kt
  ui/theme/Theme.kt
  ui/home/HomeViewModel.kt
  ui/home/HomeScreen.kt
  ui/edit/EditDayViewModel.kt
  ui/edit/EditDayScreen.kt
  ui/detail/DetailViewModel.kt
  ui/detail/DetailScreen.kt
app/src/test/java/com/nianri/app/        # Domain, repository, scheduler, VM tests
app/src/androidTest/java/com/nianri/app/ # Room, Compose, widget-config flows
app/src/main/res/xml/                    # Two widget provider definitions
app/schemas/                             # Room exported schemas
docs/DEVELOPMENT.md                      # Reproducible local setup/build commands
docs/DEVICE_QA.md                        # Xiaomi and Android-version checklist
```

---

### Task 1: Install the toolchain and create a buildable Android shell

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/nianri/app/NianriApplication.kt`
- Create: `app/src/main/java/com/nianri/app/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/test/java/com/nianri/app/SmokeTest.kt`
- Create: `docs/DEVELOPMENT.md`

**Interfaces:**
- Consumes: Apple Silicon macOS with no existing Java or Android SDK.
- Produces: a reproducible JDK 17/SDK/Gradle environment and a debug-installable `com.nianri.app` shell.

- [ ] **Step 1: Install Android Studio and command-line prerequisites**

Run with user approval because these commands download software and write outside the repository:

```bash
brew install --cask android-studio
brew install wget
```

Expected: `/Applications/Android Studio.app` exists and its bundled JDK reports Java 17 or newer.

- [ ] **Step 2: Install the stable Android SDK packages**

Set the SDK path to `$HOME/Library/Android/sdk`, then use Android Studio's bundled `sdkmanager` or the installed command-line tools:

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" "emulator" "system-images;android-36;google_apis;arm64-v8a"
```

Expected: `adb version`, `sdkmanager --list`, and `emulator -version` succeed; all licenses are accepted.

- [ ] **Step 3: Bootstrap Gradle Wrapper 9.4.1**

Download Gradle and its official checksum, verify it, unpack under `/private/tmp`, then run:

```bash
curl -L -o /private/tmp/gradle-9.4.1-bin.zip https://services.gradle.org/distributions/gradle-9.4.1-bin.zip
curl -L -o /private/tmp/gradle-9.4.1-bin.zip.sha256 https://services.gradle.org/distributions/gradle-9.4.1-bin.zip.sha256
cd /private/tmp
awk '{print $1 "  gradle-9.4.1-bin.zip"}' gradle-9.4.1-bin.zip.sha256 | shasum -a 256 -c -
unzip -q gradle-9.4.1-bin.zip
/private/tmp/gradle-9.4.1/bin/gradle wrapper --gradle-version 9.4.1 --distribution-type bin
```

Expected: `./gradlew --version` reports Gradle 9.4.1 and JVM 17.

- [ ] **Step 4: Write the failing build smoke test**

Create `SmokeTest.kt`:

```kotlin
package com.nianri.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun appName_isNianri() {
        assertEquals("念日", BuildConfig.APP_NAME)
    }
}
```

Run: `./gradlew testDebugUnitTest`

Expected: FAIL because no Android module or `BuildConfig.APP_NAME` exists.

- [ ] **Step 5: Create the minimal Gradle project**

Use these pinned plugins and Android values:

```kotlin
// root build.gradle.kts
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
}
```

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.nianri.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.nianri.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "APP_NAME", "\"念日\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
```

`MainActivity` renders `Text("念日")`; the manifest declares `NianriApplication` and the launcher activity with no network permission.

- [ ] **Step 6: Verify the shell**

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Expected: all tasks pass and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Document and commit the environment shell**

`docs/DEVELOPMENT.md` must record `JAVA_HOME`, `ANDROID_HOME`, SDK packages, wrapper version, build/test commands, emulator creation, and Xiaomi USB/wireless debugging commands.

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle app docs/DEVELOPMENT.md
git commit -m "build: bootstrap Nianri Android project"
```

---

### Task 2: Implement annual solar and lunar occurrence rules

**Files:**
- Create: `app/src/main/java/com/nianri/app/domain/model/ImportantDay.kt`
- Create: `app/src/main/java/com/nianri/app/domain/calendar/CalendarConverter.kt`
- Create: `app/src/main/java/com/nianri/app/domain/calendar/IcuCalendarConverter.kt`
- Create: `app/src/main/java/com/nianri/app/domain/calendar/DateOccurrenceCalculator.kt`
- Create: `app/src/test/java/com/nianri/app/domain/calendar/DateOccurrenceCalculatorTest.kt`
- Create: `app/src/test/java/com/nianri/app/domain/calendar/IcuCalendarConverterTest.kt`

**Interfaces:**
- Consumes: `java.time.LocalDate`, Android ICU available from API 24.
- Produces: `DateOccurrenceCalculator.next(day, today): Occurrence` and `CalendarConverter.displayDate(solarDate, calendarSystem): DisplayDate`.

- [ ] **Step 1: Define domain values and failing solar tests**

```kotlin
enum class CalendarSystem { SOLAR, LUNAR }
enum class DateAdjustment { NON_LEAP_YEAR, SHORT_LUNAR_MONTH }
data class ImportantDay(
    val id: Long = 0,
    val name: String,
    val basis: CalendarSystem,
    val month: Int,
    val day: Int,
    val appDisplay: CalendarSystem,
    val reminders: Set<Int> = setOf(14, 7, 3),
    val isPinned: Boolean = false,
)
data class LunarDate(val month: Int, val day: Int, val isLeapMonth: Boolean)
data class DisplayDate(val calendar: CalendarSystem, val text: String)
data class Occurrence(
    val solarDate: LocalDate,
    val daysRemaining: Long,
    val adjustment: DateAdjustment? = null,
)
```

Tests must assert: today returns 0, a past date rolls to next year, future date stays this year, leap-year February 29 stays February 29, and non-leap February 29 becomes February 28 with `NON_LEAP_YEAR`.

- [ ] **Step 2: Run solar tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests '*DateOccurrenceCalculatorTest'`

Expected: FAIL because `DateOccurrenceCalculator` is missing.

- [ ] **Step 3: Implement solar occurrence calculation**

```kotlin
class DateOccurrenceCalculator(private val converter: CalendarConverter) {
    fun next(day: ImportantDay, today: LocalDate): Occurrence =
        when (day.basis) {
            CalendarSystem.SOLAR -> nextSolar(day, today)
            CalendarSystem.LUNAR -> nextLunar(day, today)
        }

    private fun solarCandidate(month: Int, day: Int, year: Int): Pair<LocalDate, DateAdjustment?> =
        if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28) to DateAdjustment.NON_LEAP_YEAR
        } else LocalDate.of(year, month, day) to null
}
```

Choose the first candidate on or after `today`; compute days with `ChronoUnit.DAYS.between(today, candidate)`.

- [ ] **Step 4: Write failing ICU conversion and lunar boundary tests**

Use known fixtures such as 2026 Chinese New Year and test that leap-month instances are marked. Add calculator tests for an ordinary lunar month/day, skipping the leap copy, and lunar day 30 falling back to day 29 only when the ordinary month has no day 30.

- [ ] **Step 5: Implement ICU conversion and lunar scan**

`IcuCalendarConverter.lunarFromSolar` sets a `ChineseCalendar` from the Gregorian date at UTC noon and returns month, day, and `ChineseCalendar.IS_LEAP_MONTH`. `displayDate` returns Chinese text for lunar dates and `yyyy年M月d日` for solar dates.

`nextLunar` scans `today..today+420 days`, ignores `isLeapMonth == true`, returns an exact month/day match, and treats an ordinary month day 29 as the fallback for requested day 30 only when the next solar day leaves that ordinary lunar month.

- [ ] **Step 6: Verify all date rules**

Run: `./gradlew testDebugUnitTest --tests '*calendar*'`

Expected: PASS for solar, lunar, leap-year, short-lunar-month, cross-year, and display-only conversion tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/nianri/app/domain app/src/test/java/com/nianri/app/domain
git commit -m "feat: add annual solar and lunar date rules"
```

---

### Task 3: Persist important days and widget selections with Room

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/nianri/app/data/local/ImportantDayEntity.kt`
- Create: `app/src/main/java/com/nianri/app/data/local/WidgetPreferenceEntity.kt`
- Create: `app/src/main/java/com/nianri/app/data/local/ImportantDayDao.kt`
- Create: `app/src/main/java/com/nianri/app/data/local/WidgetPreferenceDao.kt`
- Create: `app/src/main/java/com/nianri/app/data/local/NianriDatabase.kt`
- Create: `app/src/main/java/com/nianri/app/data/ImportantDayRepository.kt`
- Create: `app/src/main/java/com/nianri/app/data/WidgetRepository.kt`
- Create: `app/src/androidTest/java/com/nianri/app/data/RepositoryTest.kt`

**Interfaces:**
- Consumes: domain `ImportantDay` and `CalendarSystem`.
- Produces: `ImportantDayRepository.observeAll`, `get`, `save`, `updateAppDisplay`, `delete`; `WidgetRepository.select`, `toggleDisplay`, `resolve`, `countReferences`.

- [ ] **Step 1: Add Room 2.8.4 dependencies and schema export**

```kotlin
implementation("androidx.room:room-runtime:2.8.4")
implementation("androidx.room:room-ktx:2.8.4")
ksp("androidx.room:room-compiler:2.8.4")
androidTestImplementation("androidx.room:room-testing:2.8.4")
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

- [ ] **Step 2: Write failing repository instrumentation tests**

Tests must prove: insert/update/delete works; setting a second pinned record clears the first in one transaction; list observation emits changes; widget preference persists by `appWidgetId`; deleting a day does not delete its widget preference, so resolution returns `MissingDay`.

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.data.RepositoryTest`

Expected: FAIL because entities and repositories are missing.

- [ ] **Step 3: Implement entities and DAOs**

```kotlin
@Entity(tableName = "important_days")
data class ImportantDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val basis: CalendarSystem,
    val month: Int,
    val day: Int,
    val appDisplay: CalendarSystem,
    val reminderMask: Int,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "widget_preferences")
data class WidgetPreferenceEntity(
    @PrimaryKey val appWidgetId: Int,
    val importantDayId: Long,
    val display: CalendarSystem,
)

class DatabaseConverters {
    @TypeConverter fun calendarToString(value: CalendarSystem): String = value.name
    @TypeConverter fun stringToCalendar(value: String): CalendarSystem = CalendarSystem.valueOf(value)
}

@Dao
interface ImportantDayDao {
    @Query("SELECT * FROM important_days") fun observeAll(): Flow<List<ImportantDayEntity>>
    @Query("SELECT * FROM important_days WHERE id = :id") suspend fun get(id: Long): ImportantDayEntity?
    @Upsert suspend fun upsert(entity: ImportantDayEntity): Long
    @Query("UPDATE important_days SET isPinned = 0 WHERE isPinned = 1") suspend fun clearPinned()
    @Query("DELETE FROM important_days WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface WidgetPreferenceDao {
    @Query("SELECT * FROM widget_preferences WHERE appWidgetId = :id") suspend fun get(id: Int): WidgetPreferenceEntity?
    @Upsert suspend fun upsert(entity: WidgetPreferenceEntity)
    @Query("SELECT COUNT(*) FROM widget_preferences WHERE importantDayId = :dayId") suspend fun countForDay(dayId: Long): Int
}
```

Annotate `NianriDatabase` with `@TypeConverters(DatabaseConverters::class)`. Do not add a foreign key from widget preferences; the intentional stale ID powers the deleted-record state.

- [ ] **Step 4: Implement transactional repositories**

`ImportantDayRepository.save` validates name/month/day, encodes reminders as bits `1`, `2`, `4`, and when pinning calls DAO methods inside `database.withTransaction { clearPinned(); upsert(entity) }`. `observeAll` maps entities to domain objects. `updateAppDisplay(id, display)` updates only `appDisplay`; it must not call the reminder scheduler.

`WidgetRepository.resolve(appWidgetId)` returns `Unconfigured`, `MissingDay`, or `Configured(day, display)`. `countReferences(dayId)` delegates to the DAO count query used by deletion confirmation.

- [ ] **Step 5: Verify Room and exported schema**

Run:

```bash
./gradlew connectedDebugAndroidTest
test -f app/schemas/com.nianri.app.data.local.NianriDatabase/1.json
```

Expected: instrumentation tests pass and schema version 1 JSON exists.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/nianri/app/data app/src/androidTest app/schemas
git commit -m "feat: persist important days and widget choices"
```

---

### Task 4: Build occurrence-backed application state and mutation coordination

**Files:**
- Create: `app/src/main/java/com/nianri/app/AppContainer.kt`
- Modify: `app/src/main/java/com/nianri/app/NianriApplication.kt`
- Create: `app/src/main/java/com/nianri/app/domain/DayMutationCoordinator.kt`
- Create: `app/src/main/java/com/nianri/app/domain/DayListProjector.kt`
- Create: `app/src/main/java/com/nianri/app/reminder/ReminderScheduler.kt`
- Create: `app/src/test/java/com/nianri/app/domain/DayMutationCoordinatorTest.kt`
- Create: `app/src/test/java/com/nianri/app/domain/DayListProjectorTest.kt`

**Interfaces:**
- Consumes: repositories and `DateOccurrenceCalculator`.
- Produces: sorted `DayCardModel` flows and one orchestration entry point for save/delete.

- [ ] **Step 1: Define collaborator contracts and failing tests**

```kotlin
interface ReminderScheduler {
    suspend fun replace(dayId: Long)
    suspend fun cancel(dayId: Long)
    suspend fun rebuildAll()
}
interface WidgetUpdater {
    suspend fun updateAll()
}
sealed interface DayCardModel {
    val day: ImportantDay
    data class Ready(
        override val day: ImportantDay,
        val occurrence: Occurrence,
        val displayedDate: DisplayDate,
    ) : DayCardModel
    data class Unavailable(override val day: ImportantDay) : DayCardModel
}
```

Tests assert pinned-first sorting, then nearest-date sorting; conversion failure produces `Unavailable` instead of a false countdown; save calls repository then reminder replacement and widget update; delete cancels reminders, deletes data, and updates widgets.

- [ ] **Step 2: Run and observe failure**

Run: `./gradlew testDebugUnitTest --tests '*DayMutationCoordinatorTest' --tests '*DayListProjectorTest'`

Expected: FAIL because coordinator and projector do not exist.

- [ ] **Step 3: Implement projector and coordinator**

```kotlin
class DayMutationCoordinator(
    private val days: ImportantDayRepository,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
) {
    suspend fun save(day: ImportantDay): Long {
        val id = days.save(day)
        reminders.replace(id)
        widgets.updateAll()
        return id
    }
    suspend fun delete(id: Long) {
        reminders.cancel(id)
        days.delete(id)
        widgets.updateAll()
    }
}
```

`DayListProjector` accepts an injected `Clock`, calculates each occurrence, converts only the displayed date, and catches conversion failures as `DayCardModel.Unavailable`. It sorts by `!isPinned`, ready-before-unavailable, `solarDate`, then name. Home and detail screens render unavailable models as “日期暂不可用” with an edit action.

- [ ] **Step 4: Wire manual dependencies**

`AppContainer` lazily constructs database, converter, calculator, repositories, scheduler, widget updater, coordinator, and view-model factories. `NianriApplication` exposes one container; no Hilt dependency is added.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew testDebugUnitTest`

Expected: PASS.

```bash
git add app/src/main/java/com/nianri/app app/src/test/java/com/nianri/app/domain
git commit -m "feat: coordinate countdown state changes"
```

---

### Task 5: Implement the night-sky Compose shell and home screen

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/nianri/app/MainActivity.kt`
- Create: `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`
- Create: `app/src/main/java/com/nianri/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/nianri/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/nianri/app/data/UiPreferences.kt`
- Create: `app/src/main/java/com/nianri/app/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/nianri/app/ui/home/HomeScreen.kt`
- Create: `app/src/androidTest/java/com/nianri/app/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `Flow<List<DayCardModel>>` and display preference updates.
- Produces: home navigation callbacks `onAdd`, `onOpen(id)`, `onToggleDisplay(id)`.

- [ ] **Step 1: Add lifecycle and navigation dependencies**

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
implementation("androidx.navigation:navigation-compose:2.9.6")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

- [ ] **Step 2: Write failing Compose tests**

Tests render fixed models and assert: the pinned card appears first; a zero-day occurrence renders “就是今天”; “按农历倒计时” and “日期展示” are visible; switching a card invokes only `onToggleDisplay`; empty state offers “新建重要日子”; the first-visit explanation can be dismissed and remains dismissed after recreating the ViewModel.

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nianri.app.ui.HomeScreenTest`

Expected: FAIL because `HomeScreen` is missing.

- [ ] **Step 3: Implement theme tokens**

```kotlin
val Night950 = Color(0xFF0D0E22)
val Night800 = Color(0xFF222553)
val Violet300 = Color(0xFFC9C2FF)
val Violet500 = Color(0xFF8B7CFF)
val TextPrimary = Color(0xFFF8F8FF)
val TextMuted = Color(0xFFA9ADD1)
```

Use a dark-only Material 3 scheme, 24dp hero corners, 16dp list corners, and no dynamic color so the confirmed design stays consistent.

- [ ] **Step 4: Implement home state and screen**

```kotlin
data class HomeUiState(
    val pinned: DayCardModel? = null,
    val upcoming: List<DayCardModel> = emptyList(),
    val showCalendarExplanation: Boolean = true,
)
```

The hero and list card share a focused `DayCard` composable. Display switches call only `ImportantDayRepository.updateAppDisplay`; the ViewModel immediately reprojects text while occurrence and days remain unchanged. `UiPreferences` stores `calendar_explanation_seen` in private `SharedPreferences("ui_preferences")` so dismissal survives process restarts.

- [ ] **Step 5: Add navigation shell and verify**

Routes: `home`, `edit?dayId={dayId}`, and `detail/{dayId}`. `MainActivity` accepts a detail deep-link extra named `importantDayId` for notifications and widgets.

Run: `./gradlew connectedDebugAndroidTest lintDebug`

Expected: UI tests and lint pass.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/nianri/app/ui app/src/main/java/com/nianri/app/MainActivity.kt app/src/androidTest
git commit -m "feat: add night-sky countdown home"
```

---

### Task 6: Implement create, edit, detail, validation, and delete flows

**Files:**
- Create: `app/src/main/java/com/nianri/app/ui/edit/EditDayViewModel.kt`
- Create: `app/src/main/java/com/nianri/app/ui/edit/EditDayScreen.kt`
- Create: `app/src/main/java/com/nianri/app/ui/detail/DetailViewModel.kt`
- Create: `app/src/main/java/com/nianri/app/ui/detail/DetailScreen.kt`
- Modify: `app/src/main/java/com/nianri/app/ui/NianriNavHost.kt`
- Create: `app/src/test/java/com/nianri/app/ui/edit/EditDayViewModelTest.kt`
- Create: `app/src/androidTest/java/com/nianri/app/ui/EditDayScreenTest.kt`

**Interfaces:**
- Consumes: `DayMutationCoordinator`, repository lookup, calculator, converter.
- Produces: complete record CRUD and special-adjustment explanations.

- [ ] **Step 1: Write failing ViewModel tests**

Test empty-name rejection; basis selection changes which month/day picker is active; default reminders are `{14,7,3}`; all reminders may be disabled; save preview distinguishes basis from display; editing a basis recalculates; delete calls the coordinator.

Run: `./gradlew testDebugUnitTest --tests '*EditDayViewModelTest'`

Expected: FAIL because the ViewModel does not exist.

- [ ] **Step 2: Implement edit state and validation**

```kotlin
data class EditDayUiState(
    val id: Long = 0,
    val name: String = "",
    val basis: CalendarSystem = CalendarSystem.SOLAR,
    val month: Int = 1,
    val day: Int = 1,
    val display: CalendarSystem = CalendarSystem.SOLAR,
    val reminders: Set<Int> = setOf(14, 7, 3),
    val pinned: Boolean = false,
    val preview: DayCardModel.Ready? = null,
    val nameError: String? = null,
)
```

Validation allows solar February 29 and lunar day 30 so annual fallback rules can apply. It rejects blank names, months outside 1..12, solar dates impossible in every year, and lunar days outside 1..30.

- [ ] **Step 3: Write failing screen tests**

Tests assert two clearly numbered sections (“① 倒计时基准”, “② 默认日期展示”), helper copy, mutually exclusive basis selection, three independent reminder chips, preview text, pin toggle, save, delete confirmation, and permission status row. When `WidgetRepository.countReferences(dayId) > 0`, the confirmation copy must be “删除后，相关小部件需要重新选择日子”.

- [ ] **Step 4: Implement edit and detail screens**

Use wheel/dialog month-day selectors, not a Gregorian `DatePicker` for lunar input. The detail screen shows both converted dates, adjustment copy, reminder summary, edit button, and delete confirmation. It queries `WidgetRepository.countReferences(id)` before deletion and uses the widget warning copy when the count is nonzero. It never offers a control that changes basis without entering edit mode.

- [ ] **Step 5: Verify CRUD UI**

Run:

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug
```

Expected: all ViewModel and Compose tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nianri/app/ui app/src/test app/src/androidTest
git commit -m "feat: add important-day editing and details"
```

---

### Task 7: Schedule exact 09:00 reminders and recover after system changes

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/nianri/app/reminder/AndroidReminderScheduler.kt`
- Create: `app/src/main/java/com/nianri/app/reminder/ReminderReceiver.kt`
- Create: `app/src/main/java/com/nianri/app/reminder/SystemChangeReceiver.kt`
- Create: `app/src/main/java/com/nianri/app/reminder/ReminderAuditWorker.kt`
- Create: `app/src/main/java/com/nianri/app/reminder/ReminderPermissionState.kt`
- Create: `app/src/test/java/com/nianri/app/reminder/AndroidReminderSchedulerTest.kt`
- Create: `app/src/test/java/com/nianri/app/reminder/ReminderReceiverTest.kt`

**Interfaces:**
- Consumes: repository records and calculated occurrences.
- Produces: exact alarms, notifications, reboot/time-change recovery, and daily audit.

- [ ] **Step 1: Add WorkManager and manifest capabilities**

```kotlin
implementation("androidx.work:work-runtime:2.11.2")
```

Declare `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and `RECEIVE_BOOT_COMPLETED`; do not declare `INTERNET` or `USE_EXACT_ALARM`. Register receivers for reminder delivery and `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `DATE_CHANGED`, `TIME_SET`, `TIMEZONE_CHANGED`, and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.

- [ ] **Step 2: Write failing scheduler tests**

Using Robolectric shadows, assert each enabled offset schedules a unique `PendingIntent`; trigger time is local 09:00 on `occurrence.solarDate.minusDays(offset)`; past reminder dates are skipped; replacing cancels old requests; missing exact-alarm permission reports `NeedsExactAlarmPermission`; zero selected reminders schedules nothing.

- [ ] **Step 3: Implement request identity and scheduling**

```kotlin
private fun requestCode(dayId: Long, offset: Int): Int =
    (31 * dayId.hashCode() + offset)

private fun triggerAt(date: LocalDate, zone: ZoneId): Long =
    date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
```

Use `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, trigger, pendingIntent)` only after `canScheduleExactAlarms()` on API 31+. Persist no duplicate alarm table; deterministically recreate all request codes from the record.

- [ ] **Step 4: Implement notification delivery**

`ReminderReceiver` loads the day, recalculates occurrence, confirms the received offset still matches today, then posts channel `important_day_reminders`. Copy format: `妈妈生日还有 7 天 · 8月6日`; append special adjustment copy when present. The content intent opens `detail/{id}`.

- [ ] **Step 5: Implement recovery and audit**

`SystemChangeReceiver` calls `rebuildAll()` from `goAsync()` for boot, package replacement, date, time, timezone, and permission-state changes. `ReminderAuditWorker` performs the same idempotent rebuild; enqueue unique daily work with a 24-hour interval and a 2-hour flex window from `NianriApplication`.

- [ ] **Step 6: Implement permission UI state**

Expose `NotNeeded`, `WaitingForNotificationPermission`, `WaitingForExactAlarmPermission`, `Denied`, and `Ready`. The edit screen requests permissions only when at least one reminder is selected and displays a system-settings action after denial or revocation.

- [ ] **Step 7: Verify and commit**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`

Expected: scheduler/receiver tests pass; manifest merger includes only intended permissions.

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/nianri/app/reminder app/src/test/java/com/nianri/app/reminder
git commit -m "feat: schedule resilient important-day reminders"
```

---

### Task 8: Add widget configuration and per-widget display state

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/nianri/app/widget/WidgetConfigActivity.kt`
- Create: `app/src/main/java/com/nianri/app/widget/ToggleWidgetCalendarAction.kt`
- Create: `app/src/main/java/com/nianri/app/widget/WidgetPresentation.kt`
- Create: `app/src/androidTest/java/com/nianri/app/widget/WidgetConfigActivityTest.kt`
- Create: `app/src/test/java/com/nianri/app/widget/WidgetPresentationTest.kt`

**Interfaces:**
- Consumes: `WidgetRepository`, important-day records, calculator/converter.
- Produces: selected `appWidgetId`, independent display state, and `WidgetModel`.

- [ ] **Step 1: Add stable Glance dependencies**

```kotlin
implementation("androidx.glance:glance:1.1.1")
implementation("androidx.glance:glance-appwidget:1.1.1")
```

- [ ] **Step 2: Write failing configuration tests**

Assert configuration starts with `RESULT_CANCELED`; lists all saved days; selection persists by `EXTRA_APPWIDGET_ID`; success returns that ID; empty data offers “新建重要日子”; reconfiguration replaces selection without removing the widget.

- [ ] **Step 3: Implement widget presentation mapping**

```kotlin
sealed interface WidgetModel {
    data object Unconfigured : WidgetModel
    data object MissingDay : WidgetModel
    data object DateUnavailable : WidgetModel
    data class Content(
        val id: Long,
        val name: String,
        val days: Long,
        val basisLabel: String,
        val displayedDate: String,
        val display: CalendarSystem,
    ) : WidgetModel
}
```

The mapper calculates occurrence from basis and converts only `displayedDate`; toggling `display` must leave `days` unchanged in tests. Conversion failure maps to `DateUnavailable`, which renders “日期暂不可用” and opens the record editor.

- [ ] **Step 4: Implement configuration activity and toggle action**

`WidgetConfigActivity` uses the night-sky Compose theme, stores the chosen day, calls both widget providers' update methods, sets the result intent, then finishes. `ToggleWidgetCalendarAction` flips the stored display enum for only the current `AppWidgetId` and updates that instance.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest`

Expected: widget mapping and configuration tests pass.

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/nianri/app/widget app/src/test app/src/androidTest
git commit -m "feat: configure per-widget countdown state"
```

---

### Task 9: Render Xiaomi-sized `2×1` and `2×2` Glance widgets

**Files:**
- Create: `app/src/main/java/com/nianri/app/widget/NianriWideWidget.kt`
- Create: `app/src/main/java/com/nianri/app/widget/NianriSquareWidget.kt`
- Create: `app/src/main/java/com/nianri/app/widget/NianriWidgetReceivers.kt`
- Create: `app/src/main/res/xml/nianri_wide_widget_info.xml`
- Create: `app/src/main/res/xml/nianri_square_widget_info.xml`
- Create: `app/src/main/res/drawable/widget_night_background.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/androidTest/java/com/nianri/app/widget/WidgetLayoutTest.kt`

**Interfaces:**
- Consumes: `WidgetModel` and toggle/detail actions.
- Produces: two discoverable launcher widget providers with configuration and deleted-record recovery.

- [ ] **Step 1: Write failing Glance layout tests**

Render Glance previews/tests at provider minimum bounds: wide `110×40 dp` and square `110×110 dp`. Separately compare device screenshots against the user's measured pixel references (wide card about `356×136 px` in a `920 px`-wide screenshot). Assert wide content has only two rows, the name is single-line ellipsized, days/date are present, and no two-segment control exists. Assert square content contains the full date display control. Assert deleted state contains “这个日子已删除” and “选择其他日子”.

- [ ] **Step 2: Define provider metadata**

Both XML files set `android:configure` to `WidgetConfigActivity`, `android:widgetFeatures="reconfigurable|configuration_optional"`, and `android:resizeMode="none"`. The wide provider uses `minWidth="110dp"`, `minHeight="40dp"`, `targetCellWidth="2"`, `targetCellHeight="1"`; the square provider uses `minWidth="110dp"`, `minHeight="110dp"`, `targetCellWidth="2"`, `targetCellHeight="2"`. Receiver labels use `@string/app_name` so Xiaomi displays “念日” below the card.

- [ ] **Step 3: Implement shared widget surface**

Use `widget_night_background.xml` for the supported Glance background instead of relying on unsupported Compose brushes. Core content uses white text, `#C5BBFF` days, and fixed internal padding small enough for the reference screenshot.

Wide layout:

```text
妈妈生日                         23天
按农历 · [新历 8/6 ↻]
```

Square layout:

```text
妈妈生日
按农历倒计时
23天
本次日期       [新历 8月6日 ↻]
```

The bracketed date area runs `ToggleWidgetCalendarAction`; the rest opens the detail route.

- [ ] **Step 4: Implement missing/deleted state**

Unconfigured and missing states open `WidgetConfigActivity` with the current widget ID. Missing state does not select another day automatically and preserves launcher placement.

- [ ] **Step 5: Verify widget providers**

Run:

```bash
./gradlew connectedDebugAndroidTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: both providers appear in the launcher picker as “念日”; configured instances update independently.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nianri/app/widget app/src/main/res app/src/main/AndroidManifest.xml app/src/androidTest
git commit -m "feat: add Xiaomi-sized Nianri widgets"
```

---

### Task 10: Run integration, compatibility, and visual acceptance

**Files:**
- Modify: `docs/DEVELOPMENT.md`
- Create: `docs/DEVICE_QA.md`
- Create: `app/src/androidTest/java/com/nianri/app/EndToEndTest.kt`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: the completed app, emulator, and Xiaomi 15 Pro.
- Produces: verified debug APK, reproducible QA evidence, and a clean repository.

- [ ] **Step 1: Add end-to-end instrumentation coverage**

The test creates a lunar birthday, confirms its solar display, toggles to lunar without changing days, pins it, creates a solar record, configures two widget IDs to different records, deletes one referenced record, and asserts the missing-widget state.

- [ ] **Step 2: Run the complete automated gate**

```bash
./gradlew clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Expected: all tasks pass with zero lint errors and a debug APK is produced.

- [ ] **Step 3: Test Android-version behavior**

Run the smoke and permission cases on API 26, API 31, API 36, and an API 37 preview emulator. Record results in `docs/DEVICE_QA.md`, including notification permission, exact-alarm permission, reboot recovery, timezone change, and local 09:00 delivery.

- [ ] **Step 4: Test the Xiaomi 15 Pro manually**

Install with `adb install -r`. Verify HyperOS 3.0.304.0 notification settings, battery guidance, `2×1` size against the “主卧灯” screenshot, `2×2` size against the “天气” screenshot, launcher “念日” label, independent widget selection/display, deleted-record reconfiguration, font scaling, and wireless debugging reconnect.

- [ ] **Step 5: Capture acceptance artifacts**

Save App and widget screenshots under `docs/qa/` with filenames `home.png`, `edit-lunar.png`, `widget-2x1.png`, `widget-2x2.png`, and `widget-missing.png`. Add the final APK SHA-256 and tested commit hash to `docs/DEVICE_QA.md`.

- [ ] **Step 6: Final verification commit**

```bash
git add docs app/src/androidTest .gitignore
git commit -m "test: complete Nianri compatibility acceptance"
git status --short
```

Expected: final `git status --short` is empty.
