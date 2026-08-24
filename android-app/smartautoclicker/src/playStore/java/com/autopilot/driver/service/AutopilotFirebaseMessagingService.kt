package com.autopilot.driver.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AutopilotFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Received message from ${remoteMessage.from}")
        remoteMessage.notification?.body?.let { Log.d(TAG, "Notification: $it") }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Firebase token refreshed")
    }

    private companion object {
        const val TAG = "AutopilotFMS"
    }
}