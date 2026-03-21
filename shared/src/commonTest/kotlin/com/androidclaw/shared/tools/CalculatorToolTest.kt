package com.androidclaw.shared.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculatorToolTest {

    private val tool = CalculatorTool()

    @Test
    fun name_isCalculator() {
        assertEquals("calculator", tool.name)
    }

    @Test
    fun basicAddition() = runTest {
        val input = buildJsonObject { put("expression", "2 + 3") }
        val result = tool.execute(input)
        assertEquals("2 + 3 = 5", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun basicSubtraction() = runTest {
        val input = buildJsonObject { put("expression", "10 - 4") }
        val result = tool.execute(input)
        assertEquals("10 - 4 = 6", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun basicMultiplication() = runTest {
        val input = buildJsonObject { put("expression", "6 * 7") }
        val result = tool.execute(input)
        assertEquals("6 * 7 = 42", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun basicDivision() = runTest {
        val input = buildJsonObject { put("expression", "15 / 3") }
        val result = tool.execute(input)
        assertEquals("15 / 3 = 5", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun decimalDivision() = runTest {
        val input = buildJsonObject { put("expression", "7 / 2") }
        val result = tool.execute(input)
        assertEquals("7 / 2 = 3.5", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun modulo() = runTest {
        val input = buildJsonObject { put("expression", "10 % 3") }
        val result = tool.execute(input)
        assertEquals("10 % 3 = 1", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun exponent() = runTest {
        val input = buildJsonObject { put("expression", "2^10") }
        val result = tool.execute(input)
        assertEquals("2^10 = 1024", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun parentheses() = runTest {
        val input = buildJsonObject { put("expression", "(2 + 3) * 4") }
        val result = tool.execute(input)
        assertEquals("(2 + 3) * 4 = 20", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun operatorPrecedence() = runTest {
        val input = buildJsonObject { put("expression", "2 + 3 * 4") }
        val result = tool.execute(input)
        assertEquals("2 + 3 * 4 = 14", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun nestedParentheses() = runTest {
        val input = buildJsonObject { put("expression", "((2 + 3) * (4 - 1))") }
        val result = tool.execute(input)
        assertEquals("((2 + 3) * (4 - 1)) = 15", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun unaryMinus() = runTest {
        val input = buildJsonObject { put("expression", "-5 + 3") }
        val result = tool.execute(input)
        assertEquals("-5 + 3 = -2", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun divisionByZero() = runTest {
        val input = buildJsonObject { put("expression", "5 / 0") }
        val result = tool.execute(input)
        assertTrue(result.isError)
        assertTrue(result.content.contains("Division by zero"))
    }

    @Test
    fun missingExpression() = runTest {
        val input = buildJsonObject { }
        val result = tool.execute(input)
        assertTrue(result.isError)
        assertTrue(result.content.contains("Missing required parameter"))
    }

    @Test
    fun decimalNumbers() = runTest {
        val input = buildJsonObject { put("expression", "3.14 * 2") }
        val result = tool.execute(input)
        assertEquals("3.14 * 2 = 6.28", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun inputSchema_hasRequiredExpression() {
        val schema = tool.inputSchema
        assertTrue(schema.containsKey("properties"))
        assertTrue(schema.containsKey("required"))
    }
}
