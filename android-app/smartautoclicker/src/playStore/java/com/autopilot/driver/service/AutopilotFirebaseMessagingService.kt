package com.autopilot.driver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.buzbuz.smartautoclicker.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AutopilotFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"].orEmpty()
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "autopilot_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Autopilot updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        manager.notify(
            title.hashCode(),
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_smart_auto_clicker)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build(),
        )
    }
}