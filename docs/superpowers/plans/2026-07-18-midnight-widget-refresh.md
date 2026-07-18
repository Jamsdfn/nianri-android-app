# Midnight Widget Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh every configured countdown widget at the next local 00:00 and reschedule the following midnight without producing sound, vibration, notifications, or a user-visible alarm.

**Architecture:** A focused `MidnightWidgetRefreshScheduler` calculates the next local day boundary and owns one fixed explicit `PendingIntent` scheduled through `AlarmManager.setExactAndAllowWhileIdle()`. An unexported receiver refreshes all configured widgets and always renews the alarm; application startup and the existing system-recovery receiver provide idempotent recovery, while the existing WorkManager audit remains the delayed fallback.

**Tech Stack:** Kotlin, Android SDK 26–36, `AlarmManager`, `BroadcastReceiver.goAsync()`, Kotlin coroutines, Glance app widgets, WorkManager, JUnit 4, Robolectric 4.16.1.

## Global Constraints

- Keep `minSdk = 26`, `compileSdk = 36`, and `targetSdk = 36` unchanged.
- Use `AlarmManager.RTC_WAKEUP` with `setExactAndAllowWhileIdle()`; never use `setAlarmClock()`.
- Reuse the already declared `android.permission.SCHEDULE_EXACT_ALARM`; add no permissions and show no new permission prompt.
- The midnight path must not call notification, audio, vibration, activity-launch, screen-wake, foreground-service, or network APIs.
- Compute the trigger as the next local date's `atStartOfDay(clock.zone)`, never as the current instant plus 24 hours.
- Keep one fixed explicit immutable broadcast `PendingIntent`, so repeated scheduling replaces the same logical alarm.
- Android 12 and later must skip scheduling when `canScheduleExactAlarms()` is false; `SecurityException` must be contained.
- Refresh failure must not prevent renewal of the next alarm, and renewal failure must not prevent `PendingResult.finish()`.
- Keep `updatePeriodMillis="0"` and retain the existing WorkManager and foreground-audit fallback paths.
- Add no third-party dependency.

---

### Task 1: Calculate and schedule the next local midnight

**Files:**
- Create: `app/src/main/java/com/nianri/app/widget/MidnightWidgetRefreshScheduler.kt`
- Create: `app/src/test/java/com/nianri/app/widget/MidnightWidgetRefreshSchedulerTest.kt`

**Interfaces:**
- Consumes: Android application `Context`, injected `java.time.Clock`, `AlarmManager`, and the receiver class introduced in Task 2.
- Produces: `internal fun nextLocalMidnight(clock: Clock): Instant` and `class MidnightWidgetRefreshScheduler` with `fun scheduleNext()`.
- Produces: `MIDNIGHT_WIDGET_REFRESH_ACTION`, shared by the scheduler and Task 2 receiver.

- [ ] **Step 1: Write failing tests for local-midnight calculation**

Create `MidnightWidgetRefreshSchedulerTest.kt` with the time calculations first:

```kotlin
package com.nianri.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MidnightWidgetRefreshSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun `next refresh is midnight of the next Shanghai date`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T15:59:59Z"),
            ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(
            Instant.parse("2026-07-18T16:00:00Z"),
            nextLocalMidnight(clock),
        )
    }

    @Test
    fun `next refresh supports a non whole hour time zone`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"),
            ZoneId.of("Asia/Kathmandu"),
        )

        assertEquals(
            Instant.parse("2026-07-18T18:15:00Z"),
            nextLocalMidnight(clock),
        )
    }

    @Test
    fun `next refresh follows daylight saving start instead of adding 24 hours`() {
        val clock = Clock.fixed(
            Instant.parse("2026-03-08T05:30:00Z"),
            ZoneId.of("America/New_York"),
        )

        assertEquals(
            Instant.parse("2026-03-09T04:00:00Z"),
            nextLocalMidnight(clock),
        )
    }
}
```

- [ ] **Step 2: Run the calculation tests and verify they fail**

Run:

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests 'com.nianri.app.widget.MidnightWidgetRefreshSchedulerTest'
```

Expected: compilation fails because `nextLocalMidnight` does not exist.

- [ ] **Step 3: Implement the pure calculation and scheduler shell**

Create `MidnightWidgetRefreshScheduler.kt`:

```kotlin
package com.nianri.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

