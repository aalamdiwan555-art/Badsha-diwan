package com.autopilot.driver.ads

import android.app.Activity
import android.content.Context
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

class RewardAdController : RewardAdPlayer {
    private var initialized = false
    private var initializing = false
    private var loading = false

    override fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onError: (String) -> Unit,
        onStarted: () -> Unit,
    ) {
        val showAd = {
            if (!UnityAds.isReady(AD_UNIT_ID)) {
                loadAndShow(activity, onRewarded, onError)
            } else {
                showLoaded(activity, onRewarded, onError)
            }
        }
        if (initialized) {
            showAd()
            return
        }
        if (initializing) {
            onError("Reward ads are still initializing. Please try again.")
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
                    initialized = false
                    onError(message ?: "Reward ads could not be initialized.")
                }
            },
        )
    }

    private fun loadAndShow(
        activity: Activity,
        onRewarded: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (loading) {
            onError("A reward ad is already loading. Please try again.")
            return
        }
        loading = true
        UnityAds.load(AD_UNIT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                loading = false
                showLoaded(activity, onRewarded, onError)
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String?,
                error: UnityAds.UnityAdsLoadError?,
                message: String?,
            ) {
                loading = false
                onError(message ?: "Reward ad is unavailable right now.")
            }
        })
    }

    private fun showLoaded(
        activity: Activity,
        onRewarded: () -> Unit,
        onError: (String) -> Unit,
    ) {
        UnityAds.show(activity, AD_UNIT_ID, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String?,
                error: UnityAds.UnityAdsShowError?,
                message: String?,
            ) = onError(message ?: "Reward ad could not be shown.")

            override fun onUnityAdsShowStart(placementId: String?) = onStarted()
            override fun onUnityAdsShowClick(placementId: String?) = Unit

            override fun onUnityAdsShowComplete(
                placementId: String?,
                state: UnityAds.UnityAdsShowCompletionState?,
            ) {
                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                    onRewarded()
                } else {
                    onError("Watch the complete ad to receive the reward.")
                }
            }
        })
    }

    private companion object {
        const val GAME_ID = "6178983"
        const val AD_UNIT_ID = "rewardedVideo"
    }
}