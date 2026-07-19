package com.nianri.app.data.transfer

import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.requireValidImportantDay
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class TransferCodec(
    private val json: Json = Json,
) {
    fun encode(document: TransferDocument): String = buildJsonObject {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", document.exportedAt.toString())
        put("days", buildJsonArray {
            document.days.forEach { day ->
                add(buildJsonObject {
                    put("name", day.name)
                    put("basis", day.basis.name)
                    put("month", day.month)
                    put("day", day.day)
                    put("appDisplay", day.appDisplay.name)
                    put("reminders", buildJsonArray {
                        day.reminders.sortedDescending().forEach { reminder ->
                            add(JsonPrimitive(reminder))
                        }
                    })
                    put("reminderTimeMinutes", day.reminderTimeMinutes)
                    put("isPinned", day.isPinned)
                })
            }
        })
    }.toString()

    fun decode(text: String): TransferDocument {
        val root = parseRoot(text)
        val format = try {
            root["format"]?.jsonPrimitive?.content
        } catch (_: IllegalArgumentException) {
            null
        }
        if (format != FORMAT) throw TransferFormatException.NotNianriConfiguration()

        val version = try {
            root.getValue("version").jsonPrimitive.int
        } catch (error: Exception) {
            throw TransferFormatException.Corrupt(error)
        }
        if (version != VERSION) throw TransferFormatException.UnsupportedVersion(version)

        val exportedAt: Instant
        val days: List<ImportantDay>
        try {
            exportedAt = Instant.parse(root.getValue("exportedAt").jsonPrimitive.content)
            days = root.getValue("days").jsonArray.map(::decodeDay)
        } catch (error: TransferFormatException) {
            throw error
        } catch (error: Exception) {
            throw TransferFormatException.Corrupt(error)
        }
        if (days.count(ImportantDay::isPinned) > 1) {
            throw TransferFormatException.InvalidDay("Only one day can be pinned")
        }
        return TransferDocument(exportedAt = exportedAt, days = days)
    }

    private fun parseRoot(text: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObject
    } catch (error: Exception) {
        throw TransferFormatException.Corrupt(error)
    }

    private fun decodeDay(element: kotlinx.serialization.json.JsonElement): ImportantDay = try {
        val objectValue = element.jsonObject
        ImportantDay(
            name = objectValue.getValue("name").jsonPrimitive.content.trim(),
            basis = CalendarSystem.valueOf(objectValue.getValue("basis").jsonPrimitive.content),
            month = objectValue.getValue("month").jsonPrimitive.int,
            day = objectValue.getValue("day").jsonPrimitive.int,
            appDisplay = CalendarSystem.valueOf(
                objectValue.getValue("appDisplay").jsonPrimitive.content,
            ),
            reminders = objectValue.getValue("reminders")
                .jsonArray
                .mapTo(linkedSetOf()) { it.jsonPrimitive.int },
            reminderTimeMinutes = objectValue.getValue("reminderTimeMinutes").jsonPrimitive.int,
            isPinned = objectValue.getValue("isPinned").jsonPrimitive.boolean,
        ).also(::requireValidImportantDay)
    } catch (error: TransferFormatException) {
        throw error
    } catch (error: Exception) {
        throw TransferFormatException.InvalidDay("Invalid important day", error)
    }

    private companion object {
        const val FORMAT = "nianri-configuration"
        const val VERSION = 1
    }
}