internal const val MIDNIGHT_WIDGET_REFRESH_ACTION =
    "com.nianri.app.action.MIDNIGHT_WIDGET_REFRESH"
private const val MIDNIGHT_WIDGET_REFRESH_REQUEST_CODE = 20_260_718

internal fun nextLocalMidnight(clock: Clock): Instant =
    LocalDate.now(clock)
        .plusDays(1)
        .atStartOfDay(clock.zone)
        .toInstant()

class MidnightWidgetRefreshScheduler internal constructor(
    context: Context,
    private val clock: Clock,
    alarmManager: AlarmManager = context.applicationContext
        .getSystemService(AlarmManager::class.java),
    private val hasExactAlarmAccess: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    },
    private val setExactAlarm: (Long, PendingIntent) -> Unit = { triggerAtMillis, operation ->
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation,
        )
    },
) {
    private val applicationContext = context.applicationContext

    fun scheduleNext() {
        if (!hasExactAlarmAccess()) return
        try {
            setExactAlarm(nextLocalMidnight(clock).toEpochMilli(), operation())
        } catch (_: SecurityException) {
            // A permission revocation can race the access check. Recovery paths retry later.
        }
    }

    private fun operation(): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        MIDNIGHT_WIDGET_REFRESH_REQUEST_CODE,
        Intent(applicationContext, MidnightWidgetRefreshReceiver::class.java)
            .setAction(MIDNIGHT_WIDGET_REFRESH_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

Task 1 temporarily references the receiver that Task 2 will add. To keep each commit buildable, add this minimal receiver declaration at the bottom of the same file and remove it in Task 2 when the real file is created:

```kotlin
internal class MidnightWidgetRefreshReceiver
```

- [ ] **Step 4: Run the calculation tests and verify they pass**

Run the Task 1 test command again.

Expected: all three calculation tests pass.

- [ ] **Step 5: Add failing tests for exact-alarm semantics and degradation**

Append these tests inside `MidnightWidgetRefreshSchedulerTest`:

```kotlin
    @Test
    fun `scheduler creates one exact idle allowed RTC wakeup for the receiver`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        MidnightWidgetRefreshScheduler(context, clock).scheduleNext()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(Instant.parse("2026-07-18T16:00:00Z").toEpochMilli(), alarm.triggerAtTime)
        assertTrue(alarm.allowWhileIdle)
        assertEquals(MIDNIGHT_WIDGET_REFRESH_ACTION, intent.action)
        assertEquals(MidnightWidgetRefreshReceiver::class.java.name, intent.component?.className)
    }

    @Test
    fun `repeated scheduling keeps one pending intent identity`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduler = MidnightWidgetRefreshScheduler(context, clock)

        scheduler.scheduleNext()
        scheduler.scheduleNext()

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `missing exact alarm access skips scheduling`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduler = MidnightWidgetRefreshScheduler(
            context,
            Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("Asia/Shanghai")),
        )

        scheduler.scheduleNext()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `permission race is contained without a fallback alarm`() {
        val attempted = mutableListOf<Long>()
        val scheduler = MidnightWidgetRefreshScheduler(
            context = context,
            clock = Clock.fixed(
                Instant.parse("2026-07-18T12:00:00Z"),
                ZoneId.of("Asia/Shanghai"),
            ),
            hasExactAlarmAccess = { true },
            setExactAlarm = { triggerAtMillis, _ ->
                attempted += triggerAtMillis
                throw SecurityException("revoked")
            },
        )

        scheduler.scheduleNext()

        assertEquals(listOf(Instant.parse("2026-07-18T16:00:00Z").toEpochMilli()), attempted)
    }
```

- [ ] **Step 6: Run the scheduler tests and make the implementation pass**

Run the Task 1 test command.

Expected: seven tests pass. If Robolectric exposes the scheduled alarm type through `alarm.type`, keep the assertion as written; this project already pins Robolectric 4.16.1, whose `ScheduledAlarm` includes that property.

- [ ] **Step 7: Commit the scheduler and its tests**

```bash
git add app/src/main/java/com/nianri/app/widget/MidnightWidgetRefreshScheduler.kt app/src/test/java/com/nianri/app/widget/MidnightWidgetRefreshSchedulerTest.kt
git commit -m "feat: schedule silent midnight widget refresh"
```

---

### Task 2: Refresh widgets and always renew from the midnight receiver

**Files:**
- Create: `app/src/main/java/com/nianri/app/widget/MidnightWidgetRefreshReceiver.kt`
- Create: `app/src/test/java/com/nianri/app/widget/MidnightWidgetRefreshReceiverTest.kt`
- Modify: `app/src/main/java/com/nianri/app/widget/MidnightWidgetRefreshScheduler.kt`
- Modify: `app/src/main/java/com/nianri/app/AppContainer.kt`
- Modify: `app/src/test/java/com/nianri/app/AppContainerTest.kt`

**Interfaces:**
- Consumes: `MIDNIGHT_WIDGET_REFRESH_ACTION`, `NianriApplication.container.widgetUpdater.updateAll()`, and `MidnightWidgetRefreshScheduler.scheduleNext()`.
- Produces: `class MidnightWidgetRefreshReceiver : BroadcastReceiver`, `internal suspend fun runMidnightWidgetRefresh(updateWidgets, scheduleNext, finish)`, and `AppContainer.midnightWidgetRefreshScheduler`.

- [ ] **Step 1: Write failing orchestration tests**

Create `MidnightWidgetRefreshReceiverTest.kt`:

```kotlin
package com.nianri.app.widget

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MidnightWidgetRefreshReceiverTest {
    @Test
    fun `successful midnight refresh updates then renews then finishes`() = runBlocking {
        val calls = mutableListOf<String>()

        runMidnightWidgetRefresh(
            updateWidgets = { calls += "widgets" },
            scheduleNext = { calls += "schedule" },
            finish = { calls += "finish" },
        )

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `widget failure still renews and finishes before surfacing the failure`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runMidnightWidgetRefresh(
                    updateWidgets = {
                        calls += "widgets"
                        error("database unavailable")
                    },
                    scheduleNext = { calls += "schedule" },
                    finish = { calls += "finish" },
                )
            }
        }

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `renewal failure still finishes the pending result`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runMidnightWidgetRefresh(
                    updateWidgets = { calls += "widgets" },
                    scheduleNext = {
                        calls += "schedule"
                        error("alarm unavailable")
                    },
                    finish = { calls += "finish" },
                )
            }
        }

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `cancellation still renews and finishes before cancellation is preserved`() {
        val calls = mutableListOf<String>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runMidnightWidgetRefresh(
                    updateWidgets = {
                        calls += "widgets"
                        throw CancellationException("stopped")
                    },
                    scheduleNext = { calls += "schedule" },
                    finish = { calls += "finish" },
                )
            }
        }

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }
}
```

Append to `AppContainerTest.kt`:

```kotlin
    @Test
    fun `container binds the midnight widget refresh scheduler`() {
        val container = AppContainer(RuntimeEnvironment.getApplication())

        assertTrue(container.midnightWidgetRefreshScheduler is MidnightWidgetRefreshScheduler)
    }
```

Add this import:

```kotlin
import com.nianri.app.widget.MidnightWidgetRefreshScheduler
```

- [ ] **Step 2: Run the receiver tests and verify they fail**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests 'com.nianri.app.widget.MidnightWidgetRefreshReceiverTest'
```

Expected: compilation fails because `runMidnightWidgetRefresh` and the container scheduler property do not exist.

- [ ] **Step 3: Implement the receiver and exception-safe orchestration**

Remove the temporary `internal class MidnightWidgetRefreshReceiver` declaration from the scheduler file. Create `MidnightWidgetRefreshReceiver.kt`:

```kotlin
package com.nianri.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nianri.app.NianriApplication
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MidnightWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MIDNIGHT_WIDGET_REFRESH_ACTION) return
        val pendingResult = goAsync()
        val application = context.applicationContext as NianriApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runMidnightWidgetRefresh(
                    updateWidgets = application.container.widgetUpdater::updateAll,
                    scheduleNext = application.container.midnightWidgetRefreshScheduler::scheduleNext,
                    finish = pendingResult::finish,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Application startup, system changes, and WorkManager remain recovery paths.
            }
        }
    }
}

