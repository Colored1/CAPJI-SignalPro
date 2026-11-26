@file:Suppress("DEPRECATION")

package com.capji.signalpro.qr

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.capji.signalpro.core.WifiCredentials
import com.capji.signalpro.core.WifiSecurity

sealed interface ConnectAction {
    /** Android shows its own confirmation screen to save the network. */
    data class SystemDialog(val intent: Intent) : ConnectAction
    /** Network registered; Android will join it (possibly after a notification prompt). */
    data object Requested : ConnectAction
    /** Cannot be done programmatically: open Wi-Fi settings and paste the password. */
    data object Manual : ConnectAction
}

object WifiConnector {

    fun connect(context: Context, credentials: WifiCredentials): ConnectAction = runCatching {
        when {
            // The suggestion API has no WEP support.
            Build.VERSION.SDK_INT >= 29 && credentials.security == WifiSecurity.WEP -> ConnectAction.Manual
            Build.VERSION.SDK_INT >= 30 -> ConnectAction.SystemDialog(
                Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
                    Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion(credentials)),
                ),
            )
            Build.VERSION.SDK_INT == 29 -> {
                val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val status = manager.addNetworkSuggestions(listOf(suggestion(credentials)))
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) ConnectAction.Requested
                else ConnectAction.Manual
            }
            else -> legacyConnect(context, credentials)
        }
    }.getOrDefault(ConnectAction.Manual)

    @RequiresApi(29)
    private fun suggestion(credentials: WifiCredentials): WifiNetworkSuggestion =
        WifiNetworkSuggestion.Builder()
            .setSsid(credentials.ssid)
            .setIsHiddenSsid(credentials.hidden)
            .apply {
                when (credentials.security) {
                    WifiSecurity.WPA -> setWpa2Passphrase(credentials.password)
                    WifiSecurity.WPA3 -> setWpa3Passphrase(credentials.password)
                    WifiSecurity.WEP, WifiSecurity.OPEN -> Unit
                }
            }
            .build()

    private fun legacyConnect(context: Context, credentials: WifiCredentials): ConnectAction {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val configuration = WifiConfiguration().apply {
            SSID = "\"${credentials.ssid}\""
            hiddenSSID = credentials.hidden
            when (credentials.security) {
                WifiSecurity.OPEN -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                WifiSecurity.WEP -> {
                    val key = credentials.password
                    wepKeys[0] = if (key.length == 10 || key.length == 26) key else "\"$key\""
                    wepTxKeyIndex = 0
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                }
                else -> preSharedKey = "\"${credentials.password}\""
            }
        }
        val id = manager.addNetwork(configuration)
        if (id < 0) return ConnectAction.Manual
        manager.disconnect()
        return if (manager.enableNetwork(id, true)) {
            manager.reconnect()
            ConnectAction.Requested
        } else ConnectAction.Manual
    }

    fun copySecret(context: Context, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("password", value)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }
}
