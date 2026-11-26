package com.capji.signalpro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.capji.signalpro.data.Prefs
import com.capji.signalpro.firebase.RemoteConfigManager
import com.capji.signalpro.vpn.CapjiDnsVpnService
import com.capji.signalpro.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val language = MutableStateFlow(prefs.arabic)

    val arabic: StateFlow<Boolean> = language.asStateFlow()
    val remote = RemoteConfigManager.state

    fun toggleLanguage() {
        val next = !language.value
        prefs.arabic = next
        language.value = next
        // Refresh the ongoing notification in the new language.
        if (VpnState.status.value.running) CapjiDnsVpnService.start(getApplication())
    }
}
