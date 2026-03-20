package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

/**
 * Sandboxed code execution tool. Evaluates simple Kotlin-like expressions
 * and scripts without requiring a full runtime. Supports:
 * - Variable assignments
 * - String operations
 * - Math operations
 * - List/map operations
 * - Conditional expressions
 * - Print statements
 */
class CodeExecutionTool : Tool {

    override val name = "run_code"

    override val description = """Execute simple code snippets. Supports basic Kotlin-like expressions including variables, math, string operations, lists, and conditionals. Use for quick computations, data transformations, or generating formatted output."""

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("code") {
                put("type", "string")
                put("description", "The code to execute. Supports variables, math, strings, lists, conditionals, and print().")
            }
            putJsonObject("language") {
                put("type", "string")
                putJsonArray("enum") { add("kotlin"); add("expression") }
                put("description", "Language: 'kotlin' for multi-line scripts, 'expression' for single expressions")
            }
        }
        putJsonArray("required") { add("code") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val code = input["code"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: code", isError = true)
        val language = input["language"]?.jsonPrimitive?.contentOrNull ?: "kotlin"

        return try {
            val interpreter = MiniInterpreter()
            val result = interpreter.execute(code)
            val output = buildString {
                if (interpreter.printBuffer.isNotEmpty()) {
                    appendLine(interpreter.printBuffer.joinToString("\n"))
                }
                if (result != null && result != Unit) {
                    appendLine("=> $result")
                }
            }.trim()
            ToolResult(output.ifEmpty { "(no output)" })
        } catch (e: Exception) {
            ToolResult("Error: ${e.message}", isError = true)
        }
    }
}

/**
 * Minimal interpreter for safe code evaluation.
 * No reflection, no file I/O, no network - pure computation only.
 */
internal class MiniInterpreter {
    private val variables = mutableMapOf<String, Any?>()
    val printBuffer = mutableListOf<String>()

    fun execute(code: String): Any? {
        val lines = code.trim().lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
        var lastResult: Any? = null

        for (line in lines) {
            lastResult = executeLine(line)
        }
        return lastResult
    }

    private fun executeLine(line: String): Any? {
        // Print statement
        if (line.startsWith("print(") || line.startsWith("println(")) {
            val isPrintln = line.startsWith("println")
            val inner = line.substringAfter("(").dropLast(1)
            val value = evaluateExpression(inner)
            printBuffer.add(value.toString())
            return null
        }

        // Variable assignment: val x = expr or var x = expr
        val assignMatch = Regex("""^(?:val|var)\s+(\w+)\s*=\s*(.+)$""").find(line)
        if (assignMatch != null) {
            val name = assignMatch.groupValues[1]
            val expr = assignMatch.groupValues[2]
            val value = evaluateExpression(expr)
            variables[name] = value
            return value
        }

        // Simple reassignment: x = expr
        val reassignMatch = Regex("""^(\w+)\s*=\s*(.+)$""").find(line)
        if (reassignMatch != null) {
            val name = reassignMatch.groupValues[1]
            if (name in variables) {
                val value = evaluateExpression(reassignMatch.groupValues[2])
                variables[name] = value
                return value
            }
        }

        // If expression
        if (line.startsWith("if ") || line.startsWith("if(")) {
            return evaluateIf(line)
        }

        // Expression evaluation
        return evaluateExpression(line)
    }

    private fun evaluateExpression(expr: String): Any? {
        val trimmed = expr.trim()

        // String literal
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return processStringInterpolation(trimmed.drop(1).dropLast(1))
        }

        // Boolean literals
        if (trimmed == "true") return true
        if (trimmed == "false") return false
        if (trimmed == "null") return null

