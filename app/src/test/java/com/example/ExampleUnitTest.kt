package com.example

import com.example.data.local.ServerConfigEntity
import com.example.data.parser.ConfigParser
import com.example.data.parser.CountryDetector
import com.example.vpn.TrafficStats
import com.example.vpn.core.CoreConfigBuilder
import com.example.vpn.core.DefaultVpnCoreEngine
import com.example.vpn.core.TunnelConfig
import com.example.vpn.core.VpnCoreState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun configParser_parsesVlessRealityWithIpv6AndParams() {
        val link = "vless://9f4e45ab-234e-4f01-92b3-a1286c8f4955@[2001:db8::1]:443?type=tcp&security=reality&sni=speed.cloudflare.com&fp=chrome&pbk=1111111111111111111111111111111111111111111=&sid=12345678&flow=xtls-rprx-vision#Javad%20IPv6"
        val parsed = ConfigParser.parseSingleLink(link, "test_source", 1)
        assertNotNull(parsed)
        assertEquals("vless", parsed?.protocol)
        assertEquals("2001:db8::1", parsed?.host)
        assertEquals(443, parsed?.port)
        assertEquals("9f4e45ab-234e-4f01-92b3-a1286c8f4955", parsed?.uuidOrPassword)
        assertEquals("reality", parsed?.securityType)
        assertEquals("speed.cloudflare.com", parsed?.sni)
        assertEquals("1111111111111111111111111111111111111111111=", parsed?.publicKey)
        assertEquals("12345678", parsed?.shortId)
        assertEquals("chrome", parsed?.fingerprint)
        assertEquals("xtls-rprx-vision", parsed?.flow)
    }

    @Test
    fun configParser_repairsBase64Padding() {
        // "Hello World!" in base64 is "SGVsbG8gV29ybGQh" (length 16, already multiple of 4)
        // "Hello World" in base64 is "SGVsbG8gV29ybGQ=" (missing padding -> "SGVsbG8gV29ybGQ")
        val unpadded = "SGVsbG8gV29ybGQ"
        val decoded = ConfigParser.decodeBase64(unpadded)
        assertEquals("Hello World", decoded)
    }

    @Test
    fun configParser_rejectsOpenVpnAndInvalidShadowsocks() {
        // OpenVPN link must be rejected
        val ovpnLink = "openvpn://public-vpn-90.opengw.net:1195#OpenVPN%20Test"
        val parsedOvpn = ConfigParser.parseSingleLink(ovpnLink, "test_source", 1)
        assertNull(parsedOvpn)

        // Invalid Shadowsocks without host must be rejected (no 127.0.0.1 fallback)
        val invalidSs = "ss://invalid-content#tag"
        val parsedSs = ConfigParser.parseSingleLink(invalidSs, "test_source", 1)
        assertNull(parsedSs)
    }

    @Test
    fun coreConfigBuilder_buildsValidXrayJson() {
        val server = ServerConfigEntity(
            name = "Javad Reality Server",
            protocol = "vless",
            host = "us-east.javadsolvpn.net",
            port = 443,
            rawLink = "vless://uuid@us-east.javadsolvpn.net:443",
            uuidOrPassword = "9f4e45ab-234e-4f01-92b3-a1286c8f4955",
            securityType = "reality",
            transportType = "tcp",
            sni = "speed.cloudflare.com",
            publicKey = "1111111111111111111111111111111111111111111=",
            shortId = "12345678",
            fingerprint = "chrome",
            flow = "xtls-rprx-vision"
        )

        val jsonString = CoreConfigBuilder.buildConfigJson(server)
        assertNotNull(jsonString)
        val json = JSONObject(jsonString)

        // Verify inbounds
        val inbounds = json.getJSONArray("inbounds")
        assertTrue(inbounds.length() >= 2)
        assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))
        assertEquals(10808, inbounds.getJSONObject(0).getInt("port"))

        // Verify outbounds
        val outbounds = json.getJSONArray("outbounds")
        val proxy = outbounds.getJSONObject(0)
        assertEquals("vless", proxy.getString("protocol"))

        val streamSettings = proxy.getJSONObject("streamSettings")
        assertEquals("reality", streamSettings.getString("security"))
        val realitySettings = streamSettings.getJSONObject("realitySettings")
        assertEquals("speed.cloudflare.com", realitySettings.getString("serverName"))
        assertEquals("1111111111111111111111111111111111111111111=", realitySettings.getString("publicKey"))
        assertEquals("12345678", realitySettings.getString("shortId"))
        assertEquals("chrome", realitySettings.getString("fingerprint"))

        // Verify stats and policy
        assertTrue(json.has("stats"))
        assertTrue(json.has("policy"))
        val policySystem = json.getJSONObject("policy").getJSONObject("system")
        assertTrue(policySystem.getBoolean("statsOutboundUplink"))
        assertTrue(policySystem.getBoolean("statsOutboundDownlink"))
    }

    @Test
    fun vpnCoreEngine_managesSupportedProtocolsWithoutOpenVpn() = runBlocking {
        val engine = DefaultVpnCoreEngine()
        val protocols = engine.getSupportedProtocols()
        assertTrue(protocols.contains("VLESS"))
        assertTrue(protocols.contains("TROJAN"))
        assertTrue(protocols.contains("VMESS"))
        assertTrue(protocols.contains("SS"))
        assertFalse(protocols.contains("OPENVPN"))

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