internal suspend fun runMidnightWidgetRefresh(
    updateWidgets: suspend () -> Unit,
    scheduleNext: () -> Unit,
    finish: () -> Unit,
) {
    var failure: Exception? = null

    try {
        updateWidgets()
    } catch (error: Exception) {
        failure = error
    }

    try {
        scheduleNext()
    } catch (error: Exception) {
        if (failure == null) failure = error else failure.addSuppressed(error)
    }

    try {
        finish()
    } catch (error: Exception) {
        if (failure == null) failure = error else failure.addSuppressed(error)
    }

    failure?.let { throw it }
}
```

The receiver has no notification, audio, vibration, activity, foreground-service, or wake-lock code. `RTC_WAKEUP` wakes the process only long enough for Android to deliver the broadcast; it does not turn on the display.

Add this import to `AppContainer.kt`:

```kotlin
import com.nianri.app.widget.MidnightWidgetRefreshScheduler
```

Add this lazy singleton immediately after `widgetUpdater`:

```kotlin
    val midnightWidgetRefreshScheduler by lazy {
        MidnightWidgetRefreshScheduler(
            context = applicationContext,
            clock = CurrentSystemZoneClock(),
        )
    }
```

- [ ] **Step 4: Run Task 1 and Task 2 tests together**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests 'com.nianri.app.widget.MidnightWidgetRefreshSchedulerTest' --tests 'com.nianri.app.widget.MidnightWidgetRefreshReceiverTest' --tests 'com.nianri.app.AppContainerTest'
```

