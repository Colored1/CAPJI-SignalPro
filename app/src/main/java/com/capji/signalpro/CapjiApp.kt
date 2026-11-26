package com.capji.signalpro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.capji.signalpro.firebase.RemoteConfigManager

class CapjiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_VPN, getString(R.string.channel_vpn), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_NEWS, getString(R.string.channel_news), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
        RemoteConfigManager.initialize(this)
    }

    companion object {
        const val CHANNEL_VPN = "capji_boost"
        const val CHANNEL_NEWS = "capji_news"
    }
}
