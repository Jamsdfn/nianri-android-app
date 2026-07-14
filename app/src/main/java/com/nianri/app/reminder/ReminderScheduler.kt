package com.nianri.app.reminder

interface ReminderScheduler {
    suspend fun replace(dayId: Long)

    suspend fun cancel(dayId: Long)

    suspend fun rebuildAll()
}
