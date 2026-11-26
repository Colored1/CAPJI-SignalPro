package com.capji.signalpro.ui.analyzer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capji.signalpro.core.AccessPoint
import com.capji.signalpro.core.Band
import com.capji.signalpro.core.Diagnosis
import com.capji.signalpro.core.PingStats
import com.capji.signalpro.core.Verdict
import com.capji.signalpro.data.NetworkInfoSource
import com.capji.signalpro.data.Pinger
import com.capji.signalpro.data.ProbeMethod
import com.capji.signalpro.data.WifiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PingTarget(
    val host: String,
    val isRouter: Boolean,
    val tcpPort: Int,
    val method: ProbeMethod = ProbeMethod.ICMP,
    val samples: List<Double?> = emptyList(),
) {
    val stats: PingStats get() = PingStats.from(samples)
}

data class AnalyzerUiState(
    val hasPermission: Boolean = false,
    val locationEnabled: Boolean = true,
    val scanning: Boolean = false,
    val scanned: Boolean = false,
    val fresh: Boolean = true,
    val accessPoints: List<AccessPoint> = emptyList(),
    val ownBssid: String? = null,
    val band: Band = Band.GHZ_2_4,
    val testing: Boolean = false,
    val progress: Float = 0f,
    val targets: List<PingTarget> = emptyList(),
    val verdict: Verdict? = null,
)

class AnalyzerViewModel(application: Application) : AndroidViewModel(application) {
    private val wifi = WifiRepository(application)
    private val network = NetworkInfoSource(application)
    private var pingJob: Job? = null
    private var bandChosen = false

    private val mutable = MutableStateFlow(AnalyzerUiState())
    val state: StateFlow<AnalyzerUiState> = mutable.asStateFlow()

    fun refreshEnvironment() = mutable.update {
        it.copy(hasPermission = wifi.hasLocationPermission(), locationEnabled = wifi.isLocationEnabled())
    }

    fun selectBand(band: Band) {
        bandChosen = true
        mutable.update { it.copy(band = band) }
    }

    fun scan() {
        refreshEnvironment()
        val current = state.value
        if (current.scanning || !current.hasPermission || !current.locationEnabled) return
        viewModelScope.launch {
            mutable.update { it.copy(scanning = true) }
            val outcome = wifi.scan()
            val telemetry = wifi.telemetry()
            mutable.update {
                it.copy(
                    scanning = false,
                    scanned = true,
                    fresh = outcome.fresh,
                    accessPoints = outcome.accessPoints,
                    ownBssid = telemetry.bssid,
                    band = if (!bandChosen && telemetry.band != Band.UNKNOWN) telemetry.band else it.band,
                )
            }
        }
    }

    /** Probes the router and two internet hosts side by side. */
    fun startPingTest() {
        if (pingJob?.isActive == true) return
        pingJob = viewModelScope.launch {
            try {
                val gateway = network.gatewayOf(network.wifiLink())
                val initial = listOfNotNull(
                    gateway?.let { PingTarget(it, isRouter = true, tcpPort = 80) },
                    PingTarget("1.1.1.1", isRouter = false, tcpPort = 443),
                    PingTarget("8.8.8.8", isRouter = false, tcpPort = 443),
                )
                mutable.update { it.copy(testing = true, progress = 0f, targets = initial, verdict = null) }
                val targets = coroutineScope {
                    initial.map { async { it.copy(method = Pinger.chooseMethod(it.host, it.tcpPort)) } }.awaitAll()
                }
                mutable.update { it.copy(targets = targets) }
                repeat(ROUNDS) { round ->
                    val started = System.currentTimeMillis()
                    val replies = coroutineScope {
                        state.value.targets
                            .map { async { Pinger.probe(it.host, it.method, it.tcpPort) } }
                            .awaitAll()
                    }
                    mutable.update { current ->
                        current.copy(
                            progress = (round + 1) / ROUNDS.toFloat(),
                            targets = current.targets.mapIndexed { index, target ->
                                target.copy(samples = target.samples + replies[index])
                            },
                        )
                    }
                    delay((500 - (System.currentTimeMillis() - started)).coerceAtLeast(50))
                }
                mutable.update { current ->
                    current.copy(
                        verdict = Diagnosis.evaluate(
                            router = current.targets.firstOrNull { it.isRouter }?.stats,
                            internet = current.targets.filterNot { it.isRouter }.map { it.stats },
                        ),
                    )
                }
            } finally {
                mutable.update { it.copy(testing = false) }
            }
        }
    }

    fun stopPingTest() {
        pingJob?.cancel()
    }

    private companion object {
        const val ROUNDS = 30
    }
}