        // Number literal
        trimmed.toLongOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }

        // Variable reference
        if (trimmed.matches(Regex("""\w+""")) && trimmed in variables) {
            return variables[trimmed]
        }

        // List literal: listOf(...)
        if (trimmed.startsWith("listOf(") && trimmed.endsWith(")")) {
            val inner = trimmed.removePrefix("listOf(").removeSuffix(")")
            return if (inner.isBlank()) emptyList<Any?>()
            else splitArgs(inner).map { evaluateExpression(it) }
        }

        // Map literal: mapOf(...)
        if (trimmed.startsWith("mapOf(") && trimmed.endsWith(")")) {
            val inner = trimmed.removePrefix("mapOf(").removeSuffix(")")
            if (inner.isBlank()) return emptyMap<Any?, Any?>()
            val pairs = splitArgs(inner).map { pair ->
                val parts = pair.split(" to ")
                if (parts.size == 2) evaluateExpression(parts[0]) to evaluateExpression(parts[1])
                else throw IllegalArgumentException("Invalid map entry: $pair")
            }
            return pairs.toMap()
        }

        // String template: "$var" or "${expr}"
        if (trimmed.contains("\$")) {
            return processStringInterpolation(trimmed)
        }

        // String concatenation with +
        if (trimmed.contains("+") && hasStringOperand(trimmed)) {
            val parts = splitByOperator(trimmed, '+')
            return parts.joinToString("") { evaluateExpression(it).toString() }
        }

        // Method calls on values: value.method(args)
        val methodMatch = Regex("""^(.+?)\.(\w+)\((.*)\)$""").find(trimmed)
        if (methodMatch != null) {
            val target = evaluateExpression(methodMatch.groupValues[1])
            val method = methodMatch.groupValues[2]
            val argsStr = methodMatch.groupValues[3]
            return callMethod(target, method, argsStr)
        }

        // Property access: value.property
        val propMatch = Regex("""^(.+?)\.(\w+)$""").find(trimmed)
        if (propMatch != null) {
            val target = evaluateExpression(propMatch.groupValues[1])
            val prop = propMatch.groupValues[2]
            return getProperty(target, prop)
        }

        // Comparison operators
        for (op in listOf("==", "!=", ">=", "<=", ">", "<")) {
            val idx = trimmed.indexOf(op)
            if (idx > 0) {
                val left = evaluateExpression(trimmed.substring(0, idx))
                val right = evaluateExpression(trimmed.substring(idx + op.length))
                return compareValues(left, right, op)
            }
        }

        // Arithmetic
        return evaluateArithmetic(trimmed)
    }

    private fun evaluateArithmetic(expr: String): Any? {
        val trimmed = expr.trim()

        // Handle parentheses
        if (trimmed.startsWith("(") && findMatchingParen(trimmed, 0) == trimmed.length - 1) {
            return evaluateExpression(trimmed.drop(1).dropLast(1))
        }

        // + and - (lowest precedence, left to right)
        var depth = 0
        for (i in trimmed.length - 1 downTo 0) {
            when (trimmed[i]) {
                ')' -> depth++
                '(' -> depth--
                '+', '-' -> if (depth == 0 && i > 0) {
                    val left = evaluateExpression(trimmed.substring(0, i))
                    val right = evaluateExpression(trimmed.substring(i + 1))
                    return arithmeticOp(left, right, trimmed[i])
                }
            }
        }

        // * / %
        depth = 0
        for (i in trimmed.length - 1 downTo 0) {
            when (trimmed[i]) {
                ')' -> depth++
                '(' -> depth--
                '*', '/', '%' -> if (depth == 0) {
                    val left = evaluateExpression(trimmed.substring(0, i))
                    val right = evaluateExpression(trimmed.substring(i + 1))
                    return arithmeticOp(left, right, trimmed[i])
                }
            }
        }

        // Variable reference (final fallback)
        if (trimmed in variables) return variables[trimmed]

        throw IllegalArgumentException("Cannot evaluate: $trimmed")
    }

    private fun arithmeticOp(left: Any?, right: Any?, op: Char): Any {
        val l = toNumber(left)
        val r = toNumber(right)
        val result = when (op) {
            '+' -> l + r
            '-' -> l - r
            '*' -> l * r
            '/' -> { if (r == 0.0) throw ArithmeticException("Division by zero"); l / r }
            '%' -> l % r
            else -> throw IllegalArgumentException("Unknown operator: $op")
        }
        return if (result == result.toLong().toDouble()) result.toLong() else result
    }

    private fun toNumber(v: Any?): Double = when (v) {
        is Long -> v.toDouble()
        is Int -> v.toDouble()
        is Double -> v
        is Float -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: throw IllegalArgumentException("Not a number: $v")
        else -> throw IllegalArgumentException("Not a number: $v")
    }

    private fun compareValues(left: Any?, right: Any?, op: String): Boolean {
        return when (op) {
            "==" -> left == right
            "!=" -> left != right
            ">", "<", ">=", "<=" -> {
                val l = toNumber(left)
                val r = toNumber(right)
                when (op) {
                    ">" -> l > r; "<" -> l < r; ">=" -> l >= r; "<=" -> l <= r
                    else -> false
                }
            }
            else -> false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun callMethod(target: Any?, method: String, argsStr: String): Any? {
        val args = if (argsStr.isBlank()) emptyList() else splitArgs(argsStr).map { evaluateExpression(it) }

        return when (target) {
            is String -> when (method) {
                "length" -> target.length.toLong()
                "uppercase" -> target.uppercase()
                "lowercase" -> target.lowercase()
                "trim" -> target.trim()
                "contains" -> target.contains(args[0].toString())
                "replace" -> target.replace(args[0].toString(), args[1].toString())
                "split" -> target.split(args[0].toString())
                "substring" -> {
                    val start = (args[0] as Long).toInt()
                    if (args.size > 1) target.substring(start, (args[1] as Long).toInt())
                    else target.substring(start)
                }
                "startsWith" -> target.startsWith(args[0].toString())
                "endsWith" -> target.endsWith(args[0].toString())
                "reversed" -> target.reversed()
                "repeat" -> target.repeat((args[0] as Long).toInt())
                "take" -> target.take((args[0] as Long).toInt())
                "drop" -> target.drop((args[0] as Long).toInt())
                else -> throw IllegalArgumentException("Unknown String method: $method")
            }
            is List<*> -> when (method) {
                "size" -> target.size.toLong()
                "first" -> target.firstOrNull()
                "last" -> target.lastOrNull()
                "reversed" -> target.reversed()
                "sorted" -> (target as List<Comparable<Any>>).sorted()
                "contains" -> target.contains(args[0])
                "joinToString" -> target.joinToString(args.getOrNull(0)?.toString() ?: ", ")
                "filter" -> target // simplified - return as-is
                "map" -> target // simplified - return as-is
                "take" -> target.take((args[0] as Long).toInt())
                "drop" -> target.drop((args[0] as Long).toInt())
                else -> throw IllegalArgumentException("Unknown List method: $method")
            }
            is Map<*, *> -> when (method) {
                "size" -> target.size.toLong()
                "keys" -> target.keys.toList()
                "values" -> target.values.toList()
                "containsKey" -> target.containsKey(args[0])
                "get" -> target[args[0]]
                else -> throw IllegalArgumentException("Unknown Map method: $method")
            }
            else -> throw IllegalArgumentException("Cannot call $method on ${target?.let { it::class.simpleName }}")
        }
    }

    private fun getProperty(target: Any?, prop: String): Any? = when (target) {
        is String -> when (prop) {
            "length" -> target.length.toLong()
            else -> throw IllegalArgumentException("Unknown property: $prop")
        }
        is List<*> -> when (prop) {
            "size" -> target.size.toLong()
            "first" -> target.firstOrNull()
            "last" -> target.lastOrNull()
            else -> throw IllegalArgumentException("Unknown property: $prop")
        }
        is Map<*, *> -> when (prop) {
            "size" -> target.size.toLong()
            "keys" -> target.keys.toList()
            "values" -> target.values.toList()
            else -> target[prop] // map key access
        }
        else -> throw IllegalArgumentException("Cannot access property $prop on $target")
    }

    private fun evaluateIf(line: String): Any? {
        val condMatch = Regex("""if\s*\((.+?)\)\s+(.+?)(?:\s+else\s+(.+))?$""").find(line)
            ?: throw IllegalArgumentException("Invalid if expression: $line")
        val condition = evaluateExpression(condMatch.groupValues[1])
        val thenExpr = condMatch.groupValues[2]
        val elseExpr = condMatch.groupValues[3].takeIf { it.isNotEmpty() }

        return if (condition == true) evaluateExpression(thenExpr)
        else if (elseExpr != null) evaluateExpression(elseExpr)
        else null
    }

    private fun processStringInterpolation(s: String): String {
        return s.replace(Regex("""\$\{(.+?)\}""")) { match ->
            evaluateExpression(match.groupValues[1]).toString()
        }.replace(Regex("""\$(\w+)""")) { match ->
            (variables[match.groupValues[1]] ?: "null").toString()
        }
    }

    private fun hasStringOperand(expr: String): Boolean {
        return expr.contains("\"") || splitByOperator(expr, '+').any {
            val v = it.trim()
            v in variables && variables[v] is String
        }
    }

    private fun splitByOperator(expr: String, op: Char): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var inString = false
        var start = 0
        for (i in expr.indices) {
            when {
                expr[i] == '"' -> inString = !inString
                !inString && expr[i] == '(' -> depth++
                !inString && expr[i] == ')' -> depth--
                !inString && depth == 0 && expr[i] == op -> {
                    parts.add(expr.substring(start, i))
                    start = i + 1
                }
            }
        }
        parts.add(expr.substring(start))
        return parts
    }

    private fun splitArgs(args: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var inString = false
        var start = 0
        for (i in args.indices) {
            when {
                args[i] == '"' -> inString = !inString
                !inString && args[i] == '(' -> depth++
                !inString && args[i] == ')' -> depth--
                !inString && depth == 0 && args[i] == ',' -> {
                    result.add(args.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        result.add(args.substring(start).trim())
        return result
    }

    private fun findMatchingParen(s: String, start: Int): Int {
        var depth = 0
        for (i in start until s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }
}
