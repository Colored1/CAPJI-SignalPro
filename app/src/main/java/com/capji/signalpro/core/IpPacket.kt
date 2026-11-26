package com.capji.signalpro.core

/** A UDP datagram extracted from a raw IPv4 packet read from the TUN interface. */
class UdpDatagram(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)

/** Just enough IPv4 / UDP / TCP handling for a DNS-only tunnel. */
object IpPacket {
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
    private const val IP_HEADER = 20
    private const val UDP_HEADER = 8
    private const val TCP_HEADER = 20

    fun parseUdp(packet: ByteArray, length: Int): UdpDatagram? {
        val headerLength = ipv4HeaderLength(packet, length, PROTOCOL_UDP) ?: return null
        if (length < headerLength + UDP_HEADER) return null
        val udpLength = u16(packet, headerLength + 4)
        if (udpLength < UDP_HEADER || headerLength + udpLength > length) return null
        return UdpDatagram(
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = u16(packet, headerLength),
            destinationPort = u16(packet, headerLength + 2),
            payload = packet.copyOfRange(headerLength + UDP_HEADER, headerLength + udpLength),
        )
    }

    /** Builds the reply to [request]: addresses and ports swapped, carrying [payload]. */
    fun buildUdpReply(request: UdpDatagram, payload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER + payload.size
        val packet = ByteArray(IP_HEADER + udpLength)
        writeIpHeader(packet, PROTOCOL_UDP, request.destinationAddress, request.sourceAddress)
        putU16(packet, IP_HEADER, request.destinationPort)
        putU16(packet, IP_HEADER + 2, request.sourcePort)
        putU16(packet, IP_HEADER + 4, udpLength)
        payload.copyInto(packet, IP_HEADER + UDP_HEADER)
        val checksum = transportChecksum(packet, PROTOCOL_UDP, udpLength)
        // For UDP a computed zero is transmitted as all ones.
        putU16(packet, IP_HEADER + 6, if (checksum == 0) 0xFFFF else checksum)
        return packet
    }

    /**
     * The tunnel only serves DNS over UDP. Any TCP segment sent to the virtual resolver
     * (for example an opportunistic DNS-over-TLS probe) gets an immediate reset so the
     * system falls back to UDP without waiting for a timeout.
     */
    fun buildTcpReset(packet: ByteArray, length: Int): ByteArray? {
        val headerLength = ipv4HeaderLength(packet, length, PROTOCOL_TCP) ?: return null
        if (length < headerLength + TCP_HEADER) return null
        val flags = packet[headerLength + 13].toInt() and 0xFF
        if (flags and 0x04 != 0) return null // never answer a reset with a reset
        val sequence = u32(packet, headerLength + 4)
        val acknowledgement = u32(packet, headerLength + 8)
        val dataOffset = ((packet[headerLength + 12].toInt() and 0xF0) ushr 4) * 4
        val totalLength = minOf(u16(packet, 2), length)
        var consumed = (totalLength - headerLength - dataOffset).coerceAtLeast(0).toLong()
        if (flags and 0x02 != 0) consumed++ // SYN
        if (flags and 0x01 != 0) consumed++ // FIN

        val reply = ByteArray(IP_HEADER + TCP_HEADER)
        writeIpHeader(reply, PROTOCOL_TCP, packet.copyOfRange(16, 20), packet.copyOfRange(12, 16))
        putU16(reply, IP_HEADER, u16(packet, headerLength + 2))
        putU16(reply, IP_HEADER + 2, u16(packet, headerLength))
        val hasAck = flags and 0x10 != 0
        putU32(reply, IP_HEADER + 4, if (hasAck) acknowledgement else 0L)
        putU32(reply, IP_HEADER + 8, (sequence + consumed) and 0xFFFFFFFFL)
        reply[IP_HEADER + 12] = 0x50
        reply[IP_HEADER + 13] = if (hasAck) 0x04 else 0x14 // RST or RST+ACK
        putU16(reply, IP_HEADER + 16, transportChecksum(reply, PROTOCOL_TCP, TCP_HEADER))
        return reply
    }

    /** RFC 1071 one's-complement sum. */
    fun checksum(data: ByteArray, offset: Int, length: Int, initial: Long = 0): Int {
        var sum = initial
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += u16(data, index)
            index += 2
        }
        if (index < end) sum += (data[index].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun transportChecksum(packet: ByteArray, protocol: Int, segmentLength: Int): Int {
        var pseudo = (protocol + segmentLength).toLong()
        for (offset in 12 until 20 step 2) pseudo += u16(packet, offset)
        return checksum(packet, IP_HEADER, segmentLength, pseudo)
    }

    private fun ipv4HeaderLength(packet: ByteArray, length: Int, protocol: Int): Int? {
        if (length < IP_HEADER || length > packet.size) return null
        if ((packet[0].toInt() and 0xF0) != 0x40) return null
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < IP_HEADER || headerLength > length) return null
        if (packet[9].toInt() != protocol) return null
        // Fragments are not reassembled.
        if (u16(packet, 6) and 0x3FFF != 0) return null
        return headerLength
    }

    private fun writeIpHeader(packet: ByteArray, protocol: Int, source: ByteArray, destination: ByteArray) {
        packet[0] = 0x45
        putU16(packet, 2, packet.size)
        putU16(packet, 6, 0x4000) // don't fragment
        packet[8] = 64            // TTL
        packet[9] = protocol.toByte()
        source.copyInto(packet, 12)
        destination.copyInto(packet, 16)
        putU16(packet, 10, checksum(packet, 0, IP_HEADER))
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun u32(data: ByteArray, offset: Int): Long =
        (u16(data, offset).toLong() shl 16) or u16(data, offset + 2).toLong()

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private fun putU32(data: ByteArray, offset: Int, value: Long) {
        putU16(data, offset, (value ushr 16).toInt())
        putU16(data, offset + 2, value.toInt())
    }
}
