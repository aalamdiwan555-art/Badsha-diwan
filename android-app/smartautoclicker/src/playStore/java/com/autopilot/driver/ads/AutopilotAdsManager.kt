package com.autopilot.driver.ads

import android.app.Activity
import android.view.ViewGroup

/**
 * Single entry point for the Play Store ad stack.
 *
 * The concrete controllers keep Unity-specific APIs out of the shared
 * Activity code and make it possible to keep F-Droid builds ad-free.
 */
class AutopilotAdsManager {
    private val bannerPlayer: BannerAdPlayer = UnityBannerAdPlayer()
    private val interstitialPlayer: InterstitialAdPlayer = UnityInterstitialAdPlayer()
    private val rewardPlayer: RewardAdPlayer = UnityRewardAdController()

    fun showBanner(
        activity: Activity,
        container: ViewGroup,
        isAdFree: Boolean,
        onEvent: (String) -> Unit = {},
    ) = bannerPlayer.show(activity, container, isAdFree, onEvent)

    fun hideBanner(container: ViewGroup) = bannerPlayer.hide(container)

    fun maybeShowInterstitial(
        activity: Activity,
        isAdFree: Boolean,
        intervalMinutes: Int,
        onShown: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) = interstitialPlayer.maybeShow(
        activity,
        isAdFree,
        intervalMinutes,
        onShown,
        onError,
    )

    fun showReward(
        activity: Activity,
        onRewarded: () -> Unit,
        onError: (String) -> Unit,
        onStarted: () -> Unit = {},
    ) = rewardPlayer.show(activity, onRewarded, onError, onStarted)

    fun destroyBanner(container: ViewGroup) = bannerPlayer.destroy(container)
}