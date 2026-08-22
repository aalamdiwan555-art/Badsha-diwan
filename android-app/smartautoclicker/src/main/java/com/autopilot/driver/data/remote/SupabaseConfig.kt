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
    const val SCENARIOS_PATH =
        "/rest/v1/scenarios?select=*&is_active=eq.true&order=created_at.asc"
}