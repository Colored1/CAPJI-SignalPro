package com.capji.signalpro.ui.tools

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capji.signalpro.core.PasswordGenerator
import com.capji.signalpro.core.PasswordOptions
import com.capji.signalpro.core.WifiCredentials
import com.capji.signalpro.core.WifiQr
import com.capji.signalpro.core.WifiSecurity
import com.capji.signalpro.data.WifiRepository
import com.capji.signalpro.qr.QrCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ToolMessage { SAVED, SAVE_FAILED, SHARE_FAILED, STORAGE_DENIED, SSID_REQUIRED, BAD_PASSWORD }

/** Held in memory only: Wi-Fi passwords are never written to storage by the app. */
data class ToolsUiState(
    val ssid: String = "",
    val password: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val hidden: Boolean = false,
    val card: Bitmap? = null,
    val message: ToolMessage? = null,
    val options: PasswordOptions = PasswordOptions(),
    val generated: String = "",
    val scanned: WifiCredentials? = null,
    val scannedOther: Boolean = false,
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val wifi = WifiRepository(application)
    private val mutable = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = mutable.asStateFlow()

    init {
        generatePassword()
    }

    /** Fills in the connected network's name when Android allows reading it. */
    fun prefillSsid() {
        if (state.value.ssid.isNotEmpty()) return
        wifi.telemetry().ssid?.let { name -> mutable.update { it.copy(ssid = name) } }
    }

    fun setSsid(value: String) = edit { copy(ssid = value.take(32)) }
    fun setPassword(value: String) = edit { copy(password = value.take(64)) }
    fun setSecurity(value: WifiSecurity) = edit { copy(security = value) }
    fun setHidden(value: Boolean) = edit { copy(hidden = value) }

    /** Any change to the form makes the previous card stale. */
    private fun edit(change: ToolsUiState.() -> ToolsUiState) =
        mutable.update { it.change().copy(card = null, message = null) }

    fun createCard(arabic: Boolean) {
        val form = state.value
        val problem = when {
            form.ssid.isBlank() -> ToolMessage.SSID_REQUIRED
            !WifiQr.isValidPassword(form.password, form.security) -> ToolMessage.BAD_PASSWORD
            else -> null
        }
        if (problem != null) {
            mutable.update { it.copy(message = problem, card = null) }
            return
        }
        viewModelScope.launch {
            val payload = WifiQr.build(
                WifiCredentials(form.ssid, form.password, form.security, form.hidden),
            )
            val card = withContext(Dispatchers.Default) { QrCard.render(payload, form.ssid, arabic) }
            mutable.update { it.copy(card = card, message = null) }
        }
    }

    fun saveCard() {
        val card = state.value.card ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { QrCard.saveToGallery(getApplication(), card) }
            mutable.update { it.copy(message = if (saved) ToolMessage.SAVED else ToolMessage.SAVE_FAILED) }
        }
    }

    fun storageDenied() = mutable.update { it.copy(message = ToolMessage.STORAGE_DENIED) }

    fun shareFailed() = mutable.update { it.copy(message = ToolMessage.SHARE_FAILED) }

    fun setOptions(options: PasswordOptions) {
        // At least one character set must stay selected.
        if (PasswordGenerator.characterSets(options).isEmpty()) return
        mutable.update { it.copy(options = options) }
        generatePassword()
    }

    fun generatePassword() =
        mutable.update { it.copy(generated = PasswordGenerator.generate(it.options)) }

    fun useGenerated() = edit {
        copy(password = generated, security = if (security == WifiSecurity.WPA3) security else WifiSecurity.WPA)
    }

    fun onScanned(raw: String) {
        val credentials = WifiQr.parse(raw)
        mutable.update { it.copy(scanned = credentials, scannedOther = credentials == null) }
    }

    fun clearScan() = mutable.update { it.copy(scanned = null, scannedOther = false) }
}
