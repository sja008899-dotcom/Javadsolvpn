#!/usr/bin/env python3
"""Fetch public share links, validate them with sing-box, and publish only working HTTPS egress."""
from __future__ import annotations
import base64, concurrent.futures, json, os, re, shutil, socket, subprocess, tempfile, time, urllib.parse, urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "config-repository"
SOURCES_FILE = STORE / "sources.json"
MAX_SOURCE_BYTES = 12 * 1024 * 1024
WORKERS = 12
CANDIDATE_TIMEOUT = 22
TEST_URLS = ("https://www.gstatic.com/generate_204", "https://cp.cloudflare.com/generate_204")
SCHEMES = ("vless://", "vmess://", "trojan://", "ss://")

def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")

def decode_b64(value: str) -> str:
    value = value.strip().replace("-", "+").replace("_", "/")
    value += "=" * ((4 - len(value) % 4) % 4)
    return base64.b64decode(value).decode("utf-8", errors="strict")

def extract_links(text: str) -> list[str]:
    # Handles plain links and base64 subscription bodies; only supported URI schemes are retained.
    chunks = [text]
    try:
        decoded = decode_b64(text)
        if "://" in decoded:
            chunks.append(decoded)
    except Exception:
        pass
    links = []
    for chunk in chunks:
        for line in chunk.splitlines():
            line = line.strip().strip("\ufeff")
            if line.startswith(SCHEMES):
                links.append(line)
    return list(dict.fromkeys(links))

def get_text(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "Javadsolvpn-ConfigWorker/1.0"})
    with urllib.request.urlopen(req, timeout=20) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status}")
        data = response.read(MAX_SOURCE_BYTES + 1)
        if len(data) > MAX_SOURCE_BYTES:
            raise RuntimeError("source feed exceeds 12 MiB safety limit")
        return data.decode("utf-8", errors="replace")

def query_map(query: str) -> dict[str, str]:
    parsed = urllib.parse.parse_qs(query, keep_blank_values=True)
    return {k: v[-1] for k, v in parsed.items() if v}

def common_config(outbound: dict, port: int) -> dict:
    return {
        "log": {"level": "error"},
        "dns": {"servers": [{"tag": "system", "address": "local"}], "final": "system"},
        "inbounds": [{"type": "socks", "tag": "local-socks", "listen": "127.0.0.1", "listen_port": port}],
        "outbounds": [outbound, {"type": "direct", "tag": "direct"}, {"type": "block", "tag": "block"}],
        "route": {"final": outbound["tag"], "auto_detect_interface": True}
    }

def tls_options(q: dict[str, str], host: str) -> dict:
    security = q.get("security", "tls").lower()
    tls = {"enabled": security not in ("", "none", "false"), "server_name": q.get("sni") or q.get("servername") or host}
    if tls["enabled"]:
        fp = q.get("fp", "")
        if fp:
            tls["utls"] = {"enabled": True, "fingerprint": fp}
        if q.get("allowinsecure", "0") == "1":
            tls["insecure"] = True
        if security == "reality":
            public_key = q.get("pbk") or q.get("publickey")
            if not public_key:
                raise ValueError("Reality link has no pbk/publicKey")
            tls["reality"] = {"enabled": True, "public_key": public_key, "short_id": q.get("sid", "")}
    return tls

def transport_options(network: str, q: dict[str, str], host: str) -> dict | None:
    network = (network or "tcp").lower()
    if network in ("tcp", "raw", "http"):
        if network == "http":
            headers = {}
            if q.get("host"):
                headers["Host"] = q["host"]
            return {"type": "http", "host": [q["host"]] if q.get("host") else [host], "path": q.get("path", "/")}
        return None
    if network == "ws":
        headers = {"Host": q["host"]} if q.get("host") else {}
        result = {"type": "ws", "path": q.get("path", "/")}
        if headers:
            result["headers"] = headers
        return result
    if network in ("grpc", "gun"):
        return {"type": "grpc", "service_name": q.get("servicename") or q.get("serviceName", "")}
    if network == "h2":
        return {"type": "http", "host": [q.get("host", host)], "path": q.get("path", "/")}
    raise ValueError(f"unsupported transport: {network}")

