package com.autopilot.driver.ads

import android.app.Activity
import android.os.SystemClock
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

class InterstitialAdController : InterstitialAdPlayer {
    private var initialized = false
    private var initializing = false
    private var loading = false
    private var lastShownAt = SystemClock.elapsedRealtime()

    override fun maybeShow(
        activity: Activity,
        isAdFree: Boolean,
        intervalMinutes: Int,
        onShown: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (isAdFree || intervalMinutes < 1) return
        val intervalMs = intervalMinutes.coerceIn(1, 60) * 60_000L
        if (SystemClock.elapsedRealtime() - lastShownAt < intervalMs) return
        if (loading || initializing) return

        val showAd = {
            if (UnityAds.isReady(AD_UNIT_ID)) {
                showLoaded(activity, onShown, onError)
            } else {
                loadAndShow(activity, onShown, onError)
            }
        }
        if (initialized) {
            showAd()
            return
        }

        initializing = true
        UnityAds.initialize(
            activity.applicationContext,
            GAME_ID,
            false,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    initializing = false
                    initialized = true
                    showAd()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?,
                ) {
                    initializing = false
                    onError(message ?: "Interstitial ads could not be initialized.")
                }
            },
        )
    }

    private fun loadAndShow(
        activity: Activity,
        onShown: () -> Unit,
        onError: (String) -> Unit,
    ) {
        loading = true
        UnityAds.load(AD_UNIT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                loading = false
                showLoaded(activity, onShown, onError)
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String?,
                error: UnityAds.UnityAdsLoadError?,
                message: String?,
            ) {
                loading = false
                onError(message ?: "Interstitial ad is unavailable right now.")
            }
        })
    }

    private fun showLoaded(
        activity: Activity,
        onShown: () -> Unit,
        onError: (String) -> Unit,
    ) {
        UnityAds.show(activity, AD_UNIT_ID, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String?,
                error: UnityAds.UnityAdsShowError?,
                message: String?,
            ) = onError(message ?: "Interstitial ad could not be shown.")

            override fun onUnityAdsShowStart(placementId: String?) {
                lastShownAt = SystemClock.elapsedRealtime()
                onShown()
            }

            override fun onUnityAdsShowClick(placementId: String?) = Unit
            override fun onUnityAdsShowComplete(
                placementId: String?,
                state: UnityAds.UnityAdsShowCompletionState?,
            ) = Unit
        })
    }

    private companion object {
        const val GAME_ID = "6178983"
        const val AD_UNIT_ID = "interstitial"
    }
}