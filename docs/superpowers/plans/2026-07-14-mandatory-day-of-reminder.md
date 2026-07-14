# Mandatory Day-of Reminder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Always remind at 09:00 on the occurrence day, catch up once later that same day, and keep 14/7/3 independently configurable.

**Architecture:** Offset `0` is implicit and never stored in the optional reminder mask. Scheduler always handles it and immediately dispatches the existing receiver when rebuilding after 09:00. A process-safe SharedPreferences ledger deduplicates day-of delivery; App foreground audits retry permission-delayed delivery.

**Tech Stack:** Kotlin, AlarmManager, BroadcastReceiver, NotificationManager, SharedPreferences, WorkManager, Compose, Robolectric.

## Global Constraints

- Day-of reminder is fixed at local 09:00 and cannot be disabled.
- 14/7/3 remain independent optional reminders.
- Catch up only on the occurrence day and at most once.
- No Room schema or reminder-mask migration.

### Task 1: Schedule Offset Zero and Immediate Catch-up

**Files:** `AndroidReminderSchedulerTest.kt`, `AndroidReminderScheduler.kt`, `ReminderReceiver.kt`.

- [ ] Update tests: enabled reminders schedule offsets `14,7,3,0`; empty optional reminders still schedule `0`; after 09:00 on occurrence day dispatches offset `0` immediately instead of a past alarm.
- [ ] Run `./gradlew testDebugUnitTest --tests '*AndroidReminderSchedulerTest'` and verify RED.
- [ ] Add injectable immediate dispatcher to scheduler; always merge `0` with optional offsets; add `0` to cancel/receiver allowed offsets; dispatch immediate day-of intent at/after 09:00.
- [ ] Re-run focused tests and verify GREEN.

### Task 2: Deduplicate Delivery and Audit on Foreground

**Files:** `ReminderReceiverTest.kt`, create `DayOfReminderLedger.kt`, modify `ReminderReceiver.kt`, `AppContainer.kt`, `ReminderAuditWorker.kt`, `NianriApplication.kt`, `MainActivity.kt`, `ReminderRecoveryTest.kt`.

- [ ] Add tests: offset `0` ignores optional mask, posts only on occurrence day, duplicate same-date delivery is rejected, next-year occurrence can deliver, notification failure releases ledger; immediate audit request is one-time.
- [ ] Verify RED with focused reminder tests.
- [ ] Implement a singleton `DayOfReminderLedger` with SharedPreferences and `Mutex`; inject it into notification service; set `setOnlyAlertOnce(true)`; rollback ledger mark on notify exception.
- [ ] Add one-time unique `ReminderAuditWorker` request from `MainActivity.onResume`; retain daily periodic work.
- [ ] Verify focused tests GREEN.

### Task 3: Show Fixed Reminder and Require Permissions

**Files:** `EditDayScreenTest.kt`, `EditDayViewModelTest.kt`, `EditDayScreen.kt`, `EditDayViewModel.kt`.

- [ ] Add failing UI/state tests for “当天 09:00 · 固定开启” and permission checks when optional reminders are empty.
- [ ] Make permission state always evaluate reminders as needed for the edit/create form; add fixed read-only copy above 14/7/3 chips.
- [ ] Run focused tests, then `./gradlew testDebugUnitTest lintDebug assembleDebug` and complete instrumentation matrix.
- [ ] Commit, install on Xiaomi 15 Pro, and verify App/widget linkage, removed FAB, fixed reminder copy, and updated alarm set.
