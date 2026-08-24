package com.autopilot.driver.ads

import android.app.Activity

class UnityInterstitialAdPlayer : InterstitialAdPlayer {
    private val delegate = InterstitialAdController()

    override fun maybeShow(
        activity: Activity,
        isAdFree: Boolean,
        intervalMinutes: Int,
        onShown: () -> Unit,
        onError: (String) -> Unit,
    ) = delegate.maybeShow(activity, isAdFree, intervalMinutes, onShown, onError)
}