# مخزن کانفیگ‌ها

This directory is maintained by GitHub Actions, not by manual edits.

- `latest.json`: timestamp, count, and ranked records with protocol, measured RTT, and last successful outbound probe.
- `top20.txt`: the 20 highest-ranked verified share links, one per line.
- `all.txt`: all currently verified public share links, one per line.
- `sources.json`: public source feeds that the scheduled worker may read.

**Verification meaning:** an entry is published only after the pinned sing-box engine accepts its generated config and a real HTTPS request succeeds through its local SOCKS tunnel. TCP port reachability alone is not enough. A server can stop working immediately after a test; no public repo can guarantee future uptime.

Only public feed configurations are allowed here. Never add private/personal subscription URLs, credentials, or configs supplied privately by users. The worker never publishes a replacement set if a run cannot verify any candidates, preserving the last known-good snapshot and recording the failed run in the workflow log.
