package com.autopilot.driver.data.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the last server-approved mode list available when the device is
 * temporarily offline. Only the published JSON payload is cached; account
 * credentials remain in AutopilotSessionStore.
 */
class AutopilotModeCache(private val context: Context) {

    fun saveModes(modes: List<ScenarioMode>) {
        val payload = JSONArray().apply {
            modes.forEach { mode ->
                put(
                    JSONObject()
                        .put("id", mode.id)
                        .put("name", mode.name)
                        .put("description", mode.description)
                        .put("version", mode.version)
                        .put("scenario_data", mode.scenarioData),
                )
            }
        }
        preferences.edit().putString(KEY_MODES, payload.toString()).apply()
    }

    fun loadModes(): List<ScenarioMode> {
        val payload = preferences.getString(KEY_MODES, null) ?: return emptyList()
        val rows = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        return buildList(minOf(rows.length(), MAX_CACHED_MODES)) {
            for (index in 0 until minOf(rows.length(), MAX_CACHED_MODES)) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = row.optString("name").trim().takeIf {
                    it.isNotBlank() && it.length <= MAX_MODE_NAME_LENGTH
                } ?: continue
                val scenarioData = row.optJSONObject("scenario_data") ?: continue
                if (!ScenarioValidator.validate(scenarioData)) continue
                add(
                    ScenarioMode(
                        id = id,
                        name = name,
                        description = row.optString("description")
                            .trim()
                            .takeIf { it.isNotBlank() && it != "null" },
                        version = row.optInt("version", 1).coerceAtLeast(1),
                        scenarioData = scenarioData,
                    ),
                )
            }
        }
    }

    fun saveSelectedMode(mode: ScenarioMode) {
        preferences.edit()
            .putString(KEY_SELECTED_ID, mode.id)
            .putString(KEY_SELECTED_NAME, mode.name)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private companion object {
        const val PREFERENCES = "autopilot_modes"
        const val KEY_MODES = "published_modes"
        const val KEY_SELECTED_ID = "selected_mode_id"
        const val KEY_SELECTED_NAME = "selected_mode_name"
        const val MAX_CACHED_MODES = 15
        const val MAX_MODE_NAME_LENGTH = 80
    }
}
