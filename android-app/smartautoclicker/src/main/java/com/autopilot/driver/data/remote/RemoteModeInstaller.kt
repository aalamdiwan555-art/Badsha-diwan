package com.autopilot.driver.data.remote

import android.graphics.Point
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Converts the small portable mode format into the foundation's local dumb
 * scenario format. Unsupported action types fail loudly instead of silently
 * running a different scenario.
 */
class RemoteModeInstaller(
    private val dumbRepository: IDumbRepository,
) {
    suspend fun install(mode: ScenarioMode): String {
        val displayName = "$LOCAL_PREFIX${mode.name}"
        val existing = dumbRepository.dumbScenarios.first()
            .firstOrNull { it.name == displayName }
        val scenarioId = existing?.id ?: Identifier(mode.id.hashCode().toLong(), asTemporary = true)
        val actions = parseActions(mode.scenarioData, scenarioId)
        check(actions.isNotEmpty()) { "The selected mode has no executable actions." }

        val scenario = DumbScenario(
            id = scenarioId,
            name = displayName,
            dumbActions = actions,
            repeatCount = 1,
            isRepeatInfinite = false,
            maxDurationMin = 1440,
            isDurationInfinite = true,
            randomize = false,
        )
        if (existing == null) dumbRepository.addDumbScenario(scenario)
        else dumbRepository.updateDumbScenario(scenario.copy(id = existing.id))

        return displayName
    }

    private fun parseActions(data: JSONObject, scenarioId: Identifier): List<DumbAction> {
        val actions = data.getJSONArray("actions")
        return buildList(actions.length()) {
            for (index in 0 until actions.length()) {
                val action = actions.getJSONObject(index)
                val actionId = Identifier(index + 1L, asTemporary = true)
                val type = action.getString("type").trim().lowercase()
                val name = action.optString("name").trim()
                    .ifBlank { "${type.replaceFirstChar { it.uppercase() }} ${index + 1}" }
                when (type) {
                    "click", "tap" -> add(
                        DumbAction.DumbClick(
                            id = actionId,
                            scenarioId = scenarioId,
                            name = name,
                            repeatCount = repeatCount(action),
                            isRepeatInfinite = action.optBoolean("repeat_infinite", false),
                            repeatDelayMs = delayAfter(action),
                            position = Point(
                                coordinate(action, "x"),
                                coordinate(action, "y"),
                            ),
                            pressDurationMs = duration(
                                action,
                                "press_duration_ms",
                                50L,
                                "pressDurationMs",
                            ),
                        ),
                    )
                    "swipe" -> add(
                        DumbAction.DumbSwipe(
                            id = actionId,
                            scenarioId = scenarioId,
                            name = name,
                            repeatCount = repeatCount(action),
                            isRepeatInfinite = action.optBoolean("repeat_infinite", false),
                            repeatDelayMs = delayAfter(action),
                            fromPosition = Point(
                                coordinate(action, "from_x", "fromX", "start_x", "startX"),
                                coordinate(action, "from_y", "fromY", "start_y", "startY"),
                            ),
                            toPosition = Point(
                                coordinate(action, "to_x", "toX", "end_x", "endX"),
                                coordinate(action, "to_y", "toY", "end_y", "endY"),
                            ),
                            swipeDurationMs = duration(action, "duration_ms", 300L),
                        ),
                    )
                    "pause", "delay" -> add(
                        DumbAction.DumbPause(
                            id = actionId,
                            scenarioId = scenarioId,
                            name = name,
                            pauseDurationMs = duration(action, "duration_ms", 500L),
                        ),
                    )
                    else -> error("Unsupported action type: $type")
                }
            }
        }
    }

    private fun coordinate(action: JSONObject, vararg keys: String): Int {
        val key = keys.firstOrNull(action::has)
            ?: error("Missing coordinate: ${keys.first()}")
        return action.getDouble(key).toInt().coerceIn(0, MAX_SCREEN_COORDINATE)
    }

    private fun duration(
        action: JSONObject,
        key: String,
        default: Long,
        vararg aliases: String,
    ): Long {
        val value = when {
            action.has(key) -> action.optDouble(key, default.toDouble())
            aliases.any(action::has) -> {
                val alias = aliases.first(action::has)
                action.optDouble(alias, default.toDouble())
            }
            action.has("duration") -> action.optDouble("duration", default.toDouble())
            action.has("durationMs") -> action.optDouble("durationMs", default.toDouble())
            action.has("milliseconds") -> action.optDouble("milliseconds", default.toDouble())
            else -> default.toDouble()
        }
        return value.toLong().coerceIn(1L, MAX_DURATION_MS)
    }

    private fun delayAfter(action: JSONObject): Long {
        val value = when {
            action.has("repeat_delay_ms") -> action.optDouble("repeat_delay_ms", 0.0)
            action.has("repeatDelayMs") -> action.optDouble("repeatDelayMs", 0.0)
            action.has("delay_after") -> action.optDouble("delay_after", 0.0)
            action.has("delayAfter") -> action.optDouble("delayAfter", 0.0)
            else -> 0.0
        }
        return value.toLong().coerceIn(0L, MAX_DURATION_MS)
    }

    private fun repeatCount(action: JSONObject): Int =
        action.optInt("repeat_count", 1).coerceIn(1, 99_999)

    private companion object {
        const val LOCAL_PREFIX = "Autopilot · "
        const val MAX_SCREEN_COORDINATE = 20_000
        const val MAX_DURATION_MS = 3_600_000L
    }
}