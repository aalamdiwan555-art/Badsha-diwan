package com.autopilot.driver.ads

import android.app.Activity

interface InterstitialAdPlayer {
    fun maybeShow(
        activity: Activity,
        isAdFree: Boolean,
        intervalMinutes: Int,
        onShown: () -> Unit = {},
        onError: (String) -> Unit = {},
    )
}