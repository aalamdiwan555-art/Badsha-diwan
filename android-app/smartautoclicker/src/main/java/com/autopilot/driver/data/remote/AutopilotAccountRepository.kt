package com.autopilot.driver.data.remote

/**
 * Coordinates authentication and remote mode selection while keeping the UI
 * independent from HTTP details.
 */
class AutopilotAccountRepository(
    private val client: SupabaseRestClient,
    private val sessionStore: AutopilotSessionStore,
) {
    suspend fun signIn(email: String, password: String): AuthResult {
        val session = client.signIn(email.trim(), password)
        sessionStore.save(session)
        return loadAccount(session)
    }

    suspend fun signUp(email: String, password: String): AuthResult {
        val session = client.signUp(email.trim(), password)
        if (session.accessToken.isBlank()) {
            return AuthResult.EmailConfirmationRequired(session.email ?: email.trim())
        }
        sessionStore.save(session)
        return loadAccount(session)
    }

    suspend fun loadSavedAccount(): AuthResult? {
        val accessToken = sessionStore.accessToken ?: return null
        val userId = sessionStore.userId ?: return null
        val profile = client.fetchProfile(accessToken, userId) ?: return null
        return AuthResult.Authenticated(
            session = AuthSession(
                accessToken = accessToken,
                refreshToken = null,
                userId = userId,
                email = sessionStore.email,
            ),
            profile = profile,
            availableModes = client.fetchActiveScenarios(accessToken),
        )
    }

    suspend fun selectMode(mode: ScenarioMode) {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot select a mode without an authenticated session")
        val userId = sessionStore.userId
            ?: error("Cannot select a mode without an authenticated user")
        client.selectScenario(accessToken, userId, mode)
    }

    fun signOut() = sessionStore.clear()

    private suspend fun loadAccount(session: AuthSession): AuthResult {
        val accessToken = session.accessToken
        val userId = session.userId ?: error("Authenticated response did not include a user id")
        val profile = client.fetchProfile(accessToken, userId)
            ?: error("Authenticated user does not have a profiles row")
        return AuthResult.Authenticated(
            session = session,
            profile = profile,
            availableModes = client.fetchActiveScenarios(accessToken),
        )
    }
}

sealed interface AuthResult {
    data class Authenticated(
        val session: AuthSession,
        val profile: UserProfile,
        val availableModes: List<ScenarioMode>,
    ) : AuthResult

    data class EmailConfirmationRequired(val email: String) : AuthResult
}