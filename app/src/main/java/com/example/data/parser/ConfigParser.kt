package com.example.data.parser

import com.example.data.local.ServerConfigEntity
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ConfigParser {

    private fun decodeBase64(input: String): String {
        val clean = input.trim().replace("\n", "").replace("\r", "")
        return try {
            val bytes = java.util.Base64.getDecoder().decode(clean)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            try {
                val bytes = java.util.Base64.getUrlDecoder().decode(clean)
                String(bytes, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    private data class ParsedUri(
        val userInfo: String,
        val host: String,
        val port: Int,
        val queryParams: Map<String, String>,
        val fragment: String
    )

    private fun parseUriManual(uriStr: String): ParsedUri? {
        val schemeEnd = uriStr.indexOf("://")
        if (schemeEnd == -1) return null
        var rest = uriStr.substring(schemeEnd + 3)

        // Fragment
        var fragment = ""
        val hashIdx = rest.indexOf('#')
        if (hashIdx != -1) {
            fragment = safeDecode(rest.substring(hashIdx + 1))
            rest = rest.substring(0, hashIdx)
        }

        // Query params
        val queryParams = mutableMapOf<String, String>()
        val queryIdx = rest.indexOf('?')
        if (queryIdx != -1) {
            val queryStr = rest.substring(queryIdx + 1)
            rest = rest.substring(0, queryIdx)
            val pairs = queryStr.split("&")
            for (p in pairs) {
                val kv = p.split("=", limit = 2)
                if (kv.isNotEmpty()) {
                    val key = kv[0]
                    val value = if (kv.size > 1) safeDecode(kv[1]) else ""
                    queryParams[key] = value
                }
            }
        }

        // UserInfo and Host:Port
        var userInfo = ""
        val atIdx = rest.lastIndexOf('@')
        if (atIdx != -1) {
            userInfo = rest.substring(0, atIdx)
            rest = rest.substring(atIdx + 1)
        }

        var host = rest
        var port = 443
        if (rest.contains(":")) {
            val parts = rest.split(":")
            host = parts[0]
            port = parts.getOrNull(1)?.toIntOrNull() ?: 443
        }

        if (host.isBlank()) return null
        return ParsedUri(userInfo, host, port, queryParams, fragment)
    }

    fun parsePayload(content: String, sourceUrl: String = "", startIndex: Int = 1): List<ServerConfigEntity> {
        val trimmed = content.trim()
        val result = mutableListOf<ServerConfigEntity>()

        // 1. Try JSON Hub schema
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                if (json.has("v2ray") || json.has("openvpn")) {
                    var currentIndex = startIndex
                    if (json.has("v2ray")) {
                        val v2rayArr = json.getJSONArray("v2ray")
                        for (i in 0 until v2rayArr.length()) {
                            val item = v2rayArr.getJSONObject(i)
                            val link = item.optString("link")
                            val parsed = parseSingleLink(link, sourceUrl, currentIndex)
                            if (parsed != null) {
                                result.add(parsed)
                                currentIndex++
                            }
                        }
                    }
                    if (json.has("openvpn")) {
                        val ovpnArr = json.getJSONArray("openvpn")
                        for (i in 0 until ovpnArr.length()) {
                            val item = ovpnArr.getJSONObject(i)
                            val ovpnB64 = item.optString("ovpn_base64")
                            val country = item.optString("country", "AUTO")
                            val parsed = parseOpenVpn(ovpnB64, country, sourceUrl, currentIndex)
                            if (parsed != null) {
                                result.add(parsed)
                                currentIndex++
                            }
                        }
                    }
                    if (result.isNotEmpty()) return result
                }
            } catch (_: Exception) {}
        }

        // 2. Try Base64 Subscription decoding
        var textToParse = trimmed
        if (!trimmed.contains("\n") && !trimmed.contains("://") && trimmed.length > 20) {
            val decoded = decodeBase64(trimmed)
            if (decoded.contains("://") || decoded.contains("\n")) {
                textToParse = decoded
            }
        }

        // 3. Line by line parsing
        val lines = textToParse.lines()
        var index = startIndex
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) continue
            val config = parseSingleLink(line, sourceUrl, index)
            if (config != null) {
                result.add(config)
                index++
            }
        }

        return result
    }

    fun parseSingleLink(link: String, sourceUrl: String = "", orderIndex: Int = 1): ServerConfigEntity? {
        val trimmed = link.trim()
        return try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed, sourceUrl, orderIndex)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed, sourceUrl, orderIndex)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed, sourceUrl, orderIndex)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed, sourceUrl, orderIndex)
                trimmed.startsWith("openvpn://", ignoreCase = true) || (trimmed.contains("client") && trimmed.contains("dev tun")) -> {
                    parseOpenVpn(trimmed, "AUTO", sourceUrl, orderIndex)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVless(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val parsed = parseUriManual(link) ?: return null
        val security = parsed.queryParams["security"] ?: "tls"
        val transport = parsed.queryParams["type"] ?: "tcp"
        val country = CountryDetector.detect("${parsed.host} ${parsed.fragment}")
        val friendlyName = "Javad $orderIndex • اتصال ${country.nameFa}"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "vless",
            host = parsed.host,
            port = parsed.port,
            rawLink = link,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            securityType = security,
            transportType = transport,
            sourceUrl = sourceUrl
        )
    }

    private fun parseVmess(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val b64 = link.substringAfter("vmess://").trim()
        val jsonStr = decodeBase64(b64)
        if (jsonStr.isBlank()) return null
        val json = JSONObject(jsonStr)
        val host = json.optString("add", "")
        if (host.isBlank()) return null
        val port = json.optInt("port", 443)
        val tls = json.optString("tls", "none")
        val net = json.optString("net", "tcp")
        val remark = json.optString("ps", "")
        val country = CountryDetector.detect("$host $remark")
        val friendlyName = "Javad $orderIndex • اتصال ${country.nameFa}"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "vmess",
            host = host,
            port = port,
            rawLink = link,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            securityType = if (tls == "tls") "tls" else "none",
            transportType = net,
            sourceUrl = sourceUrl
        )
    }

    private fun parseTrojan(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val parsed = parseUriManual(link) ?: return null
        val security = parsed.queryParams["security"] ?: "tls"
        val transport = parsed.queryParams["type"] ?: "tcp"
        val country = CountryDetector.detect("${parsed.host} ${parsed.fragment}")
        val friendlyName = "Javad $orderIndex • اتصال ${country.nameFa}"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "trojan",
            host = parsed.host,
            port = parsed.port,
            rawLink = link,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            securityType = security,
            transportType = transport,
            sourceUrl = sourceUrl
        )
    }

    private fun parseShadowsocks(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val raw = link.substringAfter("ss://").substringBefore("#")
        val fragment = if (link.contains("#")) safeDecode(link.substringAfter("#")) else ""
        var host = "127.0.0.1"
        var port = 8388

        if (raw.contains("@")) {
            val endpoint = raw.substringAfter("@")
            host = endpoint.substringBefore(":")
            port = endpoint.substringAfter(":").toIntOrNull() ?: 8388
        } else {
            val decoded = decodeBase64(raw)
            if (decoded.contains("@")) {
                val endpoint = decoded.substringAfter("@")
                host = endpoint.substringBefore(":")
                port = endpoint.substringAfter(":").toIntOrNull() ?: 8388
            }
        }

        val country = CountryDetector.detect("$host $fragment")
        val friendlyName = "Javad $orderIndex • اتصال ${country.nameFa}"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "ss",
            host = host,
            port = port,
            rawLink = link,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            securityType = "cipher",
            transportType = "tcp",
            sourceUrl = sourceUrl
        )
    }

    private fun parseOpenVpn(payload: String, countryHint: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val decoded = if (payload.startsWith("openvpn://")) payload else decodeBase64(payload).ifBlank { payload }

        var host = "openvpn.remote.net"
        var port = 1194

        for (line in decoded.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("remote ")) {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) host = parts[1]
                if (parts.size >= 3) port = parts[2].toIntOrNull() ?: 1194
                break
            }
        }

        val country = CountryDetector.detect(host, countryHint)
        val friendlyName = "Javad VPN $orderIndex • اتصال ${country.nameFa}"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "openvpn",
            host = host,
            port = port,
            rawLink = if (payload.startsWith("openvpn://")) payload else "openvpn://$host:$port#${country.nameFa}",
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            securityType = "tls",
            transportType = "udp",
            sourceUrl = sourceUrl
        )
    }

    private fun safeDecode(str: String): String {
        return try {
            URLDecoder.decode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
    }
}
