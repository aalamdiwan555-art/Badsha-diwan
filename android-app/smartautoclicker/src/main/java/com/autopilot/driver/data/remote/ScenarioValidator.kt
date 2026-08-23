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

    fun validate(data: JSONObject): Boolean {
        if (data.toString().toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) return false
        val actions = data.optJSONArray("actions") ?: return false
        if (actions.length() > MAX_ACTIONS) return false
        for (index in 0 until actions.length()) {
            val action = actions.opt(index) as? JSONObject ?: return false
            val type = action.optString("type").trim()
            if (type.isBlank() || type.length > 64) return false
            if (!hasFiniteNumbers(action)) return false
        }
        return true
    }

    private fun hasFiniteNumbers(value: Any?): Boolean = when (value) {
        is JSONObject -> value.keys().asSequence().all { hasFiniteNumbers(value.opt(it)) }
        is JSONArray -> (0 until value.length()).all { hasFiniteNumbers(value.opt(it)) }
        is Number -> value.toDouble().isFinite()
        else -> true
    }
}