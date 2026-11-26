package com.capji.signalpro.ui.radar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capji.signalpro.audio.SonarPlayer
import com.capji.signalpro.core.WifiMath
import com.capji.signalpro.data.Pinger
import com.capji.signalpro.data.ProbeMethod
import com.capji.signalpro.data.RoomReading
import com.capji.signalpro.data.RoomStore
import com.capji.signalpro.data.WifiRepository
import com.capji.signalpro.data.WifiTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class RadarUiState(
    val telemetry: WifiTelemetry = WifiTelemetry(),
    val hasPermission: Boolean = false,
    val locationEnabled: Boolean = true,
    /** dBm samples, oldest first. */
    val rssiHistory: List<Float?> = emptyList(),
    /** Router round-trip samples in ms; null is a lost probe. */
    val pingHistory: List<Float?> = emptyList(),
    val sonarOn: Boolean = false,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val rooms: List<RoomReading> = emptyList(),
    /** 0..1 while a room is being measured. */
    val measuring: Float? = null,
    val measureFailed: Boolean = false,
)

class RadarViewModel(application: Application) : AndroidViewModel(application) {
    private val wifi = WifiRepository(application)
    private val store = RoomStore(application)
    private val sonar = SonarPlayer(application)
    private var measureJob: Job? = null

    private val mutable = MutableStateFlow(RadarUiState(rooms = store.load()))
    val state: StateFlow<RadarUiState> = mutable.asStateFlow()

    /** Live updates; runs only while the screen is visible and is cancelled otherwise. */
    suspend fun monitor(): Unit = coroutineScope {
        launch {
            while (true) {
                val telemetry = wifi.telemetry()
                mutable.update {
                    it.copy(
                        telemetry = telemetry,
                        hasPermission = wifi.hasLocationPermission(),
                        locationEnabled = wifi.isLocationEnabled(),
                        rssiHistory = (it.rssiHistory + telemetry.rssi?.toFloat()).takeLast(HISTORY),
                    )
                }
                delay(1000)
            }
        }
        launch {
            var gateway: String? = null
            var method = ProbeMethod.ICMP
            while (true) {
                val current = state.value.telemetry.gateway
                if (current == null) {
                    gateway = null
                    delay(1000)
                    continue
                }
                if (current != gateway) {
                    gateway = current
                    method = Pinger.chooseMethod(current, 80)
                }
                val time = Pinger.probe(current, method, 80)
                mutable.update { it.copy(pingHistory = (it.pingHistory + time?.toFloat()).takeLast(HISTORY)) }
                delay(1000)
            }
        }
        launch {
            state.map { it.sonarOn }.distinctUntilChanged().collectLatest { on ->
                if (on) {
                    sonar.run(
                        quality = { state.value.telemetry.quality },
                        sound = { state.value.sound },
                        haptics = { state.value.haptics },
                    )
                }
            }
        }
    }

    fun setSonar(on: Boolean) = mutable.update { it.copy(sonarOn = on) }
    fun setSound(on: Boolean) = mutable.update { it.copy(sound = on) }
    fun setHaptics(on: Boolean) = mutable.update { it.copy(haptics = on) }

    /** Averages several samples so one lucky reading does not decide a room. */
    fun measureRoom(name: String) {
        val room = name.trim()
        if (room.isEmpty() || measureJob?.isActive == true) return
        measureJob = viewModelScope.launch {
            val samples = mutableListOf<WifiTelemetry>()
            repeat(SAMPLES) { index ->
                mutable.update { it.copy(measuring = index / SAMPLES.toFloat(), measureFailed = false) }
                wifi.telemetry().takeIf { it.rssi != null }?.let(samples::add)
                delay(600)
            }
            if (samples.size < SAMPLES / 2) {
                mutable.update { it.copy(measuring = null, measureFailed = true) }
                return@launch
            }
            val rssi = samples.mapNotNull { it.rssi }.average().roundToInt()
            val last = samples.last()
            val rooms = store.upsert(
                RoomReading(
                    name = room,
                    rssi = rssi,
                    quality = WifiMath.signalQuality(rssi),
                    ssid = last.ssid,
                    band = last.band.label,
                    linkSpeedMbps = last.linkSpeedMbps,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            mutable.update { it.copy(rooms = rooms, measuring = null) }
        }
    }

    fun deleteRoom(name: String) {
        val rooms = store.delete(name)
        mutable.update { it.copy(rooms = rooms) }
    }

    private companion object {
        const val HISTORY = 60
        const val SAMPLES = 8
    }
}
