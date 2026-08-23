package com.autopilot.driver.ads

import android.app.Activity
import android.view.ViewGroup

/** Stable Autopilot name around the existing Unity banner implementation. */
class UnityBannerAdPlayer : BannerAdPlayer {
    private val delegate = BannerAdController()

    override fun show(
        activity: Activity,
        container: ViewGroup,
        isAdFree: Boolean,
        onEvent: (String) -> Unit,
    ) = delegate.show(activity, container, isAdFree, onEvent)

    override fun hide(container: ViewGroup) = delegate.hide(container)
    override fun destroy(container: ViewGroup) = delegate.destroy(container)
}