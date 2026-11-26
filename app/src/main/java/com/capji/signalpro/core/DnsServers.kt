package com.capji.signalpro.core

/** A public resolver the user can benchmark and select. */
data class DnsProvider(
    val id: String,
    val name: String,
    val primary: String,
    val secondary: String?,
) {
    val servers: List<String> get() = listOfNotNull(primary, secondary)
}

object DnsServers {
    const val CUSTOM_ID = "custom"
    const val ISP_ID = "isp"

    val builtIn: List<DnsProvider> = listOf(
        DnsProvider("cloudflare", "Cloudflare", "1.1.1.1", "1.0.0.1"),
        DnsProvider("google", "Google", "8.8.8.8", "8.8.4.4"),
        DnsProvider("quad9", "Quad9", "9.9.9.9", "149.112.112.112"),
        DnsProvider("adguard", "AdGuard", "94.140.14.14", "94.140.15.15"),
        DnsProvider("opendns", "OpenDNS", "208.67.222.222", "208.67.220.220"),
    )

    fun custom(primary: String, secondary: String?): DnsProvider? {
        val first = primary.trim()
        if (!isUsableIpv4(first)) return null
        val second = secondary?.trim().orEmpty()
        if (second.isNotEmpty() && !isUsableIpv4(second)) return null
        return DnsProvider(CUSTOM_ID, "Custom", first, second.ifEmpty { null })
    }

    /** Strict dotted-quad check that never performs a name lookup. */
    fun isUsableIpv4(text: String): Boolean {
        val parts = text.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return false
            part.toInt().also { if (it > 255) return false }
        }
        // 0.x, loopback and multicast/reserved ranges can never be a resolver here.
        return octets[0] != 0 && octets[0] != 127 && octets[0] < 224
    }
}
