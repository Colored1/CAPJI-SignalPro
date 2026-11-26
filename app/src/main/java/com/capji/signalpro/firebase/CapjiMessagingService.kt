package com.capji.signalpro.firebase

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.capji.signalpro.CapjiApp
import com.capji.signalpro.MainActivity
import com.capji.signalpro.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CapjiMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(RemoteConfigManager.TOPIC_ALL)
    }

    /** Called for data messages, and for notifications received while in the foreground. */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        if (!allowed) return
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CapjiApp.CHANNEL_NEWS)
            .setSmallIcon(R.drawable.ic_stat_capji)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification)
        }
    }
}
