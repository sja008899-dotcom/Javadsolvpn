# Javadsolvpn iOS build

This folder contains a reproducible build patch for the native iOS client and a GitHub Actions workflow that builds an unsigned IPA on a macOS runner.

- Engine and Packet Tunnel implementation: [SagerNet/sing-box-for-apple](https://github.com/SagerNet/sing-box-for-apple), GPL-3.0-or-later.
- Proxy engine framework: [SagerNet/sing-box](https://github.com/SagerNet/sing-box).
- `patch_upstream.py` sets the Javadsolvpn bundle prefix/display name and adds a public verified-config repository card to the iOS dashboard. The card copies up to 20 links to the clipboard, respecting a 110-link daily cap.
- The build uses source code, not a prebuilt third-party IPA. The engine and upstream source are checked out during CI and built with Xcode.
- The artifact is an **unsigned IPA**. It is not directly installable until signed with an Apple Developer account that has the Network Extension entitlement and matching App Group / provisioning profile. A personal free signing profile is not sufficient for distributing a VPN Packet Tunnel app.
- The iOS repository button copies ranked links; use the upstream client's profile import/paste UI to add them. Android imports them directly into its local 12-entry pool.

Build it from GitHub Actions: **Build iOS Javadsolvpn Client**. A successful compile does not mean Apple has approved the app or that a particular server works on a device.
