@file:Suppress("DEPRECATION")

package com.capji.signalpro.vpn

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Keeps the Wi-Fi radio out of power save while the boost is on.
 *
 * Android applies WIFI_MODE_FULL_LOW_LATENCY only while the screen is on and this app is
 * in the foreground. Up to Android 13 the older high-performance lock also covers the
 * background (for example while a game is open); Android 14 removed that behaviour.
 */
class WifiLatencyLock(context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locks = mutableListOf<WifiManager.WifiLock>()

    val isHeld: Boolean get() = locks.any { it.isHeld }

    fun acquire() {
        if (locks.isNotEmpty()) return
        val modes = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(WifiManager.WIFI_MODE_FULL_LOW_LATENCY)
            if (Build.VERSION.SDK_INT < 34) add(WifiManager.WIFI_MODE_FULL_HIGH_PERF)
        }
        modes.forEach { mode ->
            runCatching {
                val lock = wifi.createWifiLock(mode, "capji:latency:$mode")
                lock.setReferenceCounted(false)
                lock.acquire()
                locks += lock
            }
        }
    }

    fun release() {
        locks.forEach { runCatching { if (it.isHeld) it.release() } }
        locks.clear()
    }
}
