package com.capji.signalpro.core

import java.io.ByteArrayOutputStream

/** Minimal DNS wire-format helpers (RFC 1035). */
object DnsMessage {

    /** Builds a recursive A-record query. */
    fun buildQuery(id: Int, domain: String): ByteArray {
        val out = ByteArrayOutputStream(64)
        fun u16(value: Int) {
            out.write((value ushr 8) and 0xFF)
            out.write(value and 0xFF)
        }
        u16(id)
        u16(0x0100) // standard query, recursion desired
        u16(1)      // one question
        u16(0)
        u16(0)
        u16(0)
        domain.trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "Invalid DNS label" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        u16(1) // type A
        u16(1) // class IN
        return out.toByteArray()
    }

    fun id(message: ByteArray): Int =
        if (message.size < 2) -1 else ((message[0].toInt() and 0xFF) shl 8) or (message[1].toInt() and 0xFF)

    /** True when [response] is a DNS response carrying the same transaction id as [query]. */
    fun isResponseTo(query: ByteArray, response: ByteArray, length: Int = response.size): Boolean =
        length >= 12 && id(query) == id(response) && (response[2].toInt() and 0x80) != 0

    /** NOERROR (0) and NXDOMAIN (3) both prove the resolver answered properly. */
    fun isSuccessfulAnswer(response: ByteArray): Boolean {
        if (response.size < 12) return false
        val rcode = response[3].toInt() and 0x0F
        return rcode == 0 || rcode == 3
    }

    fun isTruncated(response: ByteArray): Boolean =
        response.size >= 3 && (response[2].toInt() and 0x02) != 0
}
