package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class CalculatorTool : Tool {

    override val name = "calculator"

    override val description = "Evaluate mathematical expressions. Supports basic arithmetic (+, -, *, /), exponents (^), modulo (%), and parentheses."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "Mathematical expression to evaluate (e.g. '(2 + 3) * 4')")
            }
        }
        putJsonArray("required") { add("expression") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val expression = input["expression"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: expression", isError = true)

        return try {
            val result = evaluate(expression)
            // Format nicely - remove trailing .0 for integers
            val formatted = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                result.toString()
            }
            ToolResult("$expression = $formatted")
        } catch (e: Exception) {
            ToolResult("Could not evaluate '$expression': ${e.message}", isError = true)
        }
    }

    private fun evaluate(expr: String): Double {
        val cleaned = expr.replace(" ", "")
        val tokens = tokenize(cleaned)
        val result = parseExpression(tokens, 0)
        return result.first
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                c in "+-*/^%()" -> {
                    // Handle unary minus
                    if (c == '-' && (tokens.isEmpty() || tokens.last() in listOf("(", "+", "-", "*", "/", "^", "%"))) {
                        tokens.add("NEG")
                    } else {
                        tokens.add(c.toString())
                    }
                    i++
                }
                else -> i++ // skip whitespace
            }
        }
        return tokens
    }

    // Recursive descent parser
    private fun parseExpression(tokens: List<String>, pos: Int): Pair<Double, Int> {
        var (left, nextPos) = parseTerm(tokens, pos)
        var p = nextPos
        while (p < tokens.size && tokens[p] in listOf("+", "-")) {
            val op = tokens[p]
            val (right, newPos) = parseTerm(tokens, p + 1)
            left = if (op == "+") left + right else left - right
            p = newPos
        }
        return Pair(left, p)
    }

    private fun parseTerm(tokens: List<String>, pos: Int): Pair<Double, Int> {
        var (left, nextPos) = parsePower(tokens, pos)
        var p = nextPos
        while (p < tokens.size && tokens[p] in listOf("*", "/", "%")) {
            val op = tokens[p]
            val (right, newPos) = parsePower(tokens, p + 1)
            left = when (op) {
                "*" -> left * right
                "/" -> {
                    if (right == 0.0) throw ArithmeticException("Division by zero")
                    left / right
                }
                "%" -> left % right
                else -> left
            }
            p = newPos
        }
        return Pair(left, p)
    }

    private fun parsePower(tokens: List<String>, pos: Int): Pair<Double, Int> {
        var (base, nextPos) = parseUnary(tokens, pos)
        var p = nextPos
        if (p < tokens.size && tokens[p] == "^") {
            val (exp, newPos) = parsePower(tokens, p + 1) // right-associative
            base = Math.pow(base, exp)
            p = newPos
        }
        return Pair(base, p)
    }

    private fun parseUnary(tokens: List<String>, pos: Int): Pair<Double, Int> {
        if (pos < tokens.size && tokens[pos] == "NEG") {
            val (value, nextPos) = parseAtom(tokens, pos + 1)
            return Pair(-value, nextPos)
        }
        return parseAtom(tokens, pos)
    }

    private fun parseAtom(tokens: List<String>, pos: Int): Pair<Double, Int> {
        if (pos >= tokens.size) throw IllegalArgumentException("Unexpected end of expression")

        if (tokens[pos] == "(") {
            val (value, nextPos) = parseExpression(tokens, pos + 1)
            if (nextPos >= tokens.size || tokens[nextPos] != ")") {
                throw IllegalArgumentException("Missing closing parenthesis")
            }
            return Pair(value, nextPos + 1)
        }

        val num = tokens[pos].toDoubleOrNull()
            ?: throw IllegalArgumentException("Expected number, got '${tokens[pos]}'")
        return Pair(num, pos + 1)
    }
}
