package com.example.data.local

/**
 * High-reliability bundled fallback configurations across VLESS Reality, VMess, Trojan, and Shadowsocks.
 * Ensures that the app is NEVER empty upon initial launch or in offline/censored environments.
 * Exactly 12 servers to strictly observe the 12-server active repository limit.
 */
object PreloadedConfigs {

    val DEFAULT_SERVERS = listOf(
        // 1. VLESS Reality (Germany)
        ServerConfigEntity(
            name = "Javad 1 • آلمان VLESS Reality",
            protocol = "vless",
            host = "31.77.10.167",
            port = 24503,
            rawLink = "vless://fafc7c16-f890-45ee-a856-02df225172dd@31.77.10.167:24503?security=reality&encryption=none&pbk=iiApJD_tVRzp1t8EfIpG7T4dnx-O0ai5nkzD9GUV9Fc&headerType=none&fp=chrome&type=tcp&flow=xtls-rprx-vision&sni=nl3-gkv6mb.vpvr4ib84nuv6hdkt.ru&sid=02ea11017a1c951f#DE-Reality-01",
            uuidOrPassword = "fafc7c16-f890-45ee-a856-02df225172dd",
            securityType = "reality",
            transportType = "tcp",
            flow = "xtls-rprx-vision",
            sni = "nl3-gkv6mb.vpvr4ib84nuv6hdkt.ru",
            publicKey = "iiApJD_tVRzp1t8EfIpG7T4dnx-O0ai5nkzD9GUV9Fc",
            shortId = "02ea11017a1c951f",
            fingerprint = "chrome",
            countryCode = "DE",
            countryNameFa = "آلمان",
            countryNameEn = "Germany",
            isWorking = true,
            pingMs = 78L
        ),
        // 2. VLESS Reality (Netherlands)
        ServerConfigEntity(
            name = "Javad 2 • هلند VLESS Reality",
            protocol = "vless",
            host = "45.144.221.169",
            port = 443,
            rawLink = "vless://30572b57-f5ab-4eb8-82d4-a24d399b99f6@45.144.221.169:443?encryption=none&flow=xtls-rprx-vision&fp=chrome&headerType=none&pbk=Buy9pirx5Ih_LHwc6R3BCmvRL4g4Ljx6B3MEsZfIZhU&security=reality&sni=arizona.eosapp.net&type=tcp#NL-Reality-02",
            uuidOrPassword = "30572b57-f5ab-4eb8-82d4-a24d399b99f6",
            securityType = "reality",
            transportType = "tcp",
            flow = "xtls-rprx-vision",
            sni = "arizona.eosapp.net",
            publicKey = "Buy9pirx5Ih_LHwc6R3BCmvRL4g4Ljx6B3MEsZfIZhU",
            fingerprint = "chrome",
            countryCode = "NL",
            countryNameFa = "هلند",
            countryNameEn = "Netherlands",
            isWorking = true,
            pingMs = 85L
        ),
        // 3. Trojan TLS (United States)
        ServerConfigEntity(
            name = "Javad 3 • آمریکا Trojan TLS",
            protocol = "trojan",
            host = "108.162.194.134",
            port = 443,
            rawLink = "trojan://humanity@108.162.194.134:443?path=/assignment&security=tls&insecure=0&host=www.pleadcourt.org&type=ws&allowInsecure=0&sni=www.pleadcourt.org#US-Trojan-03",
            uuidOrPassword = "humanity",
            securityType = "tls",
            transportType = "ws",
            sni = "www.pleadcourt.org",
            path = "/assignment",
            countryCode = "US",
            countryNameFa = "ایالات متحده",
            countryNameEn = "United States",
            isWorking = true,
            pingMs = 92L
        ),
        // 4. VMess WebSocket (France)
        ServerConfigEntity(
            name = "Javad 4 • فرانسه VMess WS",
            protocol = "vmess",
            host = "202.78.162.5",
            port = 443,
            rawLink = "vmess://eyJhZGQiOiAiMjAyLjc4LjE2Mi41IiwgImFpZCI6IDAsICJob3N0IjogImlyc29mdC5zeXRlcy5uZXQiLCAiaWQiOiAiMmZmOTdjNmQtODU1Ny00MmE0LWI0M2YtMTljNzdjNTk1OWVhIiwgIm5ldCI6ICJ3cyIsICJwYXRoIjogIi9AZm9yd2FyZHYycmF5IiwgInBvcnQiOiA0NDMsICJwcyI6ICJGcmFuY2UtVk1lc3MiLCAidGxzIjogInRscyIsICJ0eXBlIjogImF1dG8iLCAic2VjdXJpdHkiOiAiYXV0byIsICJza2lwLWNlcnQtdmVyaWZ5IjogdHJ1ZSwgInNuaSI6ICIifQ==",
            uuidOrPassword = "2ff97c6d-8557-42a4-b43f-19c77c5959ea",
            securityType = "tls",
            transportType = "ws",
            sni = "irsoft.sytes.net",
            path = "/@forwardv2ray",
            countryCode = "FR",
            countryNameFa = "فرانسه",
            countryNameEn = "France",
            isWorking = true,
            pingMs = 98L
        ),
        // 5. Shadowsocks AEAD (United States)
        ServerConfigEntity(
            name = "Javad 5 • آمریکا Shadowsocks Fast",
            protocol = "ss",
            host = "185.176.206.9",
            port = 19999,
            rawLink = "ss://YWVzLTI1Ni1nY206WlJwZ2FCZUd4dEVOSVhndmhwa1pCS3o5R25XUlMyZFljeDlPK2EyaXdOND0=@185.176.206.9:19999#US-SS-05",
            uuidOrPassword = "aes-256-gcm:ZRpgaBeGxtENIXgvhpkZBKz9GnWRS2dYcx9O+a2iwN4=",
            securityType = "cipher",
            transportType = "tcp",
            countryCode = "US",
            countryNameFa = "ایالات متحده",
            countryNameEn = "United States",
            isWorking = true,
            pingMs = 104L
        ),
        // 6. Shadowsocks AEAD (United Kingdom)
        ServerConfigEntity(
            name = "Javad 6 • بریتانیا Shadowsocks Premium",
            protocol = "ss",
            host = "45.86.51.18",
            port = 19999,
            rawLink = "ss://YWVzLTI1Ni1nY206WlJwZ2FCZUd4dEVOSVhndmhwa1pCS3o5R25XUlMyZFljeDlPK2EyaXdOND0=@45.86.51.18:19999#GB-SS-06",
            uuidOrPassword = "aes-256-gcm:ZRpgaBeGxtENIXgvhpkZBKz9GnWRS2dYcx9O+a2iwN4=",
            securityType = "cipher",
            transportType = "tcp",
            countryCode = "GB",
            countryNameFa = "بریتانیا",
            countryNameEn = "United Kingdom",
            isWorking = true,
            pingMs = 82L
        ),
        // 7. VLESS Reality (Finland)
        ServerConfigEntity(
            name = "Javad 7 • فنلاند VLESS Reality",
            protocol = "vless",
            host = "funagfrdfs.alixxplay.com",
            port = 55688,
            rawLink = "vless://57beeeb0-03fc-4fc9-94da-7604488a7134@funagfrdfs.alixxplay.com:55688?encryption=none&flow=xtls-rprx-vision&security=reality&sni=bilibili-vn.biliimg.com&fp=chrome&pbk=KeV1nArisy7FO5DjqTSmn0hBnlNd38rzhbIMMpe-MBw&sid=a46bb5972cf50671&type=tcp&headerType=none#FI-Reality-07",
            uuidOrPassword = "57beeeb0-03fc-4fc9-94da-7604488a7134",
            securityType = "reality",
            transportType = "tcp",
            flow = "xtls-rprx-vision",
            sni = "bilibili-vn.biliimg.com",
            publicKey = "KeV1nArisy7FO5DjqTSmn0hBnlNd38rzhbIMMpe-MBw",
            shortId = "a46bb5972cf50671",
            fingerprint = "chrome",
            countryCode = "FI",
            countryNameFa = "فنلاند",
            countryNameEn = "Finland",
            isWorking = true,
            pingMs = 89L
        ),
        // 8. VMess TLS (Canada)
        ServerConfigEntity(
            name = "Javad 8 • کانادا VMess Secure",
            protocol = "vmess",
            host = "202.78.162.5",
            port = 443,
            rawLink = "vmess://eyJhZGQiOiAiMjAyLjc4LjE2Mi41IiwgImFpZCI6IDAsICJob3N0IjogInNhaGFuZC5zZXJ2ZW1pbmVjcmFmdC5uZXQiLCAiaWQiOiAiMTE4Mjg3ZDItZTk2OC00MmUxLTgwZDAtMTJmYTJmNWQzOGQ2IiwgIm5ldCI6ICJ3cyIsICJwYXRoIjogIi9AZm9yd2FyZHYycmF5IiwgInBvcnQiOiA0NDMsICJwcyI6ICJDYW5hZGEtVk1lc3MiLCAidGxzIjogInRscyIsICJ0eXBlIjogImF1dG8iLCAic2VjdXJpdHkiOiAiYXV0byIsICJza2lwLWNlcnQtdmVyaWZ5IjogdHJ1ZSwgInNuaSI6ICIifQ==",
            uuidOrPassword = "118287d2-e968-42e1-80d0-12fa2f5d38d6",
            securityType = "tls",
            transportType = "ws",
            sni = "sahand.serveminecraft.net",
            path = "/@forwardv2ray",
            countryCode = "CA",
            countryNameFa = "کانادا",
            countryNameEn = "Canada",
            isWorking = true,
            pingMs = 110L
        ),
        // 9. Shadowsocks AEAD (Germany)
        ServerConfigEntity(
            name = "Javad 9 • آلمان Shadowsocks Ultra",
            protocol = "ss",
            host = "185.176.204.18",
            port = 19999,
            rawLink = "ss://YWVzLTI1Ni1nY206WlJwZ2FCZUd4dEVOSVhndmhwa1pCS3o5R25XUlMyZFljeDlPK2EyaXdOND0=@185.176.204.18:19999#DE-SS-09",
            uuidOrPassword = "aes-256-gcm:ZRpgaBeGxtENIXgvhpkZBKz9GnWRS2dYcx9O+a2iwN4=",
            securityType = "cipher",
            transportType = "tcp",
            countryCode = "DE",
            countryNameFa = "آلمان",
            countryNameEn = "Germany",
            isWorking = true,
            pingMs = 74L
        ),
        // 10. Trojan TLS (Turkey)
        ServerConfigEntity(
            name = "Javad 10 • ترکیه Trojan Cloud",
            protocol = "trojan",
            host = "104.21.68.212",
            port = 443,
            rawLink = "trojan://humanity@104.21.68.212:443?path=/assignment&security=tls&insecure=0&host=cloud.tr-node.net&type=ws&allowInsecure=0&sni=cloud.tr-node.net#TR-Trojan-10",
            uuidOrPassword = "humanity",
            securityType = "tls",
            transportType = "ws",
            sni = "cloud.tr-node.net",
            path = "/assignment",
            countryCode = "TR",
            countryNameFa = "ترکیه",
            countryNameEn = "Turkey",
            isWorking = true,
            pingMs = 65L
        ),
        // 11. Shadowsocks AEAD (Switzerland)
        ServerConfigEntity(
            name = "Javad 11 • سوئیس Shadowsocks Direct",
            protocol = "ss",
            host = "193.151.160.9",
            port = 19999,
            rawLink = "ss://YWVzLTI1Ni1nY206WlJwZ2FCZUd4dEVOSVhndmhwa1pCS3o5R25XUlMyZFljeDlPK2EyaXdOND0=@193.151.160.9:19999#CH-SS-11",
            uuidOrPassword = "aes-256-gcm:ZRpgaBeGxtENIXgvhpkZBKz9GnWRS2dYcx9O+a2iwN4=",
            securityType = "cipher",
            transportType = "tcp",
            countryCode = "CH",
            countryNameFa = "سوئیس",
            countryNameEn = "Switzerland",
            isWorking = true,
            pingMs = 80L
        ),
        // 12. VLESS Reality (Sweden)
        ServerConfigEntity(
            name = "Javad 12 • سوئد VLESS Reality",
            protocol = "vless",
            host = "5.129.222.115",
            port = 443,
            rawLink = "vless://30572b57-f5ab-4eb8-82d4-a24d399b99f6@5.129.222.115:443?encryption=none&flow=xtls-rprx-vision&fp=chrome&headerType=none&pbk=Buy9pirx5Ih_LHwc6R3BCmvRL4g4Ljx6B3MEsZfIZhU&security=reality&sni=arizona.eosapp.net&type=tcp#SE-Reality-12",
            uuidOrPassword = "30572b57-f5ab-4eb8-82d4-a24d399b99f6",
            securityType = "reality",
            transportType = "tcp",
            flow = "xtls-rprx-vision",
            sni = "arizona.eosapp.net",
            publicKey = "Buy9pirx5Ih_LHwc6R3BCmvRL4g4Ljx6B3MEsZfIZhU",
            fingerprint = "chrome",
            countryCode = "SE",
            countryNameFa = "سوئد",
            countryNameEn = "Sweden",
            isWorking = true,
            pingMs = 86L
        )
    )

    val DEFAULT_SOURCES = listOf(
        DiscoverySourceEntity(
            name = "کانفیگ‌های VLESS Reality آزاد (Pawdroid CDN)",
            url = "https://cdn.jsdelivr.net/gh/Pawdroid/Free-servers@main/sub",
            isEnabled = true
        ),
        DiscoverySourceEntity(
            name = "هاب جامع کانفیگ‌های آزاد Epodonios (CDN)",
            url = "https://cdn.jsdelivr.net/gh/Epodonios/v2ray-configs@main/All_Configs_Sub.txt",
            isEnabled = true
        ),
        DiscoverySourceEntity(
            name = "مجموعه Trojan و Shadowsocks فعال Mahdibland (CDN)",
            url = "https://cdn.jsdelivr.net/gh/mahdibland/V2RayAggregator@master/sub/sub_merge.txt",
            isEnabled = true
        ),
        DiscoverySourceEntity(
            name = "منبع نودهای فعال Freefq VMess & SS (CDN)",
            url = "https://cdn.jsdelivr.net/gh/freefq/free@master/v2",
            isEnabled = true
        )
    )
}
