package com.example

import com.example.data.local.PreloadedConfigs
import com.example.vpn.core.CoreConfigBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreConfigBuilderTest {

    @Test
    fun buildConfigJson_generatesValidJsonForAllTwelvePreloadedConfigs() {
        assertEquals(12, PreloadedConfigs.DEFAULT_SERVERS.size)

        for (server in PreloadedConfigs.DEFAULT_SERVERS) {
            val jsonStr = CoreConfigBuilder.buildConfigJson(server)
            assertNotNull(jsonStr)
            assertTrue(jsonStr.isNotBlank())

            val root = JSONObject(jsonStr)

            // 1. Verify Inbounds
            val inbounds = root.getJSONArray("inbounds")
            assertTrue("Must contain at least socks, http, and tun inbounds", inbounds.length() >= 3)

            var hasTunInbound = false
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.getJSONObject(i)
                val protocol = inbound.getString("protocol")
                if (protocol == "tun") {
                    hasTunInbound = true
                    val settings = inbound.getJSONObject("settings")
                    assertEquals("tun0", settings.getString("name"))
                    assertEquals(1500, settings.getInt("MTU"))
                    assertFalse("tun inbound must not have port 0", inbound.has("port"))
                }
            }
            assertTrue("Native tun inbound must be present in inbounds", hasTunInbound)

            // 2. Verify Routing
            val routing = root.getJSONObject("routing")
            val rules = routing.getJSONArray("rules")
            assertTrue(rules.length() >= 2)

            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)
                if (rule.has("ip")) {
                    val ips = rule.getJSONArray("ip")
                    for (j in 0 until ips.length()) {
                        val ipStr = ips.getString(j)
                        assertFalse("Must NOT contain geoip:private without local dat file", ipStr.contains("geoip:"))
                    }
                }
            }

            // 3. Verify Outbounds
            val outbounds = root.getJSONArray("outbounds")
            val proxyOutbound = outbounds.getJSONObject(0)
            assertEquals("proxy", proxyOutbound.getString("tag"))
            val expectedProtocol = when (server.protocol) {
                "ss" -> "shadowsocks"
                else -> server.protocol
            }
            assertEquals(expectedProtocol, proxyOutbound.getString("protocol"))
        }
    }
}
