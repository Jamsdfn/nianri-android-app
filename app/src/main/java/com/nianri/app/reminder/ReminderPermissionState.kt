package com.nianri.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

sealed interface ReminderPermissionState {
    data object NotNeeded : ReminderPermissionState
    data object WaitingForNotificationPermission : ReminderPermissionState
    data object WaitingForExactAlarmPermission : ReminderPermissionState
    data object Denied : ReminderPermissionState
    data object Ready : ReminderPermissionState
}

interface ReminderPermissionController {
    fun state(hasReminders: Boolean): ReminderPermissionState
    fun notificationRequestStarted()
    fun exactAlarmRequestStarted()
}

class AndroidReminderPermissionController(context: Context) : ReminderPermissionController {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun state(hasReminders: Boolean): ReminderPermissionState {
        if (!hasReminders) return ReminderPermissionState.NotNeeded
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!notificationGranted) {
            return if (preferences.getBoolean(NOTIFICATION_REQUESTED, false) || wasReady()) {
                ReminderPermissionState.Denied
            } else {
                ReminderPermissionState.WaitingForNotificationPermission
            }
        }
        val exactAlarmGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (!exactAlarmGranted) {
            return if (preferences.getBoolean(EXACT_ALARM_REQUESTED, false) || wasReady()) {
                ReminderPermissionState.Denied
            } else {
                ReminderPermissionState.WaitingForExactAlarmPermission
            }
        }
        preferences.edit().putBoolean(WAS_READY, true).apply()
        return ReminderPermissionState.Ready
    }

    override fun notificationRequestStarted() {
        preferences.edit().putBoolean(NOTIFICATION_REQUESTED, true).apply()
    }

    override fun exactAlarmRequestStarted() {
        preferences.edit().putBoolean(EXACT_ALARM_REQUESTED, true).apply()
    }

    private fun wasReady() = preferences.getBoolean(WAS_READY, false)

    private companion object {
        const val PREFERENCES = "reminder_permissions"
        const val NOTIFICATION_REQUESTED = "notification_requested"
        const val EXACT_ALARM_REQUESTED = "exact_alarm_requested"
        const val WAS_READY = "was_ready"
    }
}
