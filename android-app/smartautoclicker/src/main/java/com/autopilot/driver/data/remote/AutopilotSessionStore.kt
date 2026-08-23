package com.autopilot.driver.data.remote

import android.content.Context

/**
 * Stores only the short-lived client session needed to resume the app.
 *
 * Supabase access tokens are never logged or written to files outside the app's
 * private preferences. The server remains the source of truth for profile data.
 */
class AutopilotSessionStore(context: Context) {
    val context: Context = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val accessToken: String?
        get() = preferences.getString(KEY_ACCESS_TOKEN, null)

    val refreshToken: String?
        get() = preferences.getString(KEY_REFRESH_TOKEN, null)

    val userId: String?
        get() = preferences.getString(KEY_USER_ID, null)

    val email: String?
        get() = preferences.getString(KEY_EMAIL, null)

    fun save(session: AuthSession) {
        require(session.accessToken.isNotBlank()) { "An authenticated session must contain an access token" }
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "autopilot_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
    }
}