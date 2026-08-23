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

    suspend fun refreshSession(refreshToken: String): AuthSession =
        withContext(Dispatchers.IO) {
            val response = JSONObject(request(
                method = "POST",
                path = SupabaseConfig.AUTH_REFRESH_PATH,
                body = JSONObject().put("refresh_token", refreshToken).toString(),
                accessToken = null,
            ))

            AuthSession(
                accessToken = response.getString("access_token"),
                refreshToken = response.optString("refresh_token")
                    .takeIf { it.isNotBlank() }
                    ?: refreshToken,
                userId = response.optJSONObject("user")?.optString("id"),
                email = response.optJSONObject("user")?.optString("email"),
            )
        }

    suspend fun requestPasswordReset(email: String) =
        withContext(Dispatchers.IO) {
            request(
                method = "POST",
                path = SupabaseConfig.AUTH_PASSWORD_RESET_PATH,
                body = JSONObject().put("email", email).toString(),
                accessToken = null,
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

            rows.getJSONObject(0).toUserProfile()
        }

    suspend fun selectScenario(
        accessToken: String,
        scenario: ScenarioMode,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = SupabaseConfig.SELECT_SCENARIO_RPC,
            body = JSONObject()
                .put("scenario_id", scenario.id)
                .toString(),
            accessToken = accessToken,
        )
    }

    suspend fun clearSelectedScenario(accessToken: String) =
        withContext(Dispatchers.IO) {
            request(
                method = "POST",
                path = SupabaseConfig.CLEAR_SELECTED_SCENARIO_RPC,
                body = "{}",
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

             buildList(minOf(rows.length(), MAX_PUBLISHED_MODES)) {
                 for (index in 0 until minOf(rows.length(), MAX_PUBLISHED_MODES)) {
                    val row = rows.getJSONObject(index)
                     val scenarioData = row.optJSONObject("scenario_data") ?: continue
                     if (!ScenarioValidator.validate(scenarioData)) continue
                     val name = row.optString("name").trim()
                     if (name.isBlank() || name.length > 80) continue
                     add(
                         ScenarioMode(
                             id = row.getString("id"),
                             name = name,
                             description = row.optString("description").trim()
                                 .takeIf { it.isNotBlank() },
                             version = row.optInt("version", 1).coerceAtLeast(1),
                             scenarioData = scenarioData,
                         ),
                     )
                }
            }
        }

    suspend fun fetchProfiles(accessToken: String): List<UserProfile> =
        withContext(Dispatchers.IO) {
            val rows = JSONArray(request("GET", SupabaseConfig.PROFILES_PATH, null, accessToken))
            buildList(rows.length()) {
                for (index in 0 until rows.length()) add(rows.getJSONObject(index).toUserProfile())
            }
        }

    suspend fun createScenario(
        accessToken: String,
        adminId: String,
        name: String,
        description: String?,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = SupabaseConfig.SCENARIOS_TABLE_PATH,
            body = JSONObject()
                .put("admin_id", adminId)
                .put("name", name)
                .put("description", description)
                .put("scenario_data", JSONObject().put("version", 1).put("actions", JSONArray()))
                .put("is_active", true)
                .put("is_global", true)
                .toString(),
            accessToken = accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
    }

    suspend fun deleteScenario(accessToken: String, scenarioId: String) =
        withContext(Dispatchers.IO) {
            request(
                method = "DELETE",
                path = SupabaseConfig.scenarioPath(scenarioId),
                body = null,
                accessToken = accessToken,
            )
        }

    suspend fun updateScenario(
        accessToken: String,
        scenarioId: String,
        name: String,
        description: String?,
        scenarioData: JSONObject,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "PATCH",
            path = SupabaseConfig.scenarioPath(scenarioId),
            body = JSONObject()
                .put("name", name)
                .put("description", description)
                .put("scenario_data", scenarioData)
                .put("version", scenarioData.optInt("version", 1))
                .toString(),
            accessToken = accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
    }

    suspend fun grantSubscription(
        accessToken: String,
        userId: String,
        durationDays: Int,
        note: String?,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = SupabaseConfig.ADMIN_GRANT_SUBSCRIPTION_RPC,
            body = JSONObject()
                .put("target_user_id", userId)
                .put("grant_duration_days", durationDays)
                .put("grant_note", note)
                .toString(),
            accessToken = accessToken,
        )
    }

    suspend fun setAdFree(
        accessToken: String,
        userId: String,
        adFree: Boolean,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = SupabaseConfig.ADMIN_SET_AD_FREE_RPC,
            body = JSONObject()
                .put("target_user_id", userId)
                .put("ad_free_value", adFree)
                .toString(),
            accessToken = accessToken,
        )
    }

    suspend fun setBanned(
        accessToken: String,
        userId: String,
        banned: Boolean,
        reason: String?,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = SupabaseConfig.ADMIN_SET_BANNED_RPC,
            body = JSONObject()
                .put("target_user_id", userId)
                .put("banned_value", banned)
                .put("reason", reason)
                .toString(),
            accessToken = accessToken,
        )
    }

    suspend fun fetchAppSettings(accessToken: String): AppSettings =
        withContext(Dispatchers.IO) {
            val rows = JSONArray(request("GET", SupabaseConfig.APP_SETTINGS_PATH, null, accessToken))
            if (rows.length() == 0) error("App settings are not configured")
            rows.getJSONObject(0).toAppSettings()
        }

    suspend fun updateAppSettings(
        accessToken: String,
        interstitialIntervalMinutes: Int,
        rewardAdsForOneDay: Int,
        trialDays: Int,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = SupabaseConfig.ADMIN_UPDATE_SETTINGS_RPC,
            body = JSONObject()
                .put("new_interstitial_interval_minutes", interstitialIntervalMinutes)
                .put("new_reward_ads_for_one_day", rewardAdsForOneDay)
                .put("new_trial_days", trialDays)
                .toString(),
            accessToken = accessToken,
        )
    }

    suspend fun claimRewardAd(accessToken: String): RewardAdClaim =
        withContext(Dispatchers.IO) {
            val response = JSONObject(
                request(
                    method = "POST",
                    path = SupabaseConfig.CLAIM_REWARD_AD_RPC,
                    body = "{}",
                    accessToken = accessToken,
                ),
            )
            RewardAdClaim(
                adsWatchedToday = response.optInt("ads_watched_today", 0),
                unlocked = response.optBoolean("unlocked", false),
                subscriptionExpiresAt = response.optString("subscription_expires_at")
                    .takeIf { it.isNotBlank() && it != "null" },
                rewardAdsForOneDay = response.optInt("reward_ads_for_one_day", 20),
            )
        }

    private fun request(
        method: String,
        path: String,
        body: String?,
        accessToken: String?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String {
        val connection = (URL(projectUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: publishableKey}")
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
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

private const val MAX_PUBLISHED_MODES = 15

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
    val isBanned: Boolean,
    val banReason: String?,
)

data class ScenarioMode(
    val id: String,
    val name: String,
    val description: String?,
    val version: Int,
    val scenarioData: JSONObject,
)

data class RewardAdClaim(
    val adsWatchedToday: Int,
    val unlocked: Boolean,
    val subscriptionExpiresAt: String?,
    val rewardAdsForOneDay: Int,
)

data class AppSettings(
    val interstitialIntervalMinutes: Int,
    val rewardAdsForOneDay: Int,
    val trialDays: Int,
)

private fun JSONObject.toUserProfile() = UserProfile(
    id = getString("id"),
    email = getString("email"),
    role = optString("role", "user"),
    subscriptionStatus = optString("subscription_status", "inactive"),
    subscriptionExpiresAt = optString("subscription_expires_at")
        .takeIf { it.isNotBlank() && it != "null" },
    isAdFree = optBoolean("is_ad_free", false),
    adsWatchedToday = optInt("ads_watched_today", 0),
    selectedScenarioId = optString("selected_scenario_id")
        .takeIf { it.isNotBlank() && it != "null" },
    selectedScenarioName = optString("selected_scenario_name")
        .takeIf { it.isNotBlank() && it != "null" },
    isBanned = optBoolean("is_banned", false),
    banReason = optString("ban_reason").takeIf { it.isNotBlank() && it != "null" },
)

private fun JSONObject.toAppSettings() = AppSettings(
    interstitialIntervalMinutes = optInt("interstitial_interval_minutes", 2),
    rewardAdsForOneDay = optInt("reward_ads_for_one_day", 20),
    trialDays = optInt("trial_days", 3),
)