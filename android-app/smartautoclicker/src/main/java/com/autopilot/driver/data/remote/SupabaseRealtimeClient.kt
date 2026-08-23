package com.autopilot.driver.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Receives database changes without removing the existing polling fallback. */
class SupabaseRealtimeClient {
    private val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SupabaseConfig.PROJECT_URL,
        supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
    ) {
        install(Postgrest)
        install(Realtime)
        install(Auth)
    }

    fun subscribeToScenarios(onScenarioChange: () -> Unit, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val channel = client.realtime.channel("scenarios")
                channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
                    table = "scenarios"
                }.onEach { onScenarioChange() }.launchIn(scope)
                channel.subscribe()
            } catch (error: Exception) {
                Log.e("SupabaseRealtime", "Scenarios subscription failed", error)
            }
        }
    }

    fun subscribeToProfile(userId: String, onProfileChange: () -> Unit, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val channel = client.realtime.channel("profile_$userId")
                channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
                    table = "profiles"
                    filter = "id=eq.$userId"
                }.onEach { onProfileChange() }.launchIn(scope)
                channel.subscribe()
            } catch (error: Exception) {
                Log.e("SupabaseRealtime", "Profile subscription failed", error)
            }
        }
    }
}
