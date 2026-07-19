package com.nianri.app.data.transfer

import com.nianri.app.domain.model.ImportantDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ImportPlan(
    val days: List<ImportantDay>,
    val renamedCount: Int,
)

object ImportPlanner {
    fun plan(
        existing: List<ImportantDay>,
        incoming: List<ImportantDay>,
        importDate: LocalDate,
    ): ImportPlan {
        val occupied = existing.mapTo(linkedSetOf()) { it.name.trim() }
        val keepIncomingPin = existing.none(ImportantDay::isPinned)
        var renamed = 0
        val planned = incoming.map { source ->
            val base = source.name.trim()
            val finalName = if (occupied.add(base)) {
                base
            } else {
                renamed += 1
                uniqueImportedName(base, importDate, occupied)
            }
            source.copy(
                id = 0,
                name = finalName,
                isPinned = keepIncomingPin && source.isPinned,
            )
        }
        return ImportPlan(days = planned, renamedCount = renamed)
    }

    private fun uniqueImportedName(
        base: String,
        date: LocalDate,
        occupied: MutableSet<String>,
    ): String {
        val stem = "$base-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}导入"
        if (occupied.add(stem)) return stem

        var sequence = 2
        while (!occupied.add("$stem-$sequence")) {
            sequence += 1
        }
        return "$stem-$sequence"
    }
}
