package com.autopilot.driver.ads

import android.app.Activity
import android.view.ViewGroup

/**
 * F-Droid implementation of the shared ad coordinator.
 *
 * The F-Droid flavor deliberately ships without an advertising SDK, while
 * keeping the home screen's ad lifecycle identical across flavors.
 */
class AutopilotAdsManager {
    private val bannerPlayer: BannerAdPlayer = BannerAdController()
    private val interstitialPlayer: InterstitialAdPlayer = InterstitialAdController()
    private val rewardPlayer: RewardAdPlayer = RewardAdController()

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