package com.autopilot.driver.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/** Play Store overlay service backed by a Unity banner. */
class AutopilotFloatingBannerService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var bannerView: BannerView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showFloatingBanner()
        return START_STICKY
    }

    private fun showFloatingBanner() {
        if (floatingView != null || !Settings.canDrawOverlays(this)) return

        val container = FrameLayout(this)
        val banner = BannerView(this, "Banner_Android", UnityBannerSize(320, 50))
        bannerView = banner
        container.addView(banner)
        floatingView = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 16
            y = 16
        }

        windowManager?.addView(container, params)
        banner.load()
    }

    override fun onDestroy() {
        bannerView?.destroy()
        bannerView = null
        floatingView?.let { view ->
            if (view.isAttachedToWindow) windowManager?.removeView(view)
        }
        floatingView = null
        windowManager = null
        super.onDestroy()
    }
}
