package com.capji.signalpro.core

import kotlin.math.abs

/** Statistics over a series of probes; a `null` sample is a lost probe. */
data class PingStats(
    val sent: Int = 0,
    val received: Int = 0,
    val averageMs: Double? = null,
    val minMs: Double? = null,
    val maxMs: Double? = null,
    val jitterMs: Double? = null,
) {
    val lossPercent: Double get() = if (sent == 0) 0.0 else (sent - received) * 100.0 / sent

    companion object {
        fun from(samples: List<Double?>): PingStats {
            val ok = samples.filterNotNull()
            // Jitter: mean absolute difference between consecutive replies.
            val jitter = if (ok.size < 2) null else ok.zipWithNext { a, b -> abs(b - a) }.average()
            return PingStats(
                sent = samples.size,
                received = ok.size,
                averageMs = if (ok.isEmpty()) null else ok.average(),
                minMs = ok.minOrNull(),
                maxMs = ok.maxOrNull(),
                jitterMs = jitter,
            )
        }
    }
}

fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

object PingOutput {
    private val time = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""")

    /** Extracts the round-trip time from the output of `/system/bin/ping -c 1`. */
    fun parseTimeMs(output: String): Double? = time.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
}

enum class Verdict { HEALTHY, LOCAL_WIFI, ISP, INTERNET_DOWN, NO_CONNECTION, ROUTER_SILENT_INTERNET_BAD }

/** Decides whether lag comes from the indoor Wi-Fi link or from beyond the router. */
object Diagnosis {
    const val ROUTER_MAX_MS = 30.0
    const val ROUTER_MAX_JITTER_MS = 15.0
    const val INTERNET_MAX_MS = 120.0
    const val INTERNET_MAX_JITTER_MS = 30.0
    const val MAX_LOSS_PERCENT = 5.0

    /**
     * @param router gateway statistics, or null when not on Wi-Fi / no gateway.
     * @param internet statistics per external target.
     */
    fun evaluate(router: PingStats?, internet: List<PingStats>): Verdict {
        val best = internet
            .filter { it.received > 0 }
            .minByOrNull { (it.averageMs ?: Double.MAX_VALUE) + it.lossPercent * 10 }
        // A router that answers nothing may simply ignore pings, so it proves nothing.
        val measurableRouter = router?.takeIf { it.received > 0 }
        val routerBad = measurableRouter != null && (
            measurableRouter.lossPercent > MAX_LOSS_PERCENT ||
                (measurableRouter.averageMs ?: 0.0) > ROUTER_MAX_MS ||
                (measurableRouter.jitterMs ?: 0.0) > ROUTER_MAX_JITTER_MS
            )
        if (best == null) {
            return when {
                measurableRouter == null -> Verdict.NO_CONNECTION
                routerBad -> Verdict.LOCAL_WIFI
                else -> Verdict.INTERNET_DOWN
            }
        }
        val internetBad = best.lossPercent > MAX_LOSS_PERCENT ||
            (best.averageMs ?: 0.0) > INTERNET_MAX_MS ||
            (best.jitterMs ?: 0.0) > INTERNET_MAX_JITTER_MS
        return when {
            routerBad -> Verdict.LOCAL_WIFI
            internetBad && router != null && measurableRouter == null -> Verdict.ROUTER_SILENT_INTERNET_BAD
            internetBad -> Verdict.ISP
            else -> Verdict.HEALTHY
        }
    }
}
