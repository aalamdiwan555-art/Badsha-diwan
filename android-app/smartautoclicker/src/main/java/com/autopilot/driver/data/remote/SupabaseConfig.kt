package com.autopilot.driver.data.remote

/**
 * Public Supabase client configuration.
 *
 * The publishable key is safe to ship in an Android client when protected by
 * Row Level Security. Never add a service-role or secret key here.
 */
object SupabaseConfig {
    const val PROJECT_URL = "https://rhwpbnzbevufolojjimh.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_djOPp-ptG00MCdzNxXxL7A_Hq27r9BN"

    const val AUTH_TOKEN_PATH = "/auth/v1/token?grant_type=password"
    const val AUTH_REFRESH_PATH = "/auth/v1/token?grant_type=refresh_token"
    const val AUTH_SIGN_UP_PATH = "/auth/v1/signup"
    const val AUTH_PASSWORD_RESET_PATH = "/auth/v1/recover"
    const val SCENARIOS_PATH =
        "/rest/v1/scenarios?select=*&is_active=eq.true&order=created_at.asc"
    const val SCENARIOS_TABLE_PATH = "/rest/v1/scenarios"
    const val PROFILES_PATH = "/rest/v1/profiles?select=*&order=created_at.asc"
    const val ADMIN_GRANT_SUBSCRIPTION_RPC = "/rest/v1/rpc/admin_grant_subscription"
    const val ADMIN_SET_AD_FREE_RPC = "/rest/v1/rpc/admin_set_ad_free"
    const val ADMIN_SET_BANNED_RPC = "/rest/v1/rpc/admin_set_banned"

    fun profilePath(userId: String): String =
        "/rest/v1/profiles?select=*&id=eq.$userId"

    fun updateProfilePath(userId: String): String =
        "/rest/v1/profiles?id=eq.$userId"

    fun scenarioPath(scenarioId: String): String =
        "$SCENARIOS_TABLE_PATH?id=eq.$scenarioId"
}