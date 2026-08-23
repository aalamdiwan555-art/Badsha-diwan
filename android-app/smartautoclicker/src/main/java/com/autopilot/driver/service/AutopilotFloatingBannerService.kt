package com.autopilot.driver.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Lightweight overlay shown while a click session is active.
 *
 * The ad SDK banner remains owned by the Activity. This service only owns the
 * lifecycle-safe floating status surface, avoiding an Activity leak from a
 * background service.
 */
class AutopilotFloatingBannerService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = TextView(this).apply {
            text = "Autopilot is running"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD172033.toInt())
            setPadding(24, 12, 24, 12)
            textSize = 12f
        }
        val windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 16
            y = 16
        }
        windowManager?.addView(floatingView, params)
    }

    override fun onDestroy() {
        floatingView?.let { view -> windowManager?.removeView(view) }
        floatingView = null
        windowManager = null
        super.onDestroy()
    }
}