package com.capji.signalpro.data

import android.content.Context
import android.content.SharedPreferences
import com.capji.signalpro.core.DnsProvider
import com.capji.signalpro.core.DnsServers

/** Local settings only. Nothing here ever leaves the device. */
class Prefs(context: Context) {
    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences("capji_settings", Context.MODE_PRIVATE)

    var arabic: Boolean
        get() = store.getBoolean("arabic", true)
        set(value) = store.edit().putBoolean("arabic", value).apply()

    var disclosureAccepted: Boolean
        get() = store.getBoolean("vpn_disclosure_accepted", false)
        set(value) = store.edit().putBoolean("vpn_disclosure_accepted", value).apply()

    /** When true the benchmark winner is used; otherwise [selectedDnsId]. */
    var autoSelectDns: Boolean
        get() = store.getBoolean("auto_dns", true)
        set(value) = store.edit().putBoolean("auto_dns", value).apply()

    var selectedDnsId: String
        get() = store.getString("dns_id", "cloudflare") ?: "cloudflare"
        set(value) = store.edit().putString("dns_id", value).apply()

    var customPrimary: String
        get() = store.getString("custom_primary", "") ?: ""
        set(value) = store.edit().putString("custom_primary", value).apply()

    var customSecondary: String
        get() = store.getString("custom_secondary", "") ?: ""
        set(value) = store.edit().putString("custom_secondary", value).apply()

    fun customProvider(): DnsProvider? = DnsServers.custom(customPrimary, customSecondary)

    fun allProviders(): List<DnsProvider> = DnsServers.builtIn + listOfNotNull(customProvider())

    fun selectedProvider(): DnsProvider =
        allProviders().firstOrNull { it.id == selectedDnsId } ?: DnsServers.builtIn.first()
}
