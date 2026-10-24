package com.example.data.parser

import com.example.data.local.ServerConfigEntity
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ConfigParser {

    /**
     * Decodes Base64 or Base64URL string, automatically repairing missing padding '='.
     */
    fun decodeBase64(input: String): String {
        var clean = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (clean.isEmpty()) return ""
        clean = clean.replace('-', '+').replace('_', '/')
        val remainder = clean.length % 4
        if (remainder != 0) {
            clean += "=".repeat(4 - remainder)
        }
        return try {
            val bytes = java.util.Base64.getDecoder().decode(clean)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private data class ParsedUri(
        val userInfo: String,
        val host: String,
        val port: Int,
        val queryParams: Map<String, String>,
        val fragment: String
    )

    /**
     * Correctly separates host and port supporting IPv6 bracket notation [2001:db8::1]:443
     */
    private fun parseHostAndPort(rawEndpoint: String, defaultPort: Int = 443): Pair<String, Int> {
        val endpoint = rawEndpoint.trim()
        if (endpoint.startsWith("[")) {
            val closeBracket = endpoint.indexOf(']')
            if (closeBracket != -1) {
                val host = endpoint.substring(1, closeBracket)
                val portStr = endpoint.substring(closeBracket + 1).removePrefix(":")
                val port = portStr.toIntOrNull() ?: defaultPort
                return Pair(host, port)
            }
        }
        val colonIdx = endpoint.lastIndexOf(':')
        return if (colonIdx != -1) {
            val host = endpoint.substring(0, colonIdx)
            val port = endpoint.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
            Pair(host, port)
        } else {
            Pair(endpoint, defaultPort)
        }
    }

    private fun parseUriManual(uriStr: String): ParsedUri? {
        val schemeEnd = uriStr.indexOf("://")
        if (schemeEnd == -1) return null
        var rest = uriStr.substring(schemeEnd + 3)

        // Fragment (#tag)
        var fragment = ""
        val hashIdx = rest.indexOf('#')
        if (hashIdx != -1) {
            fragment = safeDecode(rest.substring(hashIdx + 1))
            rest = rest.substring(0, hashIdx)
        }

        // Query params (?key=val&...)
        val queryParams = mutableMapOf<String, String>()
        val queryIdx = rest.indexOf('?')
        if (queryIdx != -1) {
            val queryStr = rest.substring(queryIdx + 1)
            rest = rest.substring(0, queryIdx)
            val pairs = queryStr.split("&")
            for (p in pairs) {
                val kv = p.split("=", limit = 2)
                if (kv.isNotEmpty()) {
                    val key = kv[0].lowercase().trim()
                    val value = if (kv.size > 1) safeDecode(kv[1]) else ""
                    queryParams[key] = value
                }
            }
        }

        // UserInfo (@host)
        var userInfo = ""
        val atIdx = rest.lastIndexOf('@')
        if (atIdx != -1) {
            userInfo = safeDecode(rest.substring(0, atIdx))
            rest = rest.substring(atIdx + 1)
        }

        val (host, port) = parseHostAndPort(rest, 443)
        if (host.isBlank()) return null
        return ParsedUri(userInfo, host, port, queryParams, fragment)
    }

    fun parsePayload(content: String, sourceUrl: String = "", startIndex: Int = 1): List<ServerConfigEntity> {
        val trimmed = content.trim()
        val result = mutableListOf<ServerConfigEntity>()

        // 1. JSON schema (v2ray/v2rayNG/JSON export)
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                if (json.has("v2ray")) {
                    var currentIndex = startIndex
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
                    if (result.isNotEmpty()) return result
                }
            } catch (_: Exception) {
            }
        }

        // 2. Base64 subscription
        var textToParse = trimmed
        if (!trimmed.contains("\n") && !trimmed.contains("://") && trimmed.length > 20) {
            val decoded = decodeBase64(trimmed)
            if (decoded.contains("://") || decoded.contains("\n")) {
                textToParse = decoded
            }
        }

        // 3. Line-by-line parsing
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
                trimmed.startsWith("sstp://", ignoreCase = true) -> parseSstp(trimmed, sourceUrl, orderIndex)
                trimmed.startsWith("socks://", ignoreCase = true) -> parseSocks(trimmed, sourceUrl, orderIndex)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVless(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val parsed = parseUriManual(link) ?: return null
        val security = parsed.queryParams["security"] ?: "tls"
        val transport = parsed.queryParams["type"] ?: parsed.queryParams["net"] ?: "tcp"
        val flow = parsed.queryParams["flow"] ?: ""
        val sni = parsed.queryParams["sni"] ?: parsed.queryParams["peer"] ?: ""
        val pbk = parsed.queryParams["pbk"] ?: ""
        val sid = parsed.queryParams["sid"] ?: ""
        val fp = parsed.queryParams["fp"] ?: ""
        val path = parsed.queryParams["path"] ?: ""
        val serviceName = parsed.queryParams["servicename"] ?: parsed.queryParams["service_name"] ?: ""
        val alpn = parsed.queryParams["alpn"] ?: ""
        val headerType = parsed.queryParams["headertype"] ?: "none"

        val country = CountryDetector.detect("${parsed.host} ${parsed.fragment}")
        val label = if (parsed.fragment.isNotBlank()) parsed.fragment else "اتصال ${country.nameFa}"
        val friendlyName = "Javad $orderIndex • $label"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "vless",
            host = parsed.host,
            port = parsed.port,
            rawLink = link,
            uuidOrPassword = parsed.userInfo,
            flow = flow,
            securityType = security,
            transportType = transport,
            sni = sni,
            publicKey = pbk,
            shortId = sid,
            fingerprint = fp,
            path = path,
            serviceName = serviceName,
            alpn = alpn,
            headerType = headerType,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            sourceUrl = sourceUrl,
            isWorking = false,
            pingMs = -1L,
            lastTestedTime = 0L
        )
    }

    private fun parseVmess(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val b64 = link.substringAfter("vmess://").trim()
        val jsonStr = decodeBase64(b64)
        if (jsonStr.isBlank()) return null
        val json = JSONObject(jsonStr)
        val host = json.optString("add", "").trim()
        if (host.isBlank()) return null
        val port = json.optInt("port", 443)
        val tls = json.optString("tls", "none")
        val net = json.optString("net", "tcp")
        val uuid = json.optString("id", "")
        val aid = json.optInt("aid", 0)
        val path = json.optString("path", "")
        val sni = json.optString("sni", json.optString("host", ""))
        val remark = json.optString("ps", "")

        val country = CountryDetector.detect("$host $remark")
        val label = if (remark.isNotBlank()) remark else "اتصال ${country.nameFa}"
        val friendlyName = "Javad $orderIndex • $label"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "vmess",
            host = host,
            port = port,
            rawLink = link,
            uuidOrPassword = uuid,
            alterId = aid,
            securityType = if (tls.equals("tls", ignoreCase = true)) "tls" else "none",
            transportType = net,
            sni = sni,
            path = path,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            sourceUrl = sourceUrl,
            isWorking = false,
            pingMs = -1L,
            lastTestedTime = 0L
        )
    }

    private fun parseTrojan(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val parsed = parseUriManual(link) ?: return null
        val security = parsed.queryParams["security"] ?: "tls"
        val transport = parsed.queryParams["type"] ?: parsed.queryParams["net"] ?: "tcp"
        val sni = parsed.queryParams["sni"] ?: parsed.queryParams["peer"] ?: ""
        val alpn = parsed.queryParams["alpn"] ?: ""
        val path = parsed.queryParams["path"] ?: ""

        val country = CountryDetector.detect("${parsed.host} ${parsed.fragment}")
        val label = if (parsed.fragment.isNotBlank()) parsed.fragment else "اتصال ${country.nameFa}"
        val friendlyName = "Javad $orderIndex • $label"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "trojan",
            host = parsed.host,
            port = parsed.port,
            rawLink = link,
            uuidOrPassword = parsed.userInfo,
            securityType = security,
            transportType = transport,
            sni = sni,
            alpn = alpn,
            path = path,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            sourceUrl = sourceUrl,
            isWorking = false,
            pingMs = -1L,
            lastTestedTime = 0L
        )
    }

    private fun parseShadowsocks(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val raw = link.substringAfter("ss://").substringBefore("#").trim()
        val fragment = if (link.contains("#")) safeDecode(link.substringAfter("#")) else ""

        var host = ""
        var port = 8388
        var methodAndPass = ""

        if (raw.contains("@")) {
            val userPart = raw.substringBefore("@")
            val endpointPart = raw.substringAfter("@")
            val decodedUser = decodeBase64(userPart)
            methodAndPass = if (decodedUser.contains(":")) decodedUser else userPart
            val (h, p) = parseHostAndPort(endpointPart, 8388)
            host = h
            port = p
        } else {
            val decoded = decodeBase64(raw)
            if (decoded.contains("@")) {
                val userPart = decoded.substringBefore("@")
                val endpointPart = decoded.substringAfter("@")
                methodAndPass = userPart
                val (h, p) = parseHostAndPort(endpointPart, 8388)
                host = h
                port = p
            }
        }

        if (host.isBlank() || host == "127.0.0.1" || host == "localhost") {
            return null
        }

        val country = CountryDetector.detect("$host $fragment")
        val label = if (fragment.isNotBlank()) fragment else "اتصال ${country.nameFa}"
        val friendlyName = "Javad $orderIndex • $label"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "ss",
            host = host,
            port = port,
            rawLink = link,
            uuidOrPassword = methodAndPass,
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            securityType = "cipher",
            transportType = "tcp",
            sourceUrl = sourceUrl,
            isWorking = false,
            pingMs = -1L,
            lastTestedTime = 0L
        )
    }

    private fun parseSstp(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val parsed = parseUriManual(link) ?: return null
        val country = CountryDetector.detect("${parsed.host} ${parsed.fragment}")
        val label = if (parsed.fragment.isNotBlank()) parsed.fragment else "اتصال ${country.nameFa}"
        val friendlyName = "Javad $orderIndex • $label"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "sstp",
            host = parsed.host,
            port = parsed.port,
            rawLink = link,
            uuidOrPassword = parsed.userInfo,
            securityType = parsed.queryParams["security"] ?: "tls",
            transportType = parsed.queryParams["type"] ?: parsed.queryParams["net"] ?: "tcp",
            sni = parsed.queryParams["sni"] ?: parsed.queryParams["peer"] ?: "",
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            sourceUrl = sourceUrl,
            isWorking = false,
            pingMs = -1L,
            lastTestedTime = 0L
        )
    }

    private fun parseSocks(link: String, sourceUrl: String, orderIndex: Int): ServerConfigEntity? {
        val parsed = parseUriManual(link) ?: return null
        val country = CountryDetector.detect("${parsed.host} ${parsed.fragment}")
        val label = if (parsed.fragment.isNotBlank()) parsed.fragment else "اتصال ${country.nameFa}"
        val friendlyName = "Javad $orderIndex • $label"

        return ServerConfigEntity(
            name = friendlyName,
            protocol = "socks",
            host = parsed.host,
            port = parsed.port,
            rawLink = link,
            uuidOrPassword = parsed.userInfo,
            securityType = "none",
            transportType = "tcp",
            countryCode = country.code,
            countryNameFa = country.nameFa,
            countryNameEn = country.nameEn,
            sourceUrl = sourceUrl,
            isWorking = false,
            pingMs = -1L,
            lastTestedTime = 0L
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
