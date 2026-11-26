package com.capji.signalpro

import com.capji.signalpro.core.AccessPoint
import com.capji.signalpro.core.Band
import com.capji.signalpro.core.Diagnosis
import com.capji.signalpro.core.DnsMessage
import com.capji.signalpro.core.DnsServers
import com.capji.signalpro.core.IpPacket
import com.capji.signalpro.core.PasswordGenerator
import com.capji.signalpro.core.PasswordOptions
import com.capji.signalpro.core.PingOutput
import com.capji.signalpro.core.PingStats
import com.capji.signalpro.core.SonarMapping
import com.capji.signalpro.core.UdpDatagram
import com.capji.signalpro.core.UpdatePolicy
import com.capji.signalpro.core.Verdict
import com.capji.signalpro.core.WifiCredentials
import com.capji.signalpro.core.WifiMath
import com.capji.signalpro.core.WifiQr
import com.capji.signalpro.core.WifiSecurity
import com.capji.signalpro.core.median
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    private val app = byteArrayOf(10, 215.toByte(), 173.toByte(), 1)
    private val resolver = byteArrayOf(10, 215.toByte(), 173.toByte(), 2)

    @Test
    fun dnsQueryHasExpectedWireFormat() {
        val query = DnsMessage.buildQuery(0xBEEF, "example.com")
        assertEquals(0xBEEF, DnsMessage.id(query))
        assertEquals(12 + 13 + 4, query.size)
        assertEquals(7, query[12].toInt())
        val response = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte() }
        assertTrue(DnsMessage.isResponseTo(query, response))
        assertTrue(DnsMessage.isSuccessfulAnswer(response))
        assertFalse(DnsMessage.isResponseTo(query, query))
        assertFalse(DnsMessage.isTruncated(response))
        assertFalse(DnsMessage.isSuccessfulAnswer(response.copyOf().also { it[3] = 0x82.toByte() }))
    }

    @Test
    fun udpReplyRoundTripsWithValidChecksums() {
        val request = UdpDatagram(app, resolver, 40000, 53, byteArrayOf(1, 2, 3))
        val packet = IpPacket.buildUdpReply(request, byteArrayOf(9, 8, 7, 6, 5))
        val parsed = IpPacket.parseUdp(packet, packet.size)!!
        assertArrayEquals(resolver, parsed.sourceAddress)
        assertArrayEquals(app, parsed.destinationAddress)
        assertEquals(53, parsed.sourcePort)
        assertEquals(40000, parsed.destinationPort)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6, 5), parsed.payload)
        // A correct checksum makes the one's-complement sum verify to zero.
        assertEquals(0, IpPacket.checksum(packet, 0, 20))
        var pseudo = (17 + packet.size - 20).toLong()
        for (i in 12 until 20 step 2) pseudo += ((packet[i].toInt() and 255) shl 8) or (packet[i + 1].toInt() and 255)
        assertEquals(0, IpPacket.checksum(packet, 20, packet.size - 20, pseudo))
    }

    @Test
    fun malformedPacketsAreIgnored() {
        assertNull(IpPacket.parseUdp(ByteArray(10), 10))
        assertNull(IpPacket.parseUdp(ByteArray(40), 40))
        val good = IpPacket.buildUdpReply(UdpDatagram(app, resolver, 1, 53, ByteArray(4)), ByteArray(4))
        assertNull(IpPacket.parseUdp(good, good.size - 2))
    }

    @Test
    fun tcpSynIsAnsweredWithReset() {
        val syn = ByteArray(40)
        syn[0] = 0x45; syn[3] = 40; syn[9] = 6
        app.copyInto(syn, 12); resolver.copyInto(syn, 16)
        syn[20] = 0x9C.toByte(); syn[21] = 0x40; syn[23] = 0x55 // 40000 -> 85
        syn[27] = 100 // sequence
        syn[32] = 0x50; syn[33] = 0x02
        val reset = IpPacket.buildTcpReset(syn, syn.size)!!
        assertEquals(0x14, reset[33].toInt())
        assertEquals(101, reset[31].toInt()) // ack = seq + 1
        assertArrayEquals(resolver, reset.copyOfRange(12, 16))
        assertNull(IpPacket.buildTcpReset(reset, reset.size))
    }

    @Test
    fun ipValidation() {
        assertTrue(DnsServers.isUsableIpv4("94.140.14.14"))
        listOf("", "1.1.1", "256.1.1.1", "dns.google", "127.0.0.1", "0.0.0.0", "224.0.0.1", "1.1.1.1.")
            .forEach { assertFalse(it, DnsServers.isUsableIpv4(it)) }
        assertNull(DnsServers.custom("1.1.1.1", "bad"))
        assertEquals(1, DnsServers.custom(" 1.1.1.1 ", "")!!.servers.size)
    }

    @Test
    fun pingStatistics() {
        val stats = PingStats.from(listOf(10.0, 20.0, null, 14.0))
        assertEquals(4, stats.sent)
        assertEquals(25.0, stats.lossPercent, 1e-9)
        assertEquals(8.0, stats.jitterMs!!, 1e-9)
        assertEquals(44.0 / 3, stats.averageMs!!, 1e-9)
        assertNull(PingStats.from(listOf(null)).averageMs)
        assertEquals(2.5, median(listOf(4.0, 1.0, 2.0, 3.0))!!, 1e-9)
        assertEquals(12.4, PingOutput.parseTimeMs("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.4 ms")!!, 1e-9)
        assertNull(PingOutput.parseTimeMs("1 packets transmitted, 0 received, 100% packet loss, time 0ms"))
    }

    @Test
    fun diagnosisSeparatesWifiFromProvider() {
        fun stats(ms: Double?) = PingStats.from(List(10) { ms })
        assertEquals(Verdict.HEALTHY, Diagnosis.evaluate(stats(3.0), listOf(stats(40.0))))
        assertEquals(Verdict.LOCAL_WIFI, Diagnosis.evaluate(stats(90.0), listOf(stats(130.0))))
        assertEquals(Verdict.ISP, Diagnosis.evaluate(stats(3.0), listOf(stats(300.0))))
        assertEquals(Verdict.INTERNET_DOWN, Diagnosis.evaluate(stats(3.0), listOf(stats(null))))
        assertEquals(Verdict.NO_CONNECTION, Diagnosis.evaluate(null, listOf(stats(null))))
        assertEquals(Verdict.ROUTER_SILENT_INTERNET_BAD, Diagnosis.evaluate(stats(null), listOf(stats(300.0))))
        assertEquals(Verdict.HEALTHY, Diagnosis.evaluate(null, listOf(stats(null), stats(50.0))))
    }

    @Test
    fun channelConversionAndQuality() {
        assertEquals(1, WifiMath.channelOf(2412))
        assertEquals(14, WifiMath.channelOf(2484))
        assertEquals(36, WifiMath.channelOf(5180))
        assertEquals(1, WifiMath.channelOf(5955))
        assertEquals(Band.GHZ_6, WifiMath.bandOf(5955))
        assertEquals(2437, WifiMath.frequencyOf(Band.GHZ_2_4, 6))
        assertEquals(0, WifiMath.signalQuality(-110))
        assertEquals(50, WifiMath.signalQuality(-75))
        assertEquals(100, WifiMath.signalQuality(-30))
    }

    @Test
    fun recommendationAvoidsBusyChannels() {
        val aps = listOf(
            AccessPoint("a", "aa", -40, 2412),
            AccessPoint("b", "bb", -45, 2412),
            AccessPoint("c", "cc", -70, 2437),
            AccessPoint("mine", "me", -30, 2462),
        )
        val ranked = WifiMath.recommend(Band.GHZ_2_4, aps, ownBssid = "ME")
        assertEquals(listOf(11, 6, 1), ranked.map { it.channel })
        assertEquals(0, ranked.first().congestion)
        // Channels 1 and 6 do not overlap each other.
        assertEquals(1, WifiMath.score(Band.GHZ_2_4, 6, aps).overlapping)
    }

    @Test
    fun wifiQrRoundTripWithSpecialCharacters() {
        val original = WifiCredentials("محل; \"CAPJI\":1", "p\\a;s,s:\"w", WifiSecurity.WPA, hidden = true)
        assertEquals(original, WifiQr.parse(WifiQr.build(original)))
        assertEquals("WIFI:T:nopass;S:Guest;;", WifiQr.build(WifiCredentials("Guest", "x", WifiSecurity.OPEN)))
        assertEquals(WifiSecurity.WPA3, WifiQr.parse("WIFI:S:n;T:SAE;P:12345678;;")!!.security)
        assertNull(WifiQr.parse("https://example.com"))
        assertNull(WifiQr.parse("WIFI:T:WPA;P:abc;;"))
        assertFalse(WifiQr.isValidPassword("short", WifiSecurity.WPA))
        assertTrue(WifiQr.isValidPassword("longenough", WifiSecurity.WPA))
    }

    @Test
    fun passwordsHonourOptions() {
        val options = PasswordOptions(length = 20)
        val sets = PasswordGenerator.characterSets(options)
        repeat(200) {
            val password = PasswordGenerator.generate(options)
            assertEquals(20, password.length)
            sets.forEach { set -> assertTrue(password.any { it in set }) }
            assertFalse(password.any { it in "O0oIl1|" })
        }
        val digitsOnly = PasswordOptions(8, lowercase = false, uppercase = false, symbols = false)
        assertTrue(PasswordGenerator.generate(digitsOnly).all { it.isDigit() })
        assertEquals(128.0, PasswordGenerator.entropyBits(32, 16), 1e-9)
    }

    @Test
    fun updatesOnlyPointToGooglePlay() {
        assertNull(UpdatePolicy.evaluate(5, 5, true, "", ""))
        val update = UpdatePolicy.evaluate(5, 6, true, " new ", "https://evil.example/app.apk")
        assertNotNull(update)
        assertEquals(UpdatePolicy.STORE_URL, update!!.url)
        assertEquals(UpdatePolicy.STORE_URL, UpdatePolicy.safeUrl("http://play.google.com/x"))
        assertEquals("market://details?id=a", UpdatePolicy.safeUrl("market://details?id=a"))
    }

    @Test
    fun sonarGetsHigherAndFasterWithSignal() {
        assertTrue(SonarMapping.toneHz(90) > SonarMapping.toneHz(10))
        assertTrue(SonarMapping.intervalMs(90) < SonarMapping.intervalMs(10))
        assertEquals(150L, SonarMapping.intervalMs(100))
    }
}
