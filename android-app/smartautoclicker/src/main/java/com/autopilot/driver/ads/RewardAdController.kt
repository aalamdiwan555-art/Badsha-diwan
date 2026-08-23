package com.autopilot.driver.ads

import android.app.Activity

/**
 * Displays a rewarded ad. The completion callback must only be used to call
 * the server-side reward claim endpoint; this class never changes access state.
 */
interface RewardAdPlayer {
    fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onError: (String) -> Unit,
    )
}