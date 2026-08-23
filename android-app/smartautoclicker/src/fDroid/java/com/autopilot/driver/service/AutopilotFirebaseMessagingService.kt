package com.autopilot.driver.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** F-Droid build has no Firebase dependency or push transport. */
class AutopilotFirebaseMessagingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}