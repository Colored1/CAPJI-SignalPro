package com.capji.signalpro.ui.boost

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capji.signalpro.core.DnsProvider
import com.capji.signalpro.core.DnsServers
import com.capji.signalpro.data.BenchResult
import com.capji.signalpro.data.DnsBenchmark
import com.capji.signalpro.data.NetworkInfoSource
import com.capji.signalpro.data.Prefs
import com.capji.signalpro.vpn.CapjiDnsVpnService
import com.capji.signalpro.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class BoostPhase { IDLE, BENCHMARKING, CONNECTING, ACTIVE }

enum class BoostMessage { OFFLINE, NO_DNS_ANSWERED, START_FAILED, INVALID_CUSTOM_DNS, PERMISSION_DENIED }

data class BoostUiState(
    val phase: BoostPhase = BoostPhase.IDLE,
    val testing: Boolean = false,
    val providers: List<DnsProvider> = DnsServers.builtIn,
    /** Fastest first. */
    val results: List<BenchResult> = emptyList(),
    val providerResult: BenchResult? = null,
    /** Median DNS time of the ISP resolver, measured directly. */
    val beforeMs: Double? = null,
    /** Median DNS time measured through the active tunnel. */
    val afterMs: Double? = null,
    val autoSelect: Boolean = true,
    val selectedId: String = "cloudflare",
    val customPrimary: String = "",
    val customSecondary: String = "",
    val privateDns: String? = null,
    val message: BoostMessage? = null,
)

class BoostViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val network = NetworkInfoSource(application)
    private var job: Job? = null

    private val mutable = MutableStateFlow(
        BoostUiState(
            phase = if (VpnState.status.value.running) BoostPhase.ACTIVE else BoostPhase.IDLE,
            providers = prefs.allProviders(),
            autoSelect = prefs.autoSelectDns,
            selectedId = prefs.selectedProvider().id,
            customPrimary = prefs.customPrimary,
            customSecondary = prefs.customSecondary,
        ),
    )
    val state: StateFlow<BoostUiState> = mutable.asStateFlow()
    val vpn = VpnState.status

    val disclosureAccepted: Boolean get() = prefs.disclosureAccepted

    init {
        viewModelScope.launch {
            VpnState.status.collect { status ->
                mutable.update {
                    when {
                        status.running && it.phase == BoostPhase.IDLE -> it.copy(phase = BoostPhase.ACTIVE)
                        !status.running && it.phase == BoostPhase.ACTIVE ->
                            it.copy(phase = BoostPhase.IDLE, afterMs = null)
                        else -> it
                    }
                }
            }
        }
    }

    fun acceptDisclosure() {
        prefs.disclosureAccepted = true
    }

    fun permissionDenied() = mutable.update { it.copy(message = BoostMessage.PERMISSION_DENIED) }

    fun refreshEnvironment() = mutable.update { it.copy(privateDns = network.strictPrivateDns()) }

    /** One tap: measure, pick the resolver, start the tunnel, then measure through it. */
    fun boost() {
        if (state.value.phase != BoostPhase.IDLE) return
        // A speed test that is still running is replaced by the boost, which tests again.
        val previous = job
        job = viewModelScope.launch {
            previous?.cancelAndJoin()
            if (!network.isOnline()) {
                mutable.update { it.copy(message = BoostMessage.OFFLINE) }
                return@launch
            }
            mutable.update { it.copy(phase = BoostPhase.BENCHMARKING, message = null, afterMs = null) }
            refreshEnvironment()
            val results = benchmarkNow()
            val chosen = results.firstOrNull { it.provider.id == prefs.selectedDnsId }
            if (chosen?.latencyMs == null) {
                mutable.update { it.copy(phase = BoostPhase.IDLE, message = BoostMessage.NO_DNS_ANSWERED) }
                return@launch
            }
            mutable.update { it.copy(phase = BoostPhase.CONNECTING) }
            VpnState.clearError()
            val status = if (!CapjiDnsVpnService.start(getApplication())) null else withTimeoutOrNull(8_000) {
                VpnState.status.first { it.running || it.error != null }
            }
            if (status?.running != true) {
                mutable.update { it.copy(phase = BoostPhase.IDLE, message = BoostMessage.START_FAILED) }
                return@launch
            }
            measureThroughTunnel()
            // The user may have stopped the tunnel from its notification meanwhile.
            val running = VpnState.status.value.running
            mutable.update {
                if (running) it.copy(phase = BoostPhase.ACTIVE)
                else it.copy(phase = BoostPhase.IDLE, afterMs = null)
            }
        }
    }

    fun stop() {
        job?.cancel()
        CapjiDnsVpnService.stop(getApplication())
        mutable.update { it.copy(phase = BoostPhase.IDLE, testing = false, afterMs = null) }
    }

    fun benchmark() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            if (!network.isOnline()) {
                mutable.update { it.copy(message = BoostMessage.OFFLINE) }
                return@launch
            }
            mutable.update { it.copy(message = null) }
            refreshEnvironment()
            val results = benchmarkNow()
            if (results.none { it.latencyMs != null }) {
                mutable.update { it.copy(message = BoostMessage.NO_DNS_ANSWERED) }
            } else if (VpnState.status.value.running) {
                CapjiDnsVpnService.start(getApplication())
                delay(500)
                measureThroughTunnel()
            }
        }
    }

    private suspend fun benchmarkNow(): List<BenchResult> = coroutineScope {
        mutable.update { it.copy(testing = true, results = emptyList()) }
        try {
            val isp = async {
                network.providerDnsServers().firstOrNull()?.let { address ->
                    DnsBenchmark.measure(DnsProvider(DnsServers.ISP_ID, "ISP", address, null))
                }
            }
            val results = DnsBenchmark.run(prefs.allProviders()) { result ->
                mutable.update { state -> state.copy(results = (state.results + result).sortedBy { it.score }) }
            }
            val provider = isp.await()
            val best = results.firstOrNull { it.latencyMs != null }
            if (prefs.autoSelectDns && best != null) prefs.selectedDnsId = best.provider.id
            mutable.update {
                it.copy(
                    results = results,
                    providerResult = provider,
                    beforeMs = provider?.latencyMs,
                    selectedId = prefs.selectedProvider().id,
                )
            }
            results
        } finally {
            mutable.update { it.copy(testing = false) }
        }
    }

    /** Queries the virtual resolver, so the timing covers the whole tunnel path. */
    private suspend fun measureThroughTunnel() {
        val virtual = DnsProvider("tunnel", "Tunnel", CapjiDnsVpnService.VIRTUAL_DNS, null)
        delay(400)
        val result = DnsBenchmark.measure(virtual)
        mutable.update { it.copy(afterMs = result.latencyMs) }
    }

    fun setAutoSelect(enabled: Boolean) {
        prefs.autoSelectDns = enabled
        val best = state.value.results.firstOrNull { it.latencyMs != null }
        if (enabled && best != null) prefs.selectedDnsId = best.provider.id
        mutable.update { it.copy(autoSelect = enabled, selectedId = prefs.selectedProvider().id) }
        applyToRunningTunnel()
    }

    fun select(id: String) {
        prefs.autoSelectDns = false
        prefs.selectedDnsId = id
        mutable.update { it.copy(autoSelect = false, selectedId = id) }
        applyToRunningTunnel()
    }

    fun saveCustom(primary: String, secondary: String) {
        if (DnsServers.custom(primary, secondary) == null) {
            mutable.update { it.copy(message = BoostMessage.INVALID_CUSTOM_DNS) }
            return
        }
        prefs.customPrimary = primary.trim()
        prefs.customSecondary = secondary.trim()
        mutable.update {
            it.copy(
                providers = prefs.allProviders(),
                customPrimary = primary.trim(),
                customSecondary = secondary.trim(),
                results = it.results.filterNot { r -> r.provider.id == DnsServers.CUSTOM_ID },
                message = null,
            )
        }
        select(DnsServers.CUSTOM_ID)
    }

    private fun applyToRunningTunnel() {
        if (!VpnState.status.value.running || job?.isActive == true) return
        job = viewModelScope.launch {
            mutable.update { it.copy(afterMs = null) }
            CapjiDnsVpnService.start(getApplication())
            delay(300)
            measureThroughTunnel()
        }
    }
}