def outbound_from_link(link: str) -> tuple[dict, str]:
    scheme = link.split("://", 1)[0].lower()
    if scheme == "vmess":
        payload = decode_b64(link.split("://", 1)[1].split("#", 1)[0])
        obj = json.loads(payload)
        host, port = obj.get("add"), int(obj.get("port", 0))
        if not host or not (1 <= port <= 65535) or not obj.get("id"):
            raise ValueError("invalid VMess host/port/UUID")
        network = obj.get("net", "tcp").lower()
        q = {"security": "tls" if obj.get("tls") else "none",
             "sni": obj.get("sni") or obj.get("host") or host,
             "host": obj.get("host", ""), "path": obj.get("path", "/"),
             "servicename": obj.get("path", "")}
        outbound = {"type": "vmess", "tag": "proxy", "server": host, "server_port": port,
                    "uuid": obj["id"], "security": obj.get("scy") or "auto",
                    "alter_id": int(obj.get("aid", 0))}
        if q["security"] != "none":
            outbound["tls"] = tls_options(q, host)
        transport = transport_options(network, q, host)
        if transport:
            outbound["transport"] = transport
        return outbound, f"{host}:{port}"

    parsed = urllib.parse.urlsplit(link)
    host, port = parsed.hostname, parsed.port
    if not host or not port:
        raise ValueError("missing host or port")
    q = query_map(parsed.query)
    name = urllib.parse.unquote(parsed.fragment) or f"{scheme}-{host}"
    if scheme == "vless":
        if not parsed.username:
            raise ValueError("missing VLESS UUID")
        outbound = {"type": "vless", "tag": "proxy", "server": host, "server_port": port,
                    "uuid": urllib.parse.unquote(parsed.username)}
        if q.get("flow"):
            outbound["flow"] = q["flow"]
        tls = tls_options(q, host)
        if tls["enabled"]:
            outbound["tls"] = tls
        transport = transport_options(q.get("type", "tcp"), q, host)
        if transport:
            outbound["transport"] = transport
    elif scheme == "trojan":
        password = urllib.parse.unquote(parsed.username or "")
        if not password:
            raise ValueError("missing Trojan password")
        outbound = {"type": "trojan", "tag": "proxy", "server": host, "server_port": port, "password": password}
        outbound["tls"] = tls_options(q, host)
        transport = transport_options(q.get("type", "tcp"), q, host)
        if transport:
            outbound["transport"] = transport
    elif scheme == "ss":
        # SIP002 URI: ss://base64(method:password)@host:port or fully-base64 legacy URI.
        authority = link.split("://", 1)[1].split("#", 1)[0].split("?", 1)[0]
        if "@" not in authority:
            decoded = decode_b64(authority)
            parsed_legacy = urllib.parse.urlsplit("ss://" + decoded)
            host, port = parsed_legacy.hostname, parsed_legacy.port
            userinfo = parsed_legacy.username or ""
            if parsed_legacy.password:
                userinfo += ":" + parsed_legacy.password
        else:
            userinfo = urllib.parse.unquote(parsed.netloc.rsplit("@", 1)[0])
            if ":" not in userinfo:
                userinfo = decode_b64(userinfo)
        if not host or not port or ":" not in userinfo:
            raise ValueError("invalid Shadowsocks URI")
        method, password = userinfo.split(":", 1)
        outbound = {"type": "shadowsocks", "tag": "proxy", "server": host, "server_port": port,
                    "method": method, "password": password}
    else:
        raise ValueError("unsupported scheme")
    return outbound, f"{host}:{port}"

def allocate_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]

