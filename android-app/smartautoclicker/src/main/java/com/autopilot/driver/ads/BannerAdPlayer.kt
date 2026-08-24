package com.autopilot.driver.ads

import android.app.Activity
import android.view.ViewGroup

interface BannerAdPlayer {
    fun show(
        activity: Activity,
        container: ViewGroup,
        isAdFree: Boolean,
        onEvent: (String) -> Unit = {},
    )

    fun hide(container: ViewGroup)

    fun destroy(container: ViewGroup)
}