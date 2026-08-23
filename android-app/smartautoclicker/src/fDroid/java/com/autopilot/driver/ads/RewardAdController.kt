package com.autopilot.driver.ads

import android.app.Activity

class RewardAdController : RewardAdPlayer {
    override fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onError: (String) -> Unit,
        onStarted: () -> Unit,
    ) {
        onError("Reward ads are available in the Play Store build.")
    }
}