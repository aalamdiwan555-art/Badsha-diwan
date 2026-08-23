package com.autopilot.driver.ads

import android.app.Activity

class InterstitialAdController : InterstitialAdPlayer {
    override fun maybeShow(
        activity: Activity,
        isAdFree: Boolean,
        intervalMinutes: Int,
        onShown: () -> Unit,
        onError: (String) -> Unit,
    ) = Unit
}