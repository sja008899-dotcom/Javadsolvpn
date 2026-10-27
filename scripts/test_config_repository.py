import base64
import json
import unittest

import update_config_repository as worker


class ConfigRepositoryParserTests(unittest.TestCase):
    def test_extracts_supported_plain_links_and_deduplicates(self):
        text = "comment\nvless://id@example.com:443#one\nvless://id@example.com:443#one\ntrojan://pass@example.com:443#two\n"
        links = worker.extract_links(text)
        self.assertEqual(2, len(links))
        self.assertTrue(links[0].startswith("vless://"))

    def test_extracts_base64_subscription(self):
        raw = "vless://id@example.com:443#one\ntrojan://pass@example.com:443#two\n"
        encoded = base64.b64encode(raw.encode()).decode()
        self.assertEqual(2, len(worker.extract_links(encoded)))

    def test_vless_reality_generates_tls_and_transport(self):
        link = "vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?type=ws&security=reality&sni=cdn.example.com&pbk=publickey&sid=abcd&path=%2Fedge#test"
        outbound, endpoint = worker.outbound_from_link(link)
        self.assertEqual("example.com:443", endpoint)
        self.assertEqual("vless", outbound["type"])
        self.assertEqual("publickey", outbound["tls"]["reality"]["public_key"])
        self.assertEqual("ws", outbound["transport"]["type"])

    def test_vmess_json_uri(self):
        obj = {"add": "example.com", "port": "443", "id": "123e4567-e89b-12d3-a456-426614174000",
               "aid": "0", "net": "tcp", "tls": "tls", "scy": "auto"}
        payload = base64.b64encode(json.dumps(obj).encode()).decode()
        outbound, endpoint = worker.outbound_from_link("vmess://" + payload)
        self.assertEqual("example.com:443", endpoint)
        self.assertEqual("vmess", outbound["type"])
        self.assertTrue(outbound["tls"]["enabled"])

    def test_trojan_uri(self):
        outbound, _ = worker.outbound_from_link("trojan://secret@example.com:443?sni=example.com#test")
        self.assertEqual("trojan", outbound["type"])
        self.assertEqual("secret", outbound["password"])
        self.assertTrue(outbound["tls"]["enabled"])

    def test_shadowsocks_sip002_uri(self):
        credential = base64.b64encode(b"aes-128-gcm:secret").decode()
        outbound, endpoint = worker.outbound_from_link(f"ss://{credential}@example.com:8388#test")
        self.assertEqual("example.com:8388", endpoint)
        self.assertEqual("aes-128-gcm", outbound["method"])
        self.assertEqual("secret", outbound["password"])

    def test_rejects_unsupported_scheme(self):
        with self.assertRaises(ValueError):
            worker.outbound_from_link("wireguard://example.com:443")


if __name__ == "__main__":
    unittest.main()
