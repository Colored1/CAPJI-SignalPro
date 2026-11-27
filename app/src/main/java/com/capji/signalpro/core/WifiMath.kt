package com.capji.signalpro.core

import kotlin.math.exp
import kotlin.math.roundToInt

enum class Band(val label: String) {
    GHZ_2_4("2.4 GHz"),
    GHZ_5("5 GHz"),
    GHZ_6("6 GHz"),
    UNKNOWN("—"),
}

/** A scanned access point reduced to what the analyzer needs. */
data class AccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val centerFrequency: Int = frequency,
    val widthMhz: Int = 20,
) {
    val band: Band get() = WifiMath.bandOf(frequency)
    val channel: Int? get() = WifiMath.channelOf(frequency)
}

data class ChannelScore(
    val channel: Int,
    /** 0 = completely free, 100 = saturated. */
    val congestion: Int,
    /** Number of access points whose spectrum overlaps this channel. */
    val overlapping: Int,
)

object WifiMath {

    fun bandOf(frequency: Int): Band = when (frequency) {
        in 2400..2500 -> Band.GHZ_2_4
        in 4900..5924 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> Band.UNKNOWN
    }

    fun channelOf(frequency: Int): Int? = when {
        frequency == 2484 -> 14
        frequency in 2412..2472 -> (frequency - 2407) / 5
        frequency == 5935 -> 2
        frequency in 5955..7115 -> (frequency - 5950) / 5
        frequency in 5000..5924 -> (frequency - 5000) / 5
        else -> null
    }

    fun frequencyOf(band: Band, channel: Int): Int = when (band) {
        Band.GHZ_2_4 -> if (channel == 14) 2484 else 2407 + channel * 5
        Band.GHZ_5 -> 5000 + channel * 5
        Band.GHZ_6 -> if (channel == 2) 5935 else 5950 + channel * 5
        Band.UNKNOWN -> 0
    }

    /** Maps -100 dBm → 0 % and -50 dBm → 100 %. */
    fun signalQuality(rssi: Int): Int = ((rssi + 100) * 2).coerceIn(0, 100)

    /** Channels worth recommending. DFS channels are left out on 5 GHz. */
    fun candidateChannels(band: Band): List<Int> = when (band) {
        Band.GHZ_2_4 -> listOf(1, 6, 11)
        Band.GHZ_5 -> listOf(36, 40, 44, 48, 149, 153, 157, 161)
        Band.GHZ_6 -> listOf(5, 21, 37, 53, 69, 85)
        Band.UNKNOWN -> emptyList()
    }

    /** Occupied spectrum width of an AP; legacy 20 MHz on 2.4 GHz spreads to about 22 MHz. */
    fun occupiedWidth(ap: AccessPoint): Int =
        if (ap.band == Band.GHZ_2_4 && ap.widthMhz <= 20) 22 else ap.widthMhz

    /**
     * Interference on [channel]: each neighbour contributes the fraction of the channel it
     * covers, weighted by how loud it is here.
     */
    fun score(band: Band, channel: Int, accessPoints: List<AccessPoint>): ChannelScore {
        val center = frequencyOf(band, channel)
        val half = if (band == Band.GHZ_2_4) 11.0 else 10.0
        var load = 0.0
        var count = 0
        accessPoints.filter { it.band == band }.forEach { ap ->
            val apHalf = occupiedWidth(ap) / 2.0
            val overlap = minOf(center + half, ap.centerFrequency + apHalf) -
                maxOf(center - half, ap.centerFrequency - apHalf)
            if (overlap > 0) {
                count++
                val strength = ((ap.rssi + 95) / 60.0).coerceIn(0.05, 1.0)
                load += (overlap / (half * 2)).coerceAtMost(1.0) * strength
            }
        }
        val congestion = ((1 - exp(-load / 1.5)) * 100).roundToInt().coerceIn(0, 100)
        return ChannelScore(channel, congestion, count)
    }

    /** A router change is only worth suggesting when it lowers congestion noticeably. */
    fun worthSwitching(current: ChannelScore, best: ChannelScore): Boolean =
        current.congestion - best.congestion > 5

    /**
     * Candidate channels ranked best first.
     *
     * @param ownBssid the connected router, excluded because it does not interfere with itself.
     */
    fun recommend(band: Band, accessPoints: List<AccessPoint>, ownBssid: String? = null): List<ChannelScore> {
        val neighbours = accessPoints.filterNot { it.bssid.equals(ownBssid, ignoreCase = true) }
        return candidateChannels(band)
            .map { score(band, it, neighbours) }
            .sortedWith(compareBy<ChannelScore> { it.congestion }.thenBy { it.overlapping })
    }
}

/** Pitch and tempo of the router-placement sonar. */
object SonarMapping {
    fun toneHz(quality: Int): Int = 350 + quality.coerceIn(0, 100) * 13
    fun intervalMs(quality: Int): Long = 1100L - quality.coerceIn(0, 100) * 95L / 10
}