def verify_one(link: str, sing_box: str) -> dict | None:
    started = time.monotonic()
    proc = None
    try:
        outbound, endpoint = outbound_from_link(link)
        port = allocate_port()
        config = common_config(outbound, port)
        with tempfile.TemporaryDirectory(prefix="javadsolvpn-") as temp:
            config_path = Path(temp) / "config.json"
            config_path.write_text(json.dumps(config), encoding="utf-8")
            check = subprocess.run([sing_box, "check", "-c", str(config_path)],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=8)
            if check.returncode:
                return None
            proc = subprocess.Popen([sing_box, "run", "-c", str(config_path)],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            deadline = time.monotonic() + 8
            success_ms = None
            for test_url in TEST_URLS:
                while time.monotonic() < deadline and proc.poll() is None:
                    probe_started = time.monotonic()
                    result = subprocess.run(
                        ["curl", "--silent", "--show-error", "--max-time", "5",
                         "--socks5-hostname", f"127.0.0.1:{port}", "-o", "/dev/null",
                         "-w", "%{http_code}", test_url],
                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, timeout=7)
                    if result.returncode == 0 and result.stdout.strip() in ("200", "204"):
                        success_ms = int((time.monotonic() - probe_started) * 1000)
                        break
                    time.sleep(0.4)
                if success_ms is not None:
                    break
            if success_ms is None:
                return None
        return {"link": link, "endpoint": endpoint, "rtt_ms": success_ms,
                "verified_at": utc_now(), "verification": "sing-box+HTTPS-through-SOCKS"}
    except Exception:
        return None
    finally:
        if proc is not None:
            try:
                proc.terminate()
                proc.wait(timeout=2)
            except Exception:
                try: proc.kill()
                except Exception: pass

def main() -> int:
    STORE.mkdir(parents=True, exist_ok=True)
    sources = json.loads(SOURCES_FILE.read_text(encoding="utf-8")).get("sources", [])
    candidates: list[str] = []
    source_errors = []
    for source in sources:
        try:
            candidates.extend(extract_links(get_text(source["url"])))
        except Exception as exc:
            source_errors.append({"source": source.get("name", source.get("url", "unknown")), "error": str(exc)[:160]})
    # Re-test previous verified entries too, so old links are removed when their tunnel stops working.
    previous = STORE / "all.txt"
    if previous.exists():
        candidates.extend(extract_links(previous.read_text(encoding="utf-8", errors="replace")))
    candidates = list(dict.fromkeys(candidates))
    test_limit = int(os.environ.get("MAX_CANDIDATES", "0"))
    if test_limit > 0:
        candidates = candidates[:test_limit]
        print(f"Smoke-test mode: checking at most {test_limit} candidates.")
    if not candidates:
        latest_path = STORE / "latest.json"
        if latest_path.exists():
            try:
                previous_meta = json.loads(latest_path.read_text(encoding="utf-8"))
            except Exception:
                previous_meta = {"schema_version": 1, "generated_at": None, "count": 0, "configs": []}
        else:
            previous_meta = {"schema_version": 1, "generated_at": None, "count": 0, "configs": []}
        previous_meta.update({
            "status": "stale-no-currently-verified-configs" if previous_meta.get("count", 0) else "no-verified-configs",
            "last_check_at": utc_now(),
            "last_check_status": "no-candidates",
            "source_errors": source_errors
        })
        latest_path.write_text(json.dumps(previous_meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (STORE / "last-run.json").write_text(json.dumps({
            "checked_at": utc_now(), "status": "no-candidates", "candidate_count": 0,
            "source_errors": source_errors
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"status": "no_candidates", "source_errors": source_errors}))
        return 1 if test_limit > 0 else 0
    sing_box = os.environ.get("SING_BOX_BIN", "sing-box")
    valid = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = {pool.submit(verify_one, link, sing_box): link for link in candidates}
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            if result:
                valid.append(result)
    if not valid:
        # Preserve last known-good files; never replace a good snapshot with an empty list.
        (STORE / "last-run.json").write_text(json.dumps({
            "checked_at": utc_now(), "status": "no-working-candidates",
            "candidate_count": len(candidates), "source_errors": source_errors
        }, indent=2) + "\n", encoding="utf-8")
        print("No candidate passed the end-to-end HTTPS tunnel test; preserving previous snapshot.")
        return 1
    valid.sort(key=lambda x: (x["rtt_ms"], x["endpoint"]))
    for rank, item in enumerate(valid, 1):
        item["rank"] = rank
    generated = utc_now()
    metadata = {
        "schema_version": 1, "status": "verified", "generated_at": generated, "last_check_at": generated, "last_check_status": "success", "checked_candidates": len(candidates),
        "count": len(valid), "verification": "sing-box config validation + HTTPS request through local SOCKS tunnel",
        "source_errors": source_errors, "configs": valid
    }
    (STORE / "latest.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    links = [item["link"] for item in valid]
    (STORE / "all.txt").write_text("\n".join(links) + "\n", encoding="utf-8")
    (STORE / "top20.txt").write_text("\n".join(links[:20]) + "\n", encoding="utf-8")
    table = [
        "# Top 20 configs (verified with real HTTPS tunnel traffic)",
        "",
        "| Rank | Protocol | Endpoint | Probe time (ms) |",
        "|---:|---|---|---:|"
    ]
    for item in valid[:20]:
        protocol = item["link"].split("://", 1)[0].upper()
        table.append(f'| {item["rank"]} | {protocol} | {item["endpoint"]} | {item["rtt_ms"]} |')
    table.extend(["", "Raw links for copying are in top20.txt. A successful probe does not guarantee future uptime."])
    (STORE / "top20.md").write_text("\n".join(table) + "\n", encoding="utf-8")
    (STORE / "last-run.json").write_text(json.dumps({
        "checked_at": generated, "status": "success", "candidate_count": len(candidates),
        "verified_count": len(valid), "source_errors": source_errors
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Published {len(valid)} end-to-end verified configs from {len(candidates)} candidates.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
