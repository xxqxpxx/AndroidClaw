package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

/**
 * Tool that gives the agent eyes: captures the screen, analyzes it via vision,
 * and can tap elements by visual recognition when accessibility tree fails.
 *
 * Actions:
 * - describe: capture + describe the current screen
 * - find_and_tap: locate a UI element by description and tap it
 * - tap_coordinates: tap at specific x,y coordinates
 * - scroll: scroll up/down/left/right
 * - recovery: attempt automatic stuck recovery
 * - status: get action executor state (step count, stuck status)
 */
class ScreenAnalysisTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "screen_vision"

    override val description = """Use vision to analyze and interact with the screen when accessibility tree fails.
        |Actions:
        | - describe: capture a screenshot and describe what's visible (app, content, buttons)
        | - find_and_tap: find a UI element by visual description and tap it (e.g. "the blue Send button", "hamburger menu icon")
        | - tap_coordinates: tap at exact x,y pixel coordinates (use after describe tells you where something is)
        | - scroll: scroll the screen (direction: up/down/left/right)
        | - go_back: press the system Back button
        | - recovery: attempt automatic recovery when stuck (scrolls, then presses back)
        | - status: check how many steps used and if stuck
        |
        |Use this when:
        | - tapScreenButton can't find the element via accessibility tree
        | - You need to understand what's on screen before acting
        | - You're navigating an unfamiliar app
        | - Standard tools report "couldn't find" errors
        |
        |The vision system has safety limits:
        | - Max 30 steps per goal (resets between user requests)
        | - Stuck detection: same screen 3x = forced recovery
        | - Repeat detection: same coordinates tapped 3x = forced alternative
        |These prevent infinite retry loops.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Vision action to perform")
                putJsonArray("enum") {
                    add("describe"); add("find_and_tap"); add("tap_coordinates")
                    add("scroll"); add("go_back"); add("recovery"); add("status")
                }
            }
            putJsonObject("target") {
                put("type", "string")
                put("description", "For find_and_tap: description of element to find (e.g. 'Send button', 'search icon', 'the price field')")
            }
            putJsonObject("x") {
                put("type", "integer")
                put("description", "X coordinate for tap_coordinates")
            }
            putJsonObject("y") {
                put("type", "integer")
                put("description", "Y coordinate for tap_coordinates")
            }
            putJsonObject("direction") {
                put("type", "string")
                put("description", "For scroll: up, down, left, right")
                putJsonArray("enum") { add("up"); add("down"); add("left"); add("right") }
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "describe" -> bridge.visionDescribeScreen()
            "find_and_tap" -> {
                val target = input["target"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'target' — describe what element to find and tap", isError = true)
                bridge.visionFindAndTap(target)
            }
            "tap_coordinates" -> {
                val x = input["x"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing 'x' coordinate", isError = true)
                val y = input["y"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing 'y' coordinate", isError = true)
                bridge.visionTapCoordinates(x, y)
            }
            "scroll" -> {
                val direction = input["direction"]?.jsonPrimitive?.contentOrNull ?: "down"
                bridge.visionScroll(direction)
            }
            "go_back" -> bridge.visionGoBack()
            "recovery" -> bridge.visionRecovery()
            "status" -> bridge.visionStatus()
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Vision failed: ${it.message}", isError = true) }
        )
    }
}