Expected: eleven tests pass.

- [ ] **Step 5: Commit the receiver and orchestration tests**

```bash
git add app/src/main/java/com/nianri/app/widget/MidnightWidgetRefreshScheduler.kt app/src/main/java/com/nianri/app/widget/MidnightWidgetRefreshReceiver.kt app/src/test/java/com/nianri/app/widget/MidnightWidgetRefreshReceiverTest.kt app/src/main/java/com/nianri/app/AppContainer.kt app/src/test/java/com/nianri/app/AppContainerTest.kt
git commit -m "feat: refresh and renew widgets at midnight"
```

---

### Task 3: Recover midnight scheduling on startup and system changes

**Files:**
- Modify: `app/src/main/java/com/nianri/app/NianriApplication.kt`
- Modify: `app/src/main/java/com/nianri/app/reminder/SystemChangeReceiver.kt`
- Create: `app/src/test/java/com/nianri/app/BackgroundRefreshInitializationTest.kt`
- Modify: `app/src/test/java/com/nianri/app/reminder/ReminderRecoveryTest.kt`

**Interfaces:**
- Consumes: `AppContainer.midnightWidgetRefreshScheduler` from Task 2 and its `scheduleNext()` method.
- Produces: `initializeBackgroundRefresh(enqueueAudit, scheduleNextMidnight)` and a third `scheduleNextMidnight` argument on `runSystemChangeRefresh` with a no-op default for the existing WorkManager caller.

- [ ] **Step 1: Write the failing startup initialization test**

Create `BackgroundRefreshInitializationTest.kt`:

```kotlin
package com.nianri.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRefreshInitializationTest {
    @Test
    fun `application startup keeps the audit and schedules midnight refresh`() {
        val calls = mutableListOf<String>()

        initializeBackgroundRefresh(
            enqueueAudit = { calls += "audit" },
            scheduleNextMidnight = { calls += "midnight" },
        )

        assertEquals(listOf("audit", "midnight"), calls)
    }
}
```

- [ ] **Step 2: Write failing system recovery tests**

Change the successful recovery test call in `ReminderRecoveryTest.kt` to:

```kotlin
        runSystemChangeRefresh(
            rebuildReminders = { calls += "reminders" },
            updateWidgets = { calls += "widgets" },
            scheduleNextMidnight = { calls += "midnight" },
        )

        assertEquals(listOf("reminders", "widgets", "midnight"), calls)
```

Change the reminder-failure test call and assertion to:

```kotlin
                runSystemChangeRefresh(
                    rebuildReminders = {
                        calls += "reminders"
                        error("alarm unavailable")
                    },
                    updateWidgets = { calls += "widgets" },
                    scheduleNextMidnight = { calls += "midnight" },
                )
```

```kotlin
        assertEquals(listOf("reminders", "widgets", "midnight"), calls)
```

Add a separate widget-failure coverage test:

