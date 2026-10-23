package com.example

import com.example.data.local.ServerConfigEntity
import com.example.data.parser.ConfigParser
import com.example.data.parser.CountryDetector
import com.example.vpn.TrafficStats
import com.example.vpn.core.DefaultVpnCoreEngine
import com.example.vpn.core.TunnelConfig
import com.example.vpn.core.VpnCoreState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun countryDetector_detectsAccurately() {
        val us = CountryDetector.detect("us-east.server.com #America")
        assertEquals("US", us.code)
        assertEquals("آمریکا", us.nameFa)

        val de = CountryDetector.detect("de-fra.server.com Frankfurt")
        assertEquals("DE", de.code)
        assertEquals("آلمان", de.nameFa)

        val jp = CountryDetector.detect("jp-tokyo.server.com")
        assertEquals("JP", jp.code)
        assertEquals("ژاپن", jp.nameFa)
    }

    @Test
    fun trafficStats_formatsAccurately() {
        val bytes1 = TrafficStats.formatBytes(512L)
        assertEquals("512 B", bytes1)

        val bytes2 = TrafficStats.formatBytes(1024L * 1024L * 5L)
        assertEquals("5.0 MB", bytes2)

        val stats = TrafficStats(durationSeconds = 125)
        assertEquals("02:05", stats.formattedDuration)
    }

    @Test
    fun configParser_parsesTrojanLink() {
        val link = "trojan://pass123@de.example.com:443?security=tls#Javad%20Test"
        val parsed = ConfigParser.parseSingleLink(link, "test_source", 1)
        assertNotNull(parsed)
        assertEquals("trojan", parsed?.protocol)
        assertEquals("de.example.com", parsed?.host)
        assertEquals(443, parsed?.port)
        assertTrue(parsed?.name?.contains("Javad 1") == true)
    }

    @Test
    fun configParser_parsesHubJson() {
        val hubJson = """
            {
              "updated_at": "2026-10-23T12:00:00Z",
              "v2ray": [
                {
                  "name": "Javad 1",
                  "link": "vless://9f4e45ab-234e-4f01-92b3-a1286c8f4955@us.server.com:443?security=reality#Javad%201",
                  "protocol": "vless"
                }
              ],
              "openvpn": [
                {
                  "name": "Javad VPN 1",
                  "ovpn_base64": "cmVtb3RlIGpwLnNlcnZlci5uZXQgMTE5NA==",
                  "country": "JP"
                }
              ]
            }
        """.trimIndent()

        val list = ConfigParser.parsePayload(hubJson, "hub_source")
        assertEquals(2, list.size)
        assertEquals("vless", list[0].protocol)
        assertEquals("openvpn", list[1].protocol)
    }

    @Test
    fun vpnCoreEngine_managesAdaptersAndSessionLifecycle() = runBlocking {
        val engine = DefaultVpnCoreEngine()
        val protocols = engine.getSupportedProtocols()
        assertTrue(protocols.contains("VLESS"))
        assertTrue(protocols.contains("TROJAN"))
        assertTrue(protocols.contains("VMESS"))
        assertTrue(protocols.contains("SS"))
        assertTrue(protocols.contains("OPENVPN"))

        val mockServer = ServerConfigEntity(
            name = "Javad 1 • US",
            protocol = "vless",
            host = "us-east.javadsolvpn.net",
            port = 443,
            rawLink = "vless://9f4e45ab@us-east.javadsolvpn.net:443?security=reality",
            countryCode = "US"
        )

        val config = TunnelConfig(server = mockServer)
        val prepResult = engine.prepareSession(config)
        assertTrue(prepResult.isSuccess)
        val params = prepResult.getOrThrow()
        assertEquals("VLESS", params.protocol)

        val sessionResult = engine.establishSession(params)
        assertTrue(sessionResult.isSuccess)
        assertTrue(engine.coreState.value is VpnCoreState.Established)
        assertNotNull(engine.activeSession.value)

        val termResult = engine.terminateSession("User disconnect")
        assertTrue(termResult.isSuccess)
        assertTrue(engine.coreState.value is VpnCoreState.Idle)
    }
}
