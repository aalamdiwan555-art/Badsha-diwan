package com.autopilot.driver.ads

import android.app.Activity
import android.view.ViewGroup

/**
 * F-Droid builds intentionally have no advertising SDK.
 */
class BannerAdController : BannerAdPlayer {
    override fun show(
        activity: Activity,
        container: ViewGroup,
        isAdFree: Boolean,
        onEvent: (String) -> Unit,
    ) {
        hide(container)
    }

    override fun hide(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = ViewGroup.GONE
    }

    override fun destroy(container: ViewGroup) = hide(container)
}