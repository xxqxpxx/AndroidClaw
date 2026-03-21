package com.androidclaw.shared.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DateTimeToolTest {

    private val tool = DateTimeTool()

    @Test
    fun name_isDatetime() {
        kotlin.test.assertEquals("datetime", tool.name)
    }

    @Test
    fun execute_returnsDateInfo() = runTest {
        val input = buildJsonObject { }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Current date and time:"))
        assertTrue(result.content.contains("Date:"))
        assertTrue(result.content.contains("Day:"))
        assertTrue(result.content.contains("Time:"))
        assertTrue(result.content.contains("Timezone:"))
        assertTrue(result.content.contains("Unix timestamp:"))
    }

    @Test
    fun execute_withValidTimezone() = runTest {
        val input = buildJsonObject { put("timezone", "UTC") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Timezone: UTC"))
    }

    @Test
    fun execute_withInvalidTimezone_fallsBackToSystem() = runTest {
        val input = buildJsonObject { put("timezone", "Invalid/Zone") }
        val result = tool.execute(input)
        // Should not error, falls back to system default
        assertFalse(result.isError)
        assertTrue(result.content.contains("Date:"))
    }

    @Test
    fun execute_dateFormatIsCorrect() = runTest {
        val input = buildJsonObject { }
        val result = tool.execute(input)
        // Date should be in YYYY-MM-DD format
        val dateRegex = Regex("""Date: \d{4}-\d{2}-\d{2}""")
        assertTrue(dateRegex.containsMatchIn(result.content), "Date should be in YYYY-MM-DD format")
    }

    @Test
    fun execute_timeFormatIsCorrect() = runTest {
        val input = buildJsonObject { }
        val result = tool.execute(input)
        // Time should be in HH:MM:SS format
        val timeRegex = Regex("""Time: \d{2}:\d{2}:\d{2}""")
        assertTrue(timeRegex.containsMatchIn(result.content), "Time should be in HH:MM:SS format")
    }
}
