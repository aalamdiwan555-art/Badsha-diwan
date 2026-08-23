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
        val savedSession = AuthSession(
            accessToken = accessToken,
            refreshToken = sessionStore.refreshToken,
            userId = userId,
            email = sessionStore.email,
        )

        return runCatching { loadAccount(savedSession) }.getOrElse { originalError ->
            val refreshToken = sessionStore.refreshToken ?: throw originalError
            val refreshedSession = client.refreshSession(refreshToken).copy(
                userId = userId,
                email = sessionStore.email,
            )
            sessionStore.save(refreshedSession)
            loadAccount(refreshedSession)
        }
    }

    suspend fun selectMode(mode: ScenarioMode) {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot select a mode without an authenticated session")
        val userId = sessionStore.userId
            ?: error("Cannot select a mode without an authenticated user")
        client.selectScenario(accessToken, userId, mode)
    }

    suspend fun requestPasswordReset(email: String) {
        client.requestPasswordReset(email.trim())
    }

    suspend fun createScenario(name: String, description: String?) {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot create a mode without an authenticated session")
        val adminId = sessionStore.userId
            ?: error("Cannot create a mode without an authenticated user")
        client.createScenario(accessToken, adminId, name.trim(), description?.trim())
    }

    suspend fun deleteScenario(scenarioId: String) {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot delete a mode without an authenticated session")
        client.deleteScenario(accessToken, scenarioId)
    }

    suspend fun updateScenario(
        scenarioId: String,
        name: String,
        description: String?,
        scenarioData: org.json.JSONObject,
    ) {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot update a mode without an authenticated session")
        client.updateScenario(accessToken, scenarioId, name.trim(), description?.trim(), scenarioData)
    }

    suspend fun grantSubscription(userId: String, durationDays: Int, note: String?) {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot grant access without an authenticated session")
        client.grantSubscription(accessToken, userId, durationDays, note?.trim())
    }

    suspend fun setAdFree(userId: String, adFree: Boolean) {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot change ad-free status without an authenticated session")
        client.setAdFree(accessToken, userId, adFree)
    }

    suspend fun loadUsers(): List<UserProfile> {
        val accessToken = sessionStore.accessToken
            ?: error("Cannot load users without an authenticated session")
        return client.fetchProfiles(accessToken)
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