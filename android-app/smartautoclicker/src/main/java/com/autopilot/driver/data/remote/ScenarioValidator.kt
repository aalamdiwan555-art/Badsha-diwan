package com.autopilot.driver.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Validates the portable scenario format before an administrator publishes it.
 * The engine-specific fields remain extensible, but malformed action entries
 * and unbounded payloads are rejected at the publishing boundary.
 */
object ScenarioValidator {
    private const val MAX_ACTIONS = 500
    private const val MAX_JSON_BYTES = 256 * 1024
    private const val MAX_DEPTH = 12
    private const val MAX_NAME_LENGTH = 80
    private const val MAX_SCREEN_COORDINATE = 20_000
    private const val MAX_DURATION_MS = 3_600_000L

    fun validate(data: JSONObject): Boolean {
        if (data.toString().toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) return false
        val name = data.optString("name").trim()
        if (name.isNotEmpty() && name.length > MAX_NAME_LENGTH) return false
        val actions = data.optJSONArray("actions") ?: return false
        if (actions.length() > MAX_ACTIONS) return false
        for (index in 0 until actions.length()) {
            val action = actions.opt(index) as? JSONObject ?: return false
            val type = action.optString("type").trim()
            if (type.isBlank() || type.length > 64) return false
            if (!hasExecutableFields(action, type.lowercase())) return false
            if (!hasFiniteNumbers(action, 0)) return false
        }
        return true
    }

    private fun hasExecutableFields(action: JSONObject, type: String): Boolean =
        when (type) {
            "click", "tap" ->
                hasCoordinate(action, "x") && hasCoordinate(action, "y")
            "swipe" ->
                hasCoordinate(action, "from_x", "fromX", "start_x", "startX") &&
                    hasCoordinate(action, "from_y", "fromY", "start_y", "startY") &&
                    hasCoordinate(action, "to_x", "toX", "end_x", "endX") &&
                    hasCoordinate(action, "to_y", "toY", "end_y", "endY")
            "pause", "delay" -> hasDuration(action, "duration_ms", 500L)
            else -> false
        }

    private fun hasCoordinate(action: JSONObject, vararg keys: String): Boolean {
        val key = keys.firstOrNull(action::has) ?: return false
        val value = action.optDouble(key, Double.NaN)
        return value.isFinite() && value in 0.0..MAX_SCREEN_COORDINATE.toDouble()
    }

    private fun hasDuration(action: JSONObject, key: String, default: Long): Boolean {
        val value = when {
            action.has(key) -> action.optDouble(key, Double.NaN)
            action.has("duration") -> action.optDouble("duration", Double.NaN)
            else -> default.toDouble()
        }
        return value.isFinite() && value in 1.0..MAX_DURATION_MS.toDouble()
    }

    private fun hasFiniteNumbers(value: Any?, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        return when (value) {
        is JSONObject -> value.keys().asSequence().all { hasFiniteNumbers(value.opt(it), depth + 1) }
        is JSONArray -> (0 until value.length()).all { hasFiniteNumbers(value.opt(it), depth + 1) }
        is Number -> value.toDouble().isFinite()
        else -> true
        }
    }
}