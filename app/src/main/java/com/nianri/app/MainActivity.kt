package com.nianri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nianri.app.data.UiPreferences
import com.nianri.app.ui.NianriNavHost
import com.nianri.app.ui.theme.NianriTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as NianriApplication
        val importantDayId = intent
            .takeIf { it.hasExtra(IMPORTANT_DAY_ID) }
            ?.getLongExtra(IMPORTANT_DAY_ID, 0L)
        val uiPreferences = UiPreferences(this)
        setContent {
            NianriTheme {
                NianriNavHost(
                    container = application.container,
                    uiPreferences = uiPreferences,
                    importantDayId = importantDayId,
                )
            }
        }
    }

    private companion object {
        const val IMPORTANT_DAY_ID = "importantDayId"
    }
}
