package com.androidclaw.shared.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodeExecutionToolTest {

    private val tool = CodeExecutionTool()

    @Test
    fun name_isRunCode() {
        kotlin.test.assertEquals("run_code", tool.name)
    }

    @Test
    fun execute_missingCode_returnsError() = runTest {
        val result = tool.execute(buildJsonObject { })
        assertTrue(result.isError)
        assertTrue(result.content.contains("Missing required parameter"))
    }

    @Test
    fun execute_simpleExpression() = runTest {
        val input = buildJsonObject { put("code", "2 + 3") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("5"))
    }

    @Test
    fun execute_withPrint() = runTest {
        val input = buildJsonObject { put("code", "println(\"hello\")") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("hello"))
    }

    @Test
    fun execute_withPrintAndResult() = runTest {
        val input = buildJsonObject {
            put("code", """
                println("output")
                42
            """.trimIndent())
        }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("output"))
        assertTrue(result.content.contains("42"))
    }

    @Test
    fun execute_variablesAndExpression() = runTest {
        val input = buildJsonObject {
            put("code", """
                val x = 10
                val y = 20
                x + y
            """.trimIndent())
        }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("30"))
    }

    @Test
    fun execute_noOutput_returnsNoOutput() = runTest {
        val input = buildJsonObject { put("code", "val x = 10") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        // val assignment returns the value, so it should show 10
        assertTrue(result.content.contains("10"))
    }
}
