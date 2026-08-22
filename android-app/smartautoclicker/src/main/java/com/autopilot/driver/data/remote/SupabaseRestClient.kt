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
            val message = runCatching { JSONObject(responseText).optString("msg") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
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
)

data class ScenarioMode(
    val id: String,
    val name: String,
    val description: String?,
    val version: Int,
    val scenarioData: JSONObject,
)