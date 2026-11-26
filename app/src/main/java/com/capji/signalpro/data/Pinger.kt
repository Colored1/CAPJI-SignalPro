package com.capji.signalpro.data

import com.capji.signalpro.core.PingOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

enum class ProbeMethod { ICMP, TCP }

object Pinger {

    /** One ICMP echo through the system ping binary (no special permission needed). */
    suspend fun icmp(host: String): Double? = runInterruptible(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = ProcessBuilder("/system/bin/ping", "-n", "-c", "1", "-W", "1", host)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2500, TimeUnit.MILLISECONDS)) return@runInterruptible null
            PingOutput.parseTimeMs(process.inputStream.bufferedReader().readText())
        } catch (_: IOException) {
            null
        } finally {
            process?.destroy()
        }
    }

    /**
     * Times a TCP handshake. A refused connection still proves the host answered, so it
     * counts as a round trip.
     */
    suspend fun tcp(host: String, port: Int, timeoutMs: Int = 1500): Double? =
        runInterruptible(Dispatchers.IO) {
            val start = System.nanoTime()
            fun elapsed() = (System.nanoTime() - start) / 1_000_000.0
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                elapsed()
            } catch (error: ConnectException) {
                if (error.message?.contains("ECONNREFUSED") == true) elapsed() else null
            } catch (_: IOException) {
                null
            }
        }

    /** Picks ICMP when the host answers it, otherwise TCP on [tcpPort]. */
    suspend fun chooseMethod(host: String, tcpPort: Int): ProbeMethod {
        repeat(2) { if (icmp(host) != null) return ProbeMethod.ICMP }
        return if (tcp(host, tcpPort) != null) ProbeMethod.TCP else ProbeMethod.ICMP
    }

    suspend fun probe(host: String, method: ProbeMethod, tcpPort: Int): Double? =
        if (method == ProbeMethod.ICMP) icmp(host) else tcp(host, tcpPort)
}
