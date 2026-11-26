package com.capji.signalpro.data

import com.capji.signalpro.core.DnsMessage
import com.capji.signalpro.core.DnsProvider
import com.capji.signalpro.core.median
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

data class BenchResult(
    val provider: DnsProvider,
    /** Median response time of the successful queries, null when nothing answered. */
    val latencyMs: Double?,
    val lossPercent: Int,
) {
    /** Lower is better: every percent of loss costs as much as 10 ms of delay. */
    val score: Double get() = latencyMs?.let { it + lossPercent * 10 } ?: Double.MAX_VALUE
}

/** Times real DNS queries sent over UDP port 53. */
object DnsBenchmark {
    private val domains = listOf(
        "google.com", "youtube.com", "cloudflare.com", "wikipedia.org", "whatsapp.com", "instagram.com",
    )
    private val random = SecureRandom()

    /** One query; returns the round-trip time in milliseconds or null on failure. */
    fun queryOnce(server: String, domain: String, timeoutMs: Int = 1500): Double? = runCatching {
        DatagramSocket().use { socket ->
            val query = DnsMessage.buildQuery(random.nextInt(0x10000), domain)
            // connect() makes the kernel drop datagrams from any other source.
            socket.connect(InetAddress.getByName(server), 53)
            val buffer = ByteArray(4096)
            val start = System.nanoTime()
            socket.send(DatagramPacket(query, query.size))
            val deadline = start + timeoutMs * 1_000_000L
            while (true) {
                val remaining = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
                if (remaining <= 0) break
                socket.soTimeout = remaining
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                val bytes = buffer.copyOf(reply.length)
                if (DnsMessage.isResponseTo(query, bytes)) {
                    return@use if (DnsMessage.isSuccessfulAnswer(bytes)) {
                        (System.nanoTime() - start) / 1_000_000.0
                    } else null
                }
            }
            null
        }
    }.getOrNull()

    suspend fun measure(provider: DnsProvider, server: String = provider.primary): BenchResult =
        withContext(Dispatchers.IO) {
            val samples = domains.map { domain ->
                ensureActive()
                queryOnce(server, domain)
            }
            val ok = samples.filterNotNull()
            BenchResult(provider, median(ok), (samples.size - ok.size) * 100 / samples.size)
        }

    /** Tests every provider in parallel and returns them fastest first. */
    suspend fun run(providers: List<DnsProvider>, onResult: (BenchResult) -> Unit = {}): List<BenchResult> =
        coroutineScope {
            providers.map { provider -> async { measure(provider).also(onResult) } }
                .awaitAll()
                .sortedBy { it.score }
        }
}
