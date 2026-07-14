package com.nianri.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import org.junit.Assert.assertEquals
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
class ReminderPermissionStateTest {
    private lateinit var context: Context
    private lateinit var controller: AndroidReminderPermissionController

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("reminder_permissions", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        controller = AndroidReminderPermissionController(context)
    }

    @Test
    fun `no selected reminder needs no permission`() {
        assertEquals(ReminderPermissionState.NotNeeded, controller.state(hasReminders = false))
    }

    @Test
    fun `notification permission waits before request and is denied after request`() {
        assertEquals(ReminderPermissionState.WaitingForNotificationPermission, controller.state(true))

        controller.notificationRequestStarted()

        assertEquals(ReminderPermissionState.Denied, controller.state(true))
    }

    @Test
    fun `exact alarm waits only after notification is granted`() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(ReminderPermissionState.WaitingForExactAlarmPermission, controller.state(true))
        controller.exactAlarmRequestStarted()
        assertEquals(ReminderPermissionState.Denied, controller.state(true))
    }

    @Test
    fun `all platform permissions granted is ready`() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        assertEquals(ReminderPermissionState.Ready, controller.state(true))
    }

    @Test
    fun `permission revoked after a ready state is denied`() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertEquals(ReminderPermissionState.Ready, controller.state(true))

        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(ReminderPermissionState.Denied, controller.state(true))
    }
}
