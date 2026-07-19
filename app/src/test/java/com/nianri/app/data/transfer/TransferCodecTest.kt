package com.nianri.app.data.transfer

import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCodecTest {
    private val codec = TransferCodec()

    @Test
    fun `version one round trip preserves every business field`() {
        val source = TransferDocument(
            exportedAt = Instant.parse("2026-07-19T09:30:00Z"),
            days = listOf(
                ImportantDay(
                    name = "结婚纪念日",
                    basis = CalendarSystem.LUNAR,
                    month = 8,
                    day = 15,
                    appDisplay = CalendarSystem.SOLAR,
                    reminders = setOf(14, 3),
                    reminderTimeMinutes = 20 * 60 + 5,
                    isPinned = true,
                ),
            ),
        )

        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)

        assertEquals(source, decoded)
        assertTrue(encoded.contains("\"format\":\"nianri-configuration\""))
        assertFalse(encoded.contains("\"id\""))
        assertFalse(encoded.contains("createdAt"))
        assertFalse(encoded.contains("updatedAt"))
    }

    @Test
    fun `unknown version is rejected`() {
        val error = assertThrows(TransferFormatException.UnsupportedVersion::class.java) {
            codec.decode(
                """{"format":"nianri-configuration","version":2,"exportedAt":"2026-07-19T09:30:00Z","days":[]}""",
            )
        }

        assertEquals(2, error.version)
    }

    @Test
    fun `two pinned days are rejected`() {
        val json = documentJson(
            dayJson("A", pinned = true),
            dayJson("B", pinned = true),
        )

        assertThrows(TransferFormatException.InvalidDay::class.java) {
            codec.decode(json)
        }
    }

    @Test
    fun `arbitrary json is rejected as not a nianri configuration`() {
        assertThrows(TransferFormatException.NotNianriConfiguration::class.java) {
            codec.decode("""{"format":"another-app","version":1}""")
        }
    }

    @Test
    fun `invalid important day is rejected`() {
        val json = documentJson(dayJson(" ", pinned = false))

        assertThrows(TransferFormatException.InvalidDay::class.java) {
            codec.decode(json)
        }
    }

    private fun documentJson(vararg days: String): String =
        """{"format":"nianri-configuration","version":1,"exportedAt":"2026-07-19T09:30:00Z","days":[${days.joinToString()}]}"""

    private fun dayJson(name: String, pinned: Boolean): String =
        """{"name":"$name","basis":"SOLAR","month":8,"day":6,"appDisplay":"SOLAR","reminders":[14,7,3],"reminderTimeMinutes":540,"isPinned":$pinned}"""
}
