package com.example.vpn.core

import com.example.data.local.ServerConfigEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds complete, compliant Xray-core JSON configurations from ServerConfigEntity.
 * Compatible with AndroidLibXrayLite (v2rayNG core) and standard Xray core.
 */
object CoreConfigBuilder {

    fun buildConfigJson(
        server: ServerConfigEntity,
        socksPort: Int = 10808,
        httpPort: Int = 10809
    ): String {
        val root = JSONObject()

        // 1. Log configuration
        val log = JSONObject().apply {
            put("loglevel", "warning")
        }
        root.put("log", log)

        // 2. Inbounds (SOCKS, HTTP, and Dokodemo for TUN)
        val inbounds = JSONArray()

        // SOCKS inbound
        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", socksPort)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            })
        }
        inbounds.put(socksInbound)

        // HTTP inbound
        val httpInbound = JSONObject().apply {
            put("tag", "http-in")
            put("port", httpPort)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject().apply {
                put("timeout", 0)
            })
        }
        inbounds.put(httpInbound)

        // Native TUN inbound for direct Android VpnService file descriptor processing
        val tunInbound = JSONObject().apply {
            put("tag", "tun-in")
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("name", "tun0")
                put("MTU", 1500)
                put("userLevel", 0)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            })
        }
        inbounds.put(tunInbound)
        root.put("inbounds", inbounds)

        // 3. Outbounds (Proxy, Direct, Block)
        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(server))

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().apply {
                put("domainStrategy", "AsIs")
            })
        }
        outbounds.put(directOutbound)

        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject().apply {
                put("response", JSONObject().apply {
                    put("type", "http")
                })
            })
        }
        outbounds.put(blockOutbound)
        root.put("outbounds", outbounds)

        // 4. Routing
        val routing = JSONObject().apply {
            // "AsIs" prevents client-side DNS lookup deadlock in censored networks;
            // domain requests are passed directly to the remote proxy server to resolve.
            put("domainStrategy", "AsIs")
            val rules = JSONArray()

            // Direct private IPs (LAN bypass without requiring external geoip.dat)
            val directRule = JSONObject().apply {
                put("type", "field")
                put("outboundTag", "direct")
                val lanCidrs = JSONArray().apply {
                    put("10.0.0.0/8")
                    put("172.16.0.0/12")
                    put("192.168.0.0/16")
                    put("127.0.0.0/8")
                    put("fc00::/7")
                    put("fe80::/10")
                }
                put("ip", lanCidrs)
            }
            rules.put(directRule)

            // Default proxy rule for all traffic
            val proxyRule = JSONObject().apply {
                put("type", "field")
                put("outboundTag", "proxy")
                put("network", "tcp,udp")
            }
            rules.put(proxyRule)

            put("rules", rules)
        }
        root.put("routing", routing)

        // 5. DNS
        val dns = JSONObject().apply {
            put("servers", JSONArray().apply {
                put("https+local://1.1.1.1/dns-query")
                put("1.1.1.1")
                put("8.8.8.8")
                put("localhost")
            })
        }
        root.put("dns", dns)

        // 6. Stats & Policy for real upload/download metrics
        root.put("stats", JSONObject())
        val policy = JSONObject().apply {
            put("system", JSONObject().apply {
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        }
        root.put("policy", policy)

        return root.toString(2)
    }

    private fun buildProxyOutbound(server: ServerConfigEntity): JSONObject {
        val proxy = JSONObject()
        proxy.put("tag", "proxy")

        val protocol = server.protocol.lowercase().trim()
        proxy.put("protocol", protocol)

        // Protocol-specific settings
        val settings = JSONObject()
        when (protocol) {
            "vless" -> {
                val vnext = JSONArray()
                val serverObj = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    val users = JSONArray()
                    val user = JSONObject().apply {
                        put("id", server.uuidOrPassword)
                        put("encryption", "none")
                        if (server.flow.isNotBlank()) {
                            put("flow", server.flow)
                        }
                    }
                    users.put(user)
                    put("users", users)
                }
                vnext.put(serverObj)
                settings.put("vnext", vnext)
            }
            "vmess" -> {
                val vnext = JSONArray()
                val serverObj = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    val users = JSONArray()
                    val user = JSONObject().apply {
                        put("id", server.uuidOrPassword)
                        put("alterId", server.alterId)
                        put("security", "auto")
                    }
                    users.put(user)
                    put("users", users)
                }
                vnext.put(serverObj)
                settings.put("vnext", vnext)
            }
            "trojan" -> {
                val servers = JSONArray()
                val serverObj = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    put("password", server.uuidOrPassword)
                }
                servers.put(serverObj)
                settings.put("servers", servers)
            }
            "ss", "shadowsocks" -> {
                proxy.put("protocol", "shadowsocks")
                val servers = JSONArray()
                val serverObj = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    val methodAndPass = parseShadowsocksUser(server.uuidOrPassword)
                    put("method", methodAndPass.first)
                    put("password", methodAndPass.second)
                    put("uot", true)
                }
                servers.put(serverObj)
                settings.put("servers", servers)
            }
        }
        proxy.put("settings", settings)

        // Stream Settings (network, security, TLS, Reality, WebSocket, gRPC, etc.)
        val streamSettings = JSONObject()
        val network = when (server.transportType.lowercase()) {
            "ws", "websocket" -> "ws"
            "grpc" -> "grpc"
            "httpupgrade" -> "httpupgrade"
            "xhttp" -> "xhttp"
            "h2", "http" -> "h2"
            else -> "tcp"
        }
        streamSettings.put("network", network)

        val sec = server.securityType.lowercase().trim()
        val effectiveSecurity = when {
            sec == "reality" -> "reality"
            sec == "tls" -> "tls"
            sec.contains("reality") -> "reality"
            sec.contains("tls") -> "tls"
            else -> "none"
        }
        streamSettings.put("security", effectiveSecurity)

        val effectiveSni = server.sni.ifBlank { server.host }

        // TLS settings
        if (effectiveSecurity == "tls") {
            val tlsSettings = JSONObject().apply {
                put("serverName", effectiveSni)
                put("allowInsecure", false)
                if (server.fingerprint.isNotBlank()) {
                    put("fingerprint", server.fingerprint)
                }
                val alpnList = if (server.alpn.isNotBlank()) {
                    server.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    listOf("h2", "http/1.1")
                }
                val alpnArr = JSONArray()
                alpnList.forEach { alpnArr.put(it) }
                put("alpn", alpnArr)
            }
            streamSettings.put("tlsSettings", tlsSettings)
        }

        // REALITY settings
        if (effectiveSecurity == "reality") {
            val realitySettings = JSONObject().apply {
                put("show", false)
                put("fingerprint", if (server.fingerprint.isNotBlank()) server.fingerprint else "chrome")
                put("serverName", effectiveSni)
                put("publicKey", server.publicKey)
                put("shortId", server.shortId)
                put("spiderX", "")
            }
            streamSettings.put("realitySettings", realitySettings)
        }

        // Transport-specific streamSettings
        when (network) {
            "ws" -> {
                val wsSettings = JSONObject().apply {
                    put("path", if (server.path.isNotBlank()) server.path else "/")
                    put("headers", JSONObject().apply {
                        put("Host", effectiveSni)
                    })
                }
                streamSettings.put("wsSettings", wsSettings)
            }
            "grpc" -> {
                val grpcSettings = JSONObject().apply {
                    put("serviceName", server.serviceName)
                    put("multiMode", true)
                }
                streamSettings.put("grpcSettings", grpcSettings)
            }
            "httpupgrade" -> {
                val httpUpgradeSettings = JSONObject().apply {
                    put("path", if (server.path.isNotBlank()) server.path else "/")
                    put("host", effectiveSni)
                }
                streamSettings.put("httpupgradeSettings", httpUpgradeSettings)
            }
            "xhttp" -> {
                val xhttpSettings = JSONObject().apply {
                    put("path", if (server.path.isNotBlank()) server.path else "/")
                    put("host", effectiveSni)
                }
                streamSettings.put("xhttpSettings", xhttpSettings)
            }
            "tcp" -> {
                if (server.headerType.equals("http", ignoreCase = true)) {
                    val tcpSettings = JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("type", "http")
                            put("request", JSONObject().apply {
                                put("version", "1.1")
                                put("method", "GET")
                                put("path", JSONArray().put("/"))
                                put("headers", JSONObject().apply {
                                    put("Host", JSONArray().put(effectiveSni))
                                })
                            })
                        })
                    }
                    streamSettings.put("tcpSettings", tcpSettings)
                }
            }
        }

        proxy.put("streamSettings", streamSettings)
        return proxy
    }

    private fun parseShadowsocksUser(raw: String): Pair<String, String> {
        val colonIdx = raw.indexOf(':')
        return if (colonIdx != -1) {
            Pair(raw.substring(0, colonIdx), raw.substring(colonIdx + 1))
        } else {
            Pair("aes-256-gcm", raw)
        }
    }
}