```kotlin
    @Test
    fun `midnight renewal runs when widget recovery fails`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runSystemChangeRefresh(
                    rebuildReminders = { calls += "reminders" },
                    updateWidgets = {
                        calls += "widgets"
                        error("widget host unavailable")
                    },
                    scheduleNextMidnight = { calls += "midnight" },
                )
            }
        }

        assertEquals(listOf("reminders", "widgets", "midnight"), calls)
    }
```

- [ ] **Step 3: Run the lifecycle tests and verify they fail**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests 'com.nianri.app.BackgroundRefreshInitializationTest' --tests 'com.nianri.app.reminder.ReminderRecoveryTest'
```

Expected: compilation fails for the missing startup helper and recovery argument.

- [ ] **Step 4: Schedule both background mechanisms during application startup**

Replace the direct enqueue block in `NianriApplication.onCreate()` with:

```kotlin
        initializeBackgroundRefresh(
            enqueueAudit = {
                workManager.enqueueUniquePeriodicWork(
                    REMINDER_AUDIT_WORK,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    reminderAuditRequest(),
                )
            },
            scheduleNextMidnight = container.midnightWidgetRefreshScheduler::scheduleNext,
        )
```

Add this top-level helper below the application class:

```kotlin
internal fun initializeBackgroundRefresh(
    enqueueAudit: () -> Unit,
    scheduleNextMidnight: () -> Unit,
) {
    enqueueAudit()
    scheduleNextMidnight()
}
```

- [ ] **Step 5: Renew midnight scheduling after supported system changes**

Pass the new callback from `SystemChangeReceiver.onReceive()`:

```kotlin
                runSystemChangeRefresh(
                    rebuildReminders = application.container.reminderScheduler::rebuildAll,
                    updateWidgets = application.container.widgetUpdater::updateAll,
                    scheduleNextMidnight =
                        application.container.midnightWidgetRefreshScheduler::scheduleNext,
                )
```

Replace `runSystemChangeRefresh` with this three-step aggregation, retaining a default so `runDailyAudit` remains unchanged:

```kotlin
suspend fun runSystemChangeRefresh(
    rebuildReminders: suspend () -> Unit,
    updateWidgets: suspend () -> Unit,
    scheduleNextMidnight: () -> Unit = {},
) {
    var failure: Exception? = null

    suspend fun runSuspendingStep(step: suspend () -> Unit) {
        try {
            step()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
    }

    runSuspendingStep(rebuildReminders)
    runSuspendingStep(updateWidgets)
    try {
        scheduleNextMidnight()
    } catch (error: Exception) {
        if (failure == null) failure = error else failure.addSuppressed(error)
    }

    failure?.let { throw it }
}
```

Keep the existing `SYSTEM_REBUILD_ACTIONS` unchanged; it already includes boot, package replacement, date, time, timezone, and exact-alarm permission-state changes.

- [ ] **Step 6: Run lifecycle and existing audit tests**

Run the Task 3 test command.

Expected: all selected tests pass, including the unchanged WorkManager request assertions and retry semantics.

- [ ] **Step 7: Commit lifecycle recovery wiring**

```bash
git add app/src/main/java/com/nianri/app/NianriApplication.kt app/src/main/java/com/nianri/app/reminder/SystemChangeReceiver.kt app/src/test/java/com/nianri/app/BackgroundRefreshInitializationTest.kt app/src/test/java/com/nianri/app/reminder/ReminderRecoveryTest.kt
git commit -m "feat: recover midnight widget scheduling"
```

---

### Task 4: Declare a private receiver without expanding capabilities

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/java/com/nianri/app/widget/WidgetManifestTest.kt`
- Modify: `app/src/test/java/com/nianri/app/reminder/ReminderManifestTest.kt`

**Interfaces:**
- Consumes: `com.nianri.app.widget.MidnightWidgetRefreshReceiver`.
- Produces: an installed, `android:exported="false"` broadcast receiver with no intent filter and no new permission declarations.

- [ ] **Step 1: Write failing receiver visibility and capability tests**

Add these imports to `WidgetManifestTest.kt`:

```kotlin
import android.content.ComponentName
import org.junit.Assert.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
```

Annotate the class:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetManifestTest {
```

Add this test:

```kotlin
    @Test
    fun `midnight refresh receiver is installed and not exported`() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, MidnightWidgetRefreshReceiver::class.java),
            0,
        )

        assertFalse(receiver.exported)
    }
