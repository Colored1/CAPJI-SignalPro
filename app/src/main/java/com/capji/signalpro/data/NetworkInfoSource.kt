@file:Suppress("DEPRECATION")

package com.capji.signalpro.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address

/**
 * Finds the real (non-VPN) networks. While our tunnel is up the "active" network is the
 * VPN itself, so ISP DNS and Wi-Fi details must be read from the network underneath.
 */
class NetworkInfoSource(context: Context) {
    private val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun physicalNetworks(): List<Pair<Network, NetworkCapabilities>> =
        manager.allNetworks.mapNotNull { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) network to caps else null
        }

    fun wifiLink(): LinkProperties? = physicalNetworks()
        .firstOrNull { it.second.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        ?.let { manager.getLinkProperties(it.first) }

    /** The network carrying internet traffic, preferring validated Wi-Fi. */
    fun primaryLink(): LinkProperties? {
        val networks = physicalNetworks().sortedByDescending { (_, caps) ->
            var rank = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) rank += 4
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) rank += 2
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) rank += 1
            rank
        }
        return networks.firstOrNull()?.let { manager.getLinkProperties(it.first) }
    }

    fun isOnline(): Boolean = physicalNetworks().isNotEmpty()

    /** IPv4 resolvers handed out by the current provider. */
    fun providerDnsServers(): List<String> =
        primaryLink()?.dnsServers?.filterIsInstance<Inet4Address>()?.mapNotNull { it.hostAddress }
            ?: emptyList()

    /** Non-null when Android's strict "Private DNS" is on, which overrides any VPN DNS. */
    fun strictPrivateDns(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) primaryLink()?.privateDnsServerName else null

    fun gatewayOf(link: LinkProperties?): String? = link?.routes
        ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
        ?.gateway?.hostAddress
        ?.takeIf { it != "0.0.0.0" }
}
