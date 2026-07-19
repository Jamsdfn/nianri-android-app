package com.nianri.app.data.transfer

import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.domain.WidgetUpdater
import com.nianri.app.reminder.ReminderScheduler
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

data class TransferImportResult(
    val importedCount: Int,
    val renamedCount: Int,
    val refreshFailed: Boolean,
)

class ConfigurationTransferService(
    private val days: ImportantDayRepository,
    private val codec: TransferCodec,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
    private val clock: Clock,
) {
    suspend fun exportConfiguration(): String = codec.encode(
        TransferDocument(
            exportedAt = clock.instant(),
            days = days.getAll().map { day -> day.copy(id = 0) },
        ),
    )

    suspend fun importConfiguration(text: String): TransferImportResult {
        val decoded = codec.decode(text)
        val existing = days.getAll()
        val importDate = LocalDate.now(clock)
        val plan = ImportPlanner.plan(existing, decoded.days, importDate)

        widgets.prepareMutation()
        days.importAll(plan.days)

        var refreshFailed = refreshReminders()
        if (refreshWidgets()) refreshFailed = true

        return TransferImportResult(
            importedCount = plan.days.size,
            renamedCount = plan.renamedCount,
            refreshFailed = refreshFailed,
        )
    }

    private suspend fun refreshReminders(): Boolean = try {
        reminders.rebuildAll()
        false
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        true
    }

    private suspend fun refreshWidgets(): Boolean = try {
        widgets.updateAll()
        false
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        true
    }
}
