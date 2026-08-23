package com.autopilot.driver.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small REST client used until the managed Supabase SDK connection is enabled.
 *
 * Requests intentionally use the publishable key and the user's access token.
 * Admin mutations must be implemented server-side and must never use a service
 * key from the Android app.
 */
class SupabaseRestClient(
    private val projectUrl: String = SupabaseConfig.PROJECT_URL,
    private val publishableKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    suspend fun signUp(email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            val response = JSONObject(request(
                method = "POST",
                path = SupabaseConfig.AUTH_SIGN_UP_PATH,
                body = JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .toString(),
                accessToken = null,
            ))

            AuthSession(
                accessToken = response.optString("access_token"),
                refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() },
                userId = response.optJSONObject("user")?.optString("id"),
                email = response.optJSONObject("user")?.optString("email"),
                requiresEmailConfirmation = response.optString("access_token").isBlank(),
            )
        }

    suspend fun signIn(email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()

            val response = JSONObject(request(
                method = "POST",
                path = SupabaseConfig.AUTH_TOKEN_PATH,
                body = body,
                accessToken = null,
            ))

            AuthSession(
                accessToken = response.getString("access_token"),
                refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() },
                userId = response.optJSONObject("user")?.optString("id"),
                email = response.optJSONObject("user")?.optString("email"),
            )
        }

    suspend fun fetchProfile(accessToken: String, userId: String): UserProfile? =
        withContext(Dispatchers.IO) {
            val rows = JSONArray(
                request(
                    method = "GET",
                    path = SupabaseConfig.profilePath(userId),
                    body = null,
                    accessToken = accessToken,
                ),
            )
            if (rows.length() == 0) return@withContext null

            val row = rows.getJSONObject(0)
            UserProfile(
                id = row.getString("id"),
                email = row.getString("email"),
                role = row.optString("role", "user"),
                subscriptionStatus = row.optString("subscription_status", "inactive"),
                subscriptionExpiresAt = row.optString("subscription_expires_at")
                    .takeIf { it.isNotBlank() && it != "null" },
                isAdFree = row.optBoolean("is_ad_free", false),
                adsWatchedToday = row.optInt("ads_watched_today", 0),
                selectedScenarioId = row.optString("selected_scenario_id")
                    .takeIf { it.isNotBlank() && it != "null" },
                selectedScenarioName = row.optString("selected_scenario_name")
                    .takeIf { it.isNotBlank() && it != "null" },
            )
        }

    suspend fun selectScenario(
        accessToken: String,
        userId: String,
        scenario: ScenarioMode,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "PATCH",
            path = SupabaseConfig.updateProfilePath(userId),
            body = JSONObject()
                .put("selected_scenario_id", scenario.id)
                .put("selected_scenario_name", scenario.name)
                .toString(),
            accessToken = accessToken,
        )
    }

    suspend fun fetchActiveScenarios(accessToken: String): List<ScenarioMode> =
        withContext(Dispatchers.IO) {
            val rows = JSONArray(
                request(
                    method = "GET",
                    path = SupabaseConfig.SCENARIOS_PATH,
                    body = null,
                    accessToken = accessToken,
                ),
            )

            buildList(rows.length()) {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    add(
                        ScenarioMode(
                            id = row.getString("id"),
                            name = row.getString("name"),
                            description = row.optString("description").takeIf { it.isNotBlank() },
                            version = row.optInt("version", 1),
                            scenarioData = row.optJSONObject("scenario_data") ?: JSONObject(),
                        ),
                    )
                }
            }
        }

    private fun request(
        method: String,
        path: String,
        body: String?,
        accessToken: String?,
    ): String {
        val connection = (URL(projectUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: publishableKey}")
            doInput = true
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (status !in 200..299) {
            val message = runCatching {
                JSONObject(responseText).let { body ->
                    sequenceOf(
                        body.optString("msg"),
                        body.optString("error_description"),
                        body.optString("message"),
                        body.optString("error"),
                    ).firstOrNull { it.isNotBlank() }
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "Supabase request failed with HTTP $status"
            throw IOException(message)
        }

        return responseText
    }
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String?,
    val email: String?,
    val requiresEmailConfirmation: Boolean = false,
)

data class UserProfile(
    val id: String,
    val email: String,
    val role: String,
    val subscriptionStatus: String,
    val subscriptionExpiresAt: String?,
    val isAdFree: Boolean,
    val adsWatchedToday: Int,
    val selectedScenarioId: String?,
    val selectedScenarioName: String?,
)

data class ScenarioMode(
    val id: String,
    val name: String,
    val description: String?,
    val version: Int,
    val scenarioData: JSONObject,
)