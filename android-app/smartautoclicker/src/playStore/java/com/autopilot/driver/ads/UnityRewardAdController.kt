package com.autopilot.driver.ads

import android.app.Activity

class UnityRewardAdController : RewardAdPlayer {
    private val delegate = RewardAdController()

    override fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onError: (String) -> Unit,
        onStarted: () -> Unit,
    ) = delegate.show(activity, onRewarded, onError, onStarted)
}