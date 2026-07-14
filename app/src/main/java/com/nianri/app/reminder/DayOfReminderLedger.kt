package com.nianri.app.reminder

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DayOfReminderLedger(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun deliverOnce(dayId: Long, occurrenceDate: LocalDate, deliver: () -> Unit): Boolean =
        mutex.withLock {
            val key = "day_$dayId"
            val value = occurrenceDate.toString()
            if (preferences.getString(key, null) == value) return@withLock false
            if (!preferences.edit().putString(key, value).commit()) return@withLock false
            try {
                deliver()
                true
            } catch (error: Exception) {
                if (preferences.getString(key, null) == value) {
                    preferences.edit().remove(key).commit()
                }
                throw error
            }
        }

    companion object {
        const val PREFERENCES = "day_of_reminder_delivery"
    }
}
