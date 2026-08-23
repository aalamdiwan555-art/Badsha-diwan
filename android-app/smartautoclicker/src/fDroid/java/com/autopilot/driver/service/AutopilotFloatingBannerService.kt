package com.autopilot.driver.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** F-Droid flavor: no proprietary advertising or overlay UI. */
class AutopilotFloatingBannerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // F-Droid: the service remains available but shows nothing.
        return START_NOT_STICKY
    }
}
