package com.nianri.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UiPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val explanationSeen = MutableStateFlow(
        preferences.getBoolean(CALENDAR_EXPLANATION_SEEN, false),
    )

    val calendarExplanationSeen: StateFlow<Boolean> = explanationSeen.asStateFlow()

    fun markCalendarExplanationSeen() {
        preferences.edit { putBoolean(CALENDAR_EXPLANATION_SEEN, true) }
        explanationSeen.value = true
    }

    private companion object {
        const val PREFERENCES_NAME = "ui_preferences"
        const val CALENDAR_EXPLANATION_SEEN = "calendar_explanation_seen"
    }
}
