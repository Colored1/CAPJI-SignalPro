@file:Suppress("DEPRECATION")

package com.capji.signalpro.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.capji.signalpro.core.AccessPoint
import com.capji.signalpro.core.Band
import com.capji.signalpro.core.WifiMath
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

data class WifiTelemetry(
    val connected: Boolean = false,
    val wifiEnabled: Boolean = true,
    /** Null when Android hides it (location permission missing or location off). */
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequency: Int? = null,
    val ipAddress: String? = null,
    val gateway: String? = null,
    val dns: List<String> = emptyList(),
) {
    val quality: Int? get() = rssi?.let(WifiMath::signalQuality)
    val channel: Int? get() = frequency?.let(WifiMath::channelOf)
    val band: Band get() = frequency?.let(WifiMath::bandOf) ?: Band.UNKNOWN
}

data class ScanOutcome(
    val accessPoints: List<AccessPoint>,
    /** False when Android throttled the request and returned cached results. */
    val fresh: Boolean,
)

class WifiRepository(context: Context) {
    private val context = context.applicationContext
    private val wifi = this.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val network = NetworkInfoSource(this.context)

    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    fun telemetry(): WifiTelemetry {
        val enabled = wifi.isWifiEnabled
        val link = network.wifiLink() ?: return WifiTelemetry(connected = false, wifiEnabled = enabled)
        val info = wifi.connectionInfo
        val ssid = info?.ssid?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        return WifiTelemetry(
            connected = true,
            wifiEnabled = enabled,
            ssid = ssid,
            bssid = info?.bssid?.takeIf { it != "02:00:00:00:00:00" },
            rssi = info?.rssi?.takeIf { it in -126..0 },
            linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
            frequency = info?.frequency?.takeIf { it > 0 },
            ipAddress = link.linkAddresses.map { it.address }
                .filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress,
            gateway = network.gatewayOf(link),
            dns = link.dnsServers.mapNotNull { it.hostAddress },
        )
    }

    /** Requests a scan and waits for it. Requires fine location and location services. */
    @SuppressLint("MissingPermission")
    suspend fun scan(): ScanOutcome {
        if (!hasLocationPermission()) return ScanOutcome(emptyList(), false)
        val updated = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(received: Context, intent: Intent) {
                        context.unregisterReceiver(this)
                        if (continuation.isActive) {
                            continuation.resume(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false))
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                if (!wifi.startScan()) {
                    runCatching { context.unregisterReceiver(receiver) }
                    continuation.resume(false)
                }
            }
        } ?: false
        val results = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        return ScanOutcome(results.map(::toAccessPoint), updated)
    }

    private fun toAccessPoint(result: ScanResult): AccessPoint {
        val width = when (result.channelWidth) {
            ScanResult.CHANNEL_WIDTH_40MHZ -> 40
            ScanResult.CHANNEL_WIDTH_80MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
            ScanResult.CHANNEL_WIDTH_160MHZ -> 160
            else -> if (Build.VERSION.SDK_INT >= 33 &&
                result.channelWidth == ScanResult.CHANNEL_WIDTH_320MHZ
            ) 320 else 20
        }
        return AccessPoint(
            ssid = result.SSID.orEmpty(),
            bssid = result.BSSID.orEmpty(),
            rssi = result.level,
            frequency = result.frequency,
            centerFrequency = result.centerFreq0.takeIf { width > 20 && it > 0 } ?: result.frequency,
            widthMhz = width,
        )
    }
}
