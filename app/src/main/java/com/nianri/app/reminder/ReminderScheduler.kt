package com.nianri.app.reminder

interface ReminderScheduler {
    suspend fun replace(dayId: Long): ReminderScheduleResult

    suspend fun cancel(dayId: Long)

    suspend fun rebuildAll()
}

sealed interface ReminderScheduleResult {
    data class Scheduled(val count: Int) : ReminderScheduleResult
    data object NeedsExactAlarmPermission : ReminderScheduleResult
}
