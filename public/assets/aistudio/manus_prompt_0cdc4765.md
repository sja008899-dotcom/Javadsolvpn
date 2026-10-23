# Prompt for Manus.ai

I have attached two ZIP files:
1. `javad-config-hub.zip` — a dependency-free Node.js (v20+) project that aggregates V2Ray/Xray share links (vless/vmess/trojan/ss) and VPN Gate OpenVPN profiles into ONE public JSON file, `docs/configs.json`.
2. `javad_vpn_minimal.zip` — the Flutter Android app "Javad vpn" that downloads that JSON and connects with `flutter_vless`.

Both projects are already written and unit-tested. DO NOT rewrite them from scratch and DO NOT change their architecture. Your job is to verify, deploy and publish. Work through the phases in order.

## Phase 1: verify the Hub
1. Unzip `javad-config-hub.zip`. Run `npm test` (all tests must pass).
2. Run `node scripts/aggregate.mjs` and read the log. `sources.json` lists public GitHub raw lists and Telegram channels; some may be dead. Replace dead or empty sources with working public ones (keep the file format), and re-run until at least 30 valid V2Ray configs and some OpenVPN profiles are produced. Never invent configs.
3. Check `docs/configs.json` strictly matches this schema:
   ```json
   {
     "updated_at": "2026-10-22T12:00:00Z",
     "v2ray":   [ { "name": "Javad 1", "link": "vless://...#Javad%201", "protocol": "vless" } ],
     "openvpn": [ { "name": "Javad VPN 1", "ovpn_base64": "...", "country": "JP" } ]
   }
   ```
   Rules: every V2Ray name is exactly `Javad 1`, `Javad 2`, ... in order; every OpenVPN name is exactly `Javad VPN 1`, `Javad VPN 2`, ...; OpenVPN is a separate array and is never converted to V2Ray.
4. If I give you a Cloudflare Worker VLESS link, pass it as the environment variable `WORKER_VLESS_LINK` when running the script. It must become `Javad 1`.

## Phase 2: publish with a stable public URL (most important)
The Android app needs a stable public HTTPS URL that returns the JSON and is refreshed every hour.
- **Preferred:** if you have GitHub access, create a PUBLIC repo `javad-config-hub`, push the project, then: (a) Settings → Actions → Workflow permissions = Read and write, (b) Settings → Pages → deploy from branch `main`, folder `/docs`, (c) add the optional secret `WORKER_VLESS_LINK`, (d) run the "Update configs" workflow once. The workflow `.github/workflows/update.yml` already runs hourly.
- **If GitHub is not available:** publish the `docs/` folder as a static website with Manus's publish feature, and set up a scheduled task that re-runs `node scripts/aggregate.mjs` every hour and republishes `docs/configs.json` at the same URL.
- Confirm by opening the final URL in a fresh request: it must return HTTP 200, `Content-Type` JSON, valid JSON, and the schema above. Also open the dashboard page (`docs/index.html`) and confirm it shows the counts and the first 10 names.

## Phase 3: build the APK (do this if your sandbox can run Flutter; otherwise skip and tell me)
1. Unzip `javad_vpn_minimal.zip`. Install the latest stable Flutter + Android SDK + JDK 17.
2. Run `flutter create --platforms=android .` WITHOUT overwriting `lib/main.dart`, `pubspec.yaml`, `android/app/build.gradle`, `android/app/src/main/AndroidManifest.xml`. Delete any generated `MainActivity` in another package (e.g. `kotlin/com/example/...`); keep ours at `kotlin/com/javadvpn/app/MainActivity.kt`.
3. Run `flutter pub get`, `flutter analyze`, `flutter test`. Fix only real compile/analyze errors, with minimal changes, and list every change you made. Note: `flutter_vless` latest is 1.1.6 (needs minSdk 23 and `useLegacyPackaging = true`, already set).
4. Build with the real Hub URL from Phase 2:
   `flutter build apk --release --dart-define=HUB_URL=<FINAL_JSON_URL>`
5. Deliver `app-release.apk`.

## Constraints
- Android app name: "Javad vpn" (project/package: `javad_vpn`, `com.javadvpn.app`). Hub name: "Javad Config Hub".
- Do not hard-code secrets or my Worker link in any public file.
- If something cannot be done in your environment (no GitHub access, no Flutter), say exactly what is missing and give me the exact manual steps instead of pretending it worked.

## Final report (keep it short)
1. The FINAL PUBLIC JSON URL (plus mirrors if any) and the dashboard URL.
2. Counts: V2Ray configs, OpenVPN profiles, and the `updated_at` value.
3. Which sources in `sources.json` you replaced and why.
4. The APK file (or the reason it was skipped) and any code changes.
5. A sample of the first 2 V2Ray entries with the links masked (show only the scheme, host and name).
