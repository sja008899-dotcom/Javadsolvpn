#!/usr/bin/env python3
"""Apply the Javadsolvpn config-repository card to the GPL-3.0 sing-box Apple client source."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent / "ios-client"
project = root / "sing-box.xcodeproj" / "project.pbxproj"
overview = root / "ApplicationLibrary" / "Views" / "Dashboard" / "Overview" / "OverviewView.swift"
if not project.exists() or not overview.exists():
    raise SystemExit("Expected sing-box-for-apple source tree was not checked out")

# Use app-specific bundle IDs in the generated unsigned build. Signing still needs the user's Apple account.
p = project.read_text(encoding="utf-8")
p = p.replace("BASE_PACKAGE_IDENTIFIER = io.nekohasekai.sfamt;", "BASE_PACKAGE_IDENTIFIER = com.javadsolvpn.ios;")
p = p.replace('INFOPLIST_KEY_CFBundleDisplayName = "sing-box";', 'INFOPLIST_KEY_CFBundleDisplayName = "Javadsolvpn";')
project.write_text(p, encoding="utf-8")

s = overview.read_text(encoding="utf-8")
if "javadsolvpnRepositoryCard" in s:
    print("Config repository card already applied")
    raise SystemExit(0)

import_anchor = "import SwiftUI"
if import_anchor not in s:
    raise SystemExit("Could not locate SwiftUI import in OverviewView")
s = s.replace(import_anchor, import_anchor + "\n#if os(iOS)\nimport UIKit\n#endif", 1)

state_anchor = "    @StateObject private var coordinator = OverviewViewModel()"
if state_anchor not in s:
    raise SystemExit("Could not locate OverviewView coordinator")
s = s.replace(
    state_anchor,
    state_anchor + """
    #if os(iOS)
    @State private var javadsolvpnRepositoryMessage: String?
    @State private var javadsolvpnRepositoryLoading = false
    #endif""",
    1
)

grid_anchor = """            }
        }
    }

    private func shouldShowCard"""
grid_replacement = """            }
            #if os(iOS)
            javadsolvpnRepositoryCard
            #endif
        }
    }

    #if os(iOS)
    private var javadsolvpnRepositoryCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Javadsolvpn — مخزن کانفیگ‌ها")
                .font(.headline)
            Text("دریافت و کپی کانفیگ‌های عمومی رتبه‌بندی‌شده؛ حداکثر ۱۱۰ کانفیگ در روز.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button {
                Task { await refreshJavadsolvpnRepository() }
            } label: {
                HStack {
                    if javadsolvpnRepositoryLoading {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.down.to.line")
                    }
                    Text(javadsolvpnRepositoryLoading ? "در حال دریافت…" : "دریافت ۲۰ کانفیگ برتر")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(javadsolvpnRepositoryLoading)
            if let message = javadsolvpnRepositoryMessage {
                Text(message).font(.footnote).textSelection(.enabled)
            }
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    @MainActor
    private func refreshJavadsolvpnRepository() async {
        let defaults = UserDefaults.standard
        let day = ISO8601DateFormatter().string(from: Calendar.current.startOfDay(for: Date()))
        let dayKey = "javadsolvpn.repository.day"
        let usedKey = "javadsolvpn.repository.used"
        let used = defaults.string(forKey: dayKey) == day ? defaults.integer(forKey: usedKey) : 0
        let remaining = max(0, 110 - used)
        guard remaining > 0 else {
            javadsolvpnRepositoryMessage = "سهمیهٔ روزانهٔ ۱۱۰ کانفیگ تمام شده است."
            return
        }
        javadsolvpnRepositoryLoading = true
        defer { javadsolvpnRepositoryLoading = false }
        do {
            let url = URL(string: "https://raw.githubusercontent.com/sja008899-dotcom/Javadsolvpn/main/config-repository/top20.txt")!
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                throw URLError(.badServerResponse)
            }
            let lines = String(decoding: data, as: UTF8.self)
                .split(whereSeparator: \\.isNewline)
                .map(String.init)
                .filter { $0.hasPrefix("vless://") || $0.hasPrefix("vmess://") || $0.hasPrefix("trojan://") || $0.hasPrefix("ss://") }
            let links = Array(lines.prefix(min(20, remaining)))
            guard !links.isEmpty else {
                javadsolvpnRepositoryMessage = "هنوز کانفیگ تأییدشده‌ای در مخزن منتشر نشده است."
                return
            }
            UIPasteboard.general.string = links.joined(separator: "\\n")
            defaults.set(day, forKey: dayKey)
            defaults.set(used + links.count, forKey: usedKey)
            javadsolvpnRepositoryMessage = "\\(links.count) کانفیگ در کلیپ‌بورد کپی شد. آن‌ها را از بخش Import/Paste وارد کنید. سهمیهٔ امروز: \\(used + links.count)/110."
        } catch {
            javadsolvpnRepositoryMessage = "دریافت از مخزن ناموفق بود: \\(error.localizedDescription)"
        }
    }
    #endif

    private func shouldShowCard"""
if grid_anchor not in s:
    raise SystemExit("Could not locate dashboard card grid insertion point")
s = s.replace(grid_anchor, grid_replacement, 1)
overview.write_text(s, encoding="utf-8")
print("Applied Javadsolvpn config repository card")
