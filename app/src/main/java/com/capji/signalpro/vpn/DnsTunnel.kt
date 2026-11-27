package com.capji.signalpro.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.capji.signalpro.core.DnsMessage
import com.capji.signalpro.core.IpPacket
import com.capji.signalpro.core.UdpDatagram
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Reads packets from the TUN device, forwards DNS queries to the chosen resolver and
 * writes the answers back. Nothing is inspected beyond what is needed to relay it, and
 * nothing is logged or stored.
 */
class DnsTunnel(
    private val service: VpnService,
    private val tun: ParcelFileDescriptor,
    @Volatile var upstream: List<String>,
    private val onAnswered: () -> Unit,
    private val onFailure: () -> Unit,
) {
    @Volatile private var running = true
    private val output = FileOutputStream(tun.fileDescriptor)
    private val workers = ThreadPoolExecutor(
        8, 8, 10, TimeUnit.SECONDS,
        ArrayBlockingQueue(256),
        ThreadPoolExecutor.DiscardOldestPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }
    private val reader = Thread(::readLoop, "capji-dns-tunnel")

    fun start() = reader.start()

    fun stop() {
        running = false
        workers.shutdownNow()
        // The reader polls with a timeout, so it notices the flag quickly.
        runCatching { reader.join(1000) }
        runCatching { tun.close() }
    }

    private fun readLoop() {
        val input = FileInputStream(tun.fileDescriptor)
        val buffer = ByteArray(32767)
        val poll = StructPollfd().apply {
            fd = tun.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        try {
            while (running) {
                poll.revents = 0
                if (Os.poll(arrayOf(poll), 500) <= 0) continue
                if (poll.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP) != 0) break
                val length = input.read(buffer)
                if (length > 0) handle(buffer, length)
            }
        } catch (_: IOException) {
            // Interface closed.
        } catch (_: ErrnoException) {
            // Interface closed while polling.
        }
        if (running) onFailure()
    }

    private fun handle(packet: ByteArray, length: Int) {
        val datagram = IpPacket.parseUdp(packet, length)
        if (datagram != null) {
            if (datagram.destinationPort == 53 && datagram.payload.size >= 12) {
                runCatching { workers.execute { relay(datagram) } }
            }
            return
        }
        IpPacket.buildTcpReset(packet, length)?.let(::write)
    }

    private fun relay(request: UdpDatagram) {
        for (server in upstream) {
            if (!running) return
            val answer = exchangeUdp(server, request.payload) ?: continue
            val complete = if (DnsMessage.isTruncated(answer)) {
                exchangeTcp(server, request.payload)?.takeIf { it.size <= MAX_REPLY } ?: answer
            } else answer
            write(IpPacket.buildUdpReply(request, complete))
            onAnswered()
            return
        }
    }

    private fun exchangeUdp(server: String, query: ByteArray): ByteArray? = runCatching {
        DatagramSocket().use { socket ->
            service.protect(socket)
            socket.connect(InetAddress.getByName(server), 53)
            socket.soTimeout = TIMEOUT_MS
            socket.send(DatagramPacket(query, query.size))
            val buffer = ByteArray(MAX_REPLY)
            val reply = DatagramPacket(buffer, buffer.size)
            var result: ByteArray? = null
            val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L
            while (result == null && System.nanoTime() < deadline) {
                reply.length = buffer.size
                socket.receive(reply)
                if (DnsMessage.isResponseTo(query, buffer, reply.length)) result = buffer.copyOf(reply.length)
            }
            result
        }
    }.getOrNull()

    private fun exchangeTcp(server: String, query: ByteArray): ByteArray? = runCatching {
        Socket().use { socket ->
            socket.bind(null)
            service.protect(socket)
            socket.connect(InetSocketAddress(server, 53), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val out = DataOutputStream(socket.getOutputStream())
            out.writeShort(query.size)
            out.write(query)
            out.flush()
            val input = DataInputStream(socket.getInputStream())
            val answer = ByteArray(input.readUnsignedShort())
            input.readFully(answer)
            answer.takeIf { DnsMessage.isResponseTo(query, it) }
        }
    }.getOrNull()

    @Synchronized
    private fun write(packet: ByteArray) {
        if (!running) return
        try {
            output.write(packet)
        } catch (_: IOException) {
            // Dropped packet: the resolver client retries.
        }
    }

    companion object {
        const val MTU = 4096
        private const val MAX_REPLY = MTU - 28
        private const val TIMEOUT_MS = 2500
    }
}
