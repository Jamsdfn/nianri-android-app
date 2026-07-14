package com.nianri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.nianri.app.data.UiPreferences
import com.nianri.app.reminder.reminderImmediateAuditRequest
import com.nianri.app.ui.NianriNavHost
import com.nianri.app.ui.theme.NianriTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        WorkManager.getInstance(this).enqueueUniqueWork(
            NianriApplication.REMINDER_FOREGROUND_AUDIT_WORK,
            ExistingWorkPolicy.REPLACE,
            reminderImmediateAuditRequest(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as NianriApplication
        val importantDayId = intent
            .takeIf { it.hasExtra(EXTRA_IMPORTANT_DAY_ID) }
            ?.getLongExtra(EXTRA_IMPORTANT_DAY_ID, 0L)
        val editImportantDayId = intent
            .takeIf { it.hasExtra(EXTRA_EDIT_IMPORTANT_DAY_ID) }
            ?.getLongExtra(EXTRA_EDIT_IMPORTANT_DAY_ID, 0L)
        val openNewDay = intent.getBooleanExtra(EXTRA_OPEN_NEW_DAY, false)
        val uiPreferences = UiPreferences(this)
        setContent {
            NianriTheme {
                NianriNavHost(
                    container = application.container,
                    uiPreferences = uiPreferences,
                    importantDayId = importantDayId,
                    editImportantDayId = editImportantDayId,
                    openNewDay = openNewDay,
                )
            }
        }
    }

    companion object {
        const val EXTRA_IMPORTANT_DAY_ID = "importantDayId"
        const val EXTRA_OPEN_NEW_DAY = "openNewDay"
        const val EXTRA_EDIT_IMPORTANT_DAY_ID = "editImportantDayId"
    }
}
