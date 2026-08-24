package com.autopilot.driver.ads

import android.app.Activity
import android.view.ViewGroup
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.IUnityBannerListener
import com.unity3d.services.banners.UnityBannerSize

class BannerAdController : BannerAdPlayer {
    private var bannerView: BannerView? = null

    override fun show(
        activity: Activity,
        container: ViewGroup,
        isAdFree: Boolean,
        onEvent: (String) -> Unit,
    ) {
        if (isAdFree) {
            hide(container)
            return
        }

        val existing = bannerView
        if (existing != null) {
            container.visibility = ViewGroup.VISIBLE
            return
        }

        onEvent("requested")
        val banner = BannerView(activity, AD_UNIT_ID, UnityBannerSize(320, 50))
        banner.bannerListener = object : IUnityBannerListener {
            override fun onUnityBannerLoaded(bannerAdView: BannerView?) {
                onEvent("loaded")
            }

            override fun onUnityBannerUnloaded(bannerAdView: BannerView?) {
                onEvent("closed")
            }

            override fun onUnityBannerShow(bannerAdView: BannerView?) {
                onEvent("shown")
            }

            override fun onUnityBannerClick(bannerAdView: BannerView?) {
                onEvent("clicked")
            }

            override fun onUnityBannerFailedToLoad(
                bannerAdView: BannerView?,
                errorInfo: String?,
            ) {
                onEvent("failed")
            }
        }
        bannerView = banner
        container.removeAllViews()
        container.addView(banner)
        container.visibility = ViewGroup.VISIBLE
        banner.load()
    }

    override fun hide(container: ViewGroup) {
        container.visibility = ViewGroup.GONE
    }

    override fun destroy(container: ViewGroup) {
        bannerView?.destroy()
        bannerView = null
        container.removeAllViews()
        container.visibility = ViewGroup.GONE
    }

    private companion object {
        const val AD_UNIT_ID = "banner"
    }
}