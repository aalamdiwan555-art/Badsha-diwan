package com.autopilot.driver.data.remote

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioValidatorTests {

    @Test
    fun acceptsExecutableClick() {
        assertTrue(
            ScenarioValidator.validate(
                JSONObject(
                    """{"version":1,"actions":[{"type":"click","x":480,"y":960}]}""",
                ),
            ),
        )
    }

    @Test
    fun rejectsClickWithoutCoordinates() {
        assertFalse(
            ScenarioValidator.validate(
                JSONObject("""{"version":1,"actions":[{"type":"click","x":480}]}"""),
            ),
        )
    }

    @Test
    fun acceptsSwipeAndPause() {
        assertTrue(
            ScenarioValidator.validate(
                JSONObject(
                    """{"version":1,"actions":[{"type":"swipe","from_x":100,"from_y":200,"to_x":500,"to_y":800,"duration_ms":300},{"type":"pause","duration_ms":500}]}""",
                ),
            ),
        )
    }

    @Test
    fun rejectsUnsupportedActionType() {
        assertFalse(
            ScenarioValidator.validate(
                JSONObject("""{"version":1,"actions":[{"type":"image_match"}]}"""),
            ),
        )
    }

    @Test
    fun rejectsOutOfRangeDuration() {
        assertFalse(
            ScenarioValidator.validate(
                JSONObject(
                    """{"version":1,"actions":[{"type":"pause","duration_ms":3600001}]}""",
                ),
            ),
        )
    }
}