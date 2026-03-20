package com.androidclaw.shared.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniInterpreterTest {

    private fun eval(code: String): Pair<Any?, List<String>> {
        val interpreter = MiniInterpreter()
        val result = interpreter.execute(code)
        return result to interpreter.printBuffer
    }

    @Test
    fun arithmetic_addition() {
        val (result, _) = eval("2 + 3")
        assertEquals(5L, result)
    }

    @Test
    fun arithmetic_multiplication() {
        val (result, _) = eval("4 * 7")
        assertEquals(28L, result)
    }

    @Test
    fun arithmetic_division() {
        val (result, _) = eval("10 / 3")
        assertTrue(result is Double || result is Long)
    }

    @Test
    fun arithmetic_modulo() {
        val (result, _) = eval("10 % 3")
        assertEquals(1L, result)
    }

    @Test
    fun arithmetic_complexExpression() {
        val (result, _) = eval("2 + 3 * 4")
        assertEquals(14L, result)
    }

    @Test
    fun variables_valAssignment() {
        val (result, _) = eval("""
            val x = 42
            x
        """.trimIndent())
        assertEquals(42L, result)
    }

    @Test
    fun variables_expressionAssignment() {
        val (result, _) = eval("""
            val a = 10
            val b = 20
            a + b
        """.trimIndent())
        assertEquals(30L, result)
    }

    @Test
    fun strings_literal() {
        val (result, _) = eval(""""hello"""")
        assertEquals("hello", result)
    }

    @Test
    fun strings_concatenation() {
        val (result, _) = eval(""""hello" + " " + "world"""")
        assertEquals("hello world", result)
    }

    @Test
    fun strings_interpolation() {
        val (result, _) = eval("""
            val name = "World"
            "Hello ${'$'}name"
        """.trimIndent())
        assertEquals("Hello World", result)
    }

    @Test
    fun strings_methods() {
        val (result, _) = eval(""""hello".uppercase()""")
        assertEquals("HELLO", result)
    }

    @Test
    fun strings_length() {
        val (result, _) = eval(""""hello".length""")
        assertEquals(5L, result)
    }

    @Test
    fun lists_creation() {
        val (result, _) = eval("listOf(1, 2, 3)")
        assertEquals(listOf(1L, 2L, 3L), result)
    }

    @Test
    fun lists_size() {
        val (result, _) = eval("listOf(1, 2, 3).size")
        assertEquals(3L, result)
    }

    @Test
    fun lists_joinToString() {
        val (result, _) = eval("""listOf(1, 2, 3).joinToString(", ")""")
        assertEquals("1, 2, 3", result)
    }

    @Test
    fun maps_creation() {
        val (result, _) = eval("""mapOf("a" to 1, "b" to 2)""")
        assertTrue(result is Map<*, *>)
        assertEquals(1L, (result as Map<*, *>)["a"])
    }

    @Test
    fun booleans_comparison() {
        val (result, _) = eval("5 > 3")
        assertEquals(true, result)
    }

    @Test
    fun booleans_equality() {
        val (result, _) = eval("10 == 10")
        assertEquals(true, result)
    }

    @Test
    fun print_capturesOutput() {
        val (_, output) = eval("""
            println("hello")
            println("world")
        """.trimIndent())
        assertEquals(listOf("hello", "world"), output)
    }

    @Test
    fun ifExpression_true() {
        val (result, _) = eval("if (5 > 3) 1 else 0")
        assertEquals(1L, result)
    }

    @Test
    fun ifExpression_false() {
        val (result, _) = eval("if (3 > 5) 1 else 0")
        assertEquals(0L, result)
    }

    @Test
    fun comments_ignored() {
        val (result, _) = eval("""
            // This is a comment
            val x = 42
            x
        """.trimIndent())
        assertEquals(42L, result)
    }
}
