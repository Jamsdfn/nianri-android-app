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
            root["format"]?.jsonPrimitive?.takeIf(JsonPrimitive::isString)?.content
        } catch (_: IllegalArgumentException) {
            null
        }
        if (format != FORMAT) throw TransferFormatException.NotNianriConfiguration()

        val version = try {
            root.requiredInt("version")
        } catch (error: Exception) {
            throw TransferFormatException.Corrupt(error)
        }
        if (version != VERSION) throw TransferFormatException.UnsupportedVersion(version)

        val exportedAt: Instant
        val days: List<ImportantDay>
        try {
            exportedAt = Instant.parse(root.requiredString("exportedAt"))
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
            name = objectValue.requiredString("name").trim(),
            basis = CalendarSystem.valueOf(objectValue.requiredString("basis")),
            month = objectValue.requiredInt("month"),
            day = objectValue.requiredInt("day"),
            appDisplay = CalendarSystem.valueOf(
                objectValue.requiredString("appDisplay"),
            ),
            reminders = objectValue.getValue("reminders")
                .jsonArray
                .mapTo(linkedSetOf()) { it.jsonPrimitive.strictInt() },
            reminderTimeMinutes = objectValue.requiredInt("reminderTimeMinutes"),
            isPinned = objectValue.requiredBoolean("isPinned"),
        ).also(::requireValidImportantDay)
    } catch (error: TransferFormatException) {
        throw error
    } catch (error: Exception) {
        throw TransferFormatException.InvalidDay("Invalid important day", error)
    }

    private fun JsonObject.requiredString(name: String): String {
        val primitive = getValue(name).jsonPrimitive
        require(primitive.isString) { "$name must be a string" }
        return primitive.content
    }

    private fun JsonObject.requiredInt(name: String): Int =
        getValue(name).jsonPrimitive.strictInt()

    private fun JsonPrimitive.strictInt(): Int {
        require(!isString) { "Expected a number" }
        return int
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val primitive = getValue(name).jsonPrimitive
        require(!primitive.isString) { "$name must be a boolean" }
        return primitive.boolean
    }

    private companion object {
        const val FORMAT = "nianri-configuration"
        const val VERSION = 1
    }
}