```

Add this import to `ReminderManifestTest`:

```kotlin
import java.io.File
```

Extend `ReminderManifestTest` after the existing `INTERNET` and `USE_EXACT_ALARM` checks. Inspect the app-owned source manifest rather than the merged package for these capabilities, because WorkManager may contribute its own internal service permissions during manifest merging:

```kotlin
        val sourceManifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("android.permission.VIBRATE" in sourceManifest)
        assertFalse("android.permission.WAKE_LOCK" in sourceManifest)
        assertFalse("android.permission.FOREGROUND_SERVICE" in sourceManifest)
        assertFalse("android.permission.MODIFY_AUDIO_SETTINGS" in sourceManifest)
```

- [ ] **Step 2: Run the manifest tests and verify the receiver test fails**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests 'com.nianri.app.widget.WidgetManifestTest' --tests 'com.nianri.app.reminder.ReminderManifestTest'
```

Expected: `NameNotFoundException` for `MidnightWidgetRefreshReceiver`; existing permission assertions pass.

- [ ] **Step 3: Register the receiver privately**

Add this declaration immediately after `ReminderReceiver` in `AndroidManifest.xml`:

```xml
        <receiver
            android:name=".widget.MidnightWidgetRefreshReceiver"
            android:exported="false" />
```

Do not add an intent filter. The scheduler uses an explicit component, and keeping the receiver unexported ensures other apps cannot trigger the database/widget work.

- [ ] **Step 4: Run all focused midnight and recovery tests**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest --tests 'com.nianri.app.widget.MidnightWidgetRefreshSchedulerTest' --tests 'com.nianri.app.widget.MidnightWidgetRefreshReceiverTest' --tests 'com.nianri.app.widget.WidgetManifestTest' --tests 'com.nianri.app.AppContainerTest' --tests 'com.nianri.app.BackgroundRefreshInitializationTest' --tests 'com.nianri.app.reminder.ReminderRecoveryTest' --tests 'com.nianri.app.reminder.ReminderManifestTest'
```

Expected: every focused test passes.

- [ ] **Step 5: Commit the manifest declaration and guardrails**

```bash
git add app/src/main/AndroidManifest.xml app/src/test/java/com/nianri/app/widget/WidgetManifestTest.kt app/src/test/java/com/nianri/app/reminder/ReminderManifestTest.kt
git commit -m "feat: register private midnight refresh receiver"
```

---

### Task 5: Run regression, device, and package verification

**Files:**
- Verify only; no source file is expected to change.
- Output: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: all production and test changes from Tasks 1–4.
- Produces: a fully tested debug APK and recorded checksum/signature result.

- [ ] **Step 1: Run every JVM unit test**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, with no failed tests.

- [ ] **Step 2: Build the debug APK from a clean compile graph**

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Run the complete connected suite on the API 36 emulator**

Start the existing `nianri_api36` AVD if it is not already running:

```bash
/Users/alexander/Library/Android/sdk/emulator/emulator -avd nianri_api36 -no-window -no-audio -no-boot-anim -no-snapshot-save
```

After `adb shell getprop sys.boot_completed` returns `1`, run:

```bash
ANDROID_HOME=/Users/alexander/Library/Android/sdk ./gradlew connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`, with all instrumentation tests passing on API 36.

- [ ] **Step 4: Verify scheduling state on the emulator**

Launch the app once, then inspect its alarm entries:

```bash
/Users/alexander/Library/Android/sdk/platform-tools/adb shell monkey -p com.nianri.app 1
/Users/alexander/Library/Android/sdk/platform-tools/adb shell dumpsys alarm
```

Expected: the dump contains the package `com.nianri.app` and the action `com.nianri.app.action.MIDNIGHT_WIDGET_REFRESH`. It must not identify the entry as an alarm-clock entry. If exact-alarm access is disabled in the emulator, enable the app's “Alarms & reminders” access through system settings, relaunch once, and repeat only this inspection.

- [ ] **Step 5: Verify APK signature and checksum**

```bash
/Users/alexander/Library/Android/sdk/build-tools/36.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
```

Expected: signature verification succeeds (including APK Signature Scheme v2 or newer), followed by one SHA-256 line for the APK.

- [ ] **Step 6: Verify the worktree contains only intentional changes**

```bash
git status --short
git log --oneline -5
```

Expected: clean status; the log includes the four implementation commits plus the implementation-plan commit.
