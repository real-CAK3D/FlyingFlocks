# UX Reference — Biscuit Manager & WDG Wars (observed on device)

Captured 2026-10-30 from the two live apps on the user's Samsung (RFGL71XGAGW).
Screenshots: `G:\MyPetID-work\biscuit-explore\`. This is the design reference for
mirroring look/feel and for the WDG integration. Nothing here was changed on the
server side; it is observation only.

## The user's real hardware (for testing our app)
Known boards saved in Biscuit Manager → Connection sheet (both Seeed XIAO ESP32-C5):
- **Xiao Biscuit** — `10:BD:A3:CC:11:7A`
- **CAK3D Biscuit** — `10:BD:A3:CA:30:82`

Both were "Out of range" during the tour. Our app should discover these same
boards (advertising name `Biscuit`, service `4fafc201-…`).

---

## 1. Biscuit Manager (`com.hancockventuresllc.biscuitmanager`)

### Visual language
- Pure-dark background (`~#0E1116`), rounded cards on a slightly lighter surface.
- **Monospace** body/label type throughout; gold/amber accent for headings + active state.
- Standard row pattern everywhere: **circular colored icon + bold title + grey subtitle + chevron**.
- Colour coding: blue = scan/benign, **red = attack/offensive**, amber = neutral tools, green = BT scan/go.
- Locked/gated rows render greyed with a **lock icon** and a reason subtitle.

### Information architecture
Bottom nav, 5 tabs: **Dashboard · Wardrive · WiFi · Bluetooth · Misc**.
Dashboard top bar has 3 icons: **Terminal · Settings · Connection**.

### Dashboard (`bm-01.png`)
- Top connection pill with state dot (Connecting/Connected/Offline) + device dropdown.
- Device card: render image + name ("Xiao Biscuit") + state pill + chevron to detail.
- **Personal Stats** — gamified: Level + circular XP ring + `XP / nextXP`, 3-card carousel,
  stat icons (wardrive/upload/wifi/cloud). (This mirrors WDG's game loop.)
- **Quick Actions** — editable 2×2 grid (Scan APs, Wardrive, BT Scan, Deauth).
- **Recent Scans** list ("No recent scans" empty state).

### WiFi tab — "WiFi Tools", sub-tabs **Sniffing / Attacks / Scanners**
- Sniffing (`bm-wifi.png`): Scan Access Points, Scan Stations, Scan All; then Beacon Frames,
  Probe Requests, Deauth Frames, Raw Capture, Pwnagotchi.
- Attacks (`bm-wifi-attacks.png`, all red): Deauth, Beacon Spam, RickRoll, Probe Attack,
  Bad Message, Sleep Attack, Captive Portal (Evil Portal & Karma).
- Scanners (`bm-wifi-scanners.png`): Ping, ARP, Port Scan All, SSH (22), Telnet (23).

### Bluetooth tab — "Bluetooth Tools", sub-tabs **Scanning / Attacks**
- Scanning (`bm-bt.png`, `bm-bt-2.png`): Bluetooth Scan, Find My Sniff (AirTags/FMNA/DULT),
  SmartTag 🔒(fw1.4.36+), Flipper, Skimmer, Axon (body cams), Meta (glasses),
  nyanBOX 🔒(1.4.34+), Biscuit 🔒(1.4.34+), Meshtastic 🔒(1.0.94+).
- Attacks (`bm-bt-attacks.png`): Sour Apple, Swiftpair, Samsung Spam, Google Fast Pair,
  Flipper Spam, BLE Spam All, AirTag Spoof, Find My Beeper.

### Wardrive tab (`bm-wardrive.png`) — the centrepiece
- Full-screen live map, GPS puck + accuracy badge (±6m), recenter button, History/Settings/Help.
- Bottom panel: **Mode** selector (All), live counters (WiFi / BT / eye-samples / duration),
  big green **Start**, and a shield (authorized-use) button.
- **WDGWARS overlay button on the map** → opens WDG panel (see integration below).

### Misc tab (`bm-misc.png`)
802.15.4 Mesh Recon (Zigbee/Thread/Matter), HID Automation 🔒(needs Pro/Ultra — *hardware* gate),
**Flock Detection** (BLE+WiFi), Drone Detection (RemoteID).

### Terminal (`bm-terminal.png`)
Raw console: scrollback area + **CMD** prefix chip + "Enter command…" input + send; info/clear icons.
→ validates our single-fleet console design.

### Settings (`bm-settings.png`)
Device Settings · Connection / Wardriving / Counter Surveillance / **Wardrive APIs** ·
Scanning & Data / Custom OUI / Logging & Terminal · Display & UI / Export & Import.
(WDG/WiGLE credentials live under **Wardrive APIs**.)

### Connection sheet (`bm-connection.png`)
Side sheet: **+ Add new device** + **KNOWN DEVICES** list (saved boards, with "Out of range"
status). **Pick-one** model — this is the single biggest gap our fleet app closes.

### Gating patterns to mirror
1. **Firmware-version gate** — greyed row + lock + "Requires firmware vX.Y.Z+".
2. **Hardware-tier gate** — e.g. HID needs Biscuit Pro/Ultra.
3. **Authorized-use gate** — shield button by the attack/wardrive Start control.

---

## 2. WDG Wars (`pl.wdgwars.client`, v1.1.4-beta)

A geolocation **territory-control game**. Splash = tactical-gorilla mascot, cyan accent.
Bottom nav, 6 tabs: **Home · Map · Chat(99+) · Bounties · Ranking · Profile**.

### Home (`wdg-02.png`)
- Deck/identity header: 👑 CAK3D, team CAK3D, loc US, joined 2026-10-05.
- **Feeder-app cards** (the integration surface):
  - **WARDRIVE GO** — "Ours shows the map. RocketGod's Wardrive Go fills it: scans and sends
    straight to WDGWars." → feeder apps upload scans to the WDG server.
  - **BT HOUSE** — catalogue nearby BLE (speakers, trackers, toothbrushes).
- **Your Stats vs Portal:** WiFi 29,569 · BLE 60,180 · ADS-B 0 · **MESH 0** (22,509 global) ·
  Reinforce 8,573 — global shares shown.
- **Territory Decay:** Fresh / Decay / Critical / Vulnerable counts (cells decay → must re-scan).
- **Team** (members, APs, role) and **Credits**.

### Map (`wdg-map.png` loading, `wdg-map2.png` loaded)
- World divided into large **grid cells coloured by ownership** (yours blue/purple vs enemy red).
- Your **wardrive GPS track** + captured AP points overlaid on real streets; green dot = you.
- Top stat strip (📡 APs · 🛡 reinforce · ✈ ADS-B · 📶 mesh) + filter / recenter /
  **download-cells** / refresh.

---

## 3. What this means for Biscuit Mesh

### Mirror (our app's screens, in build order)
1. **Fleet / Connection** — our differentiator. Same saved-device + add-new pattern, but
   **multi-select / connect-many**. Reuse the card pattern (icon, name, MAC, state, RSSI).
2. **Per-board detail** — status dashboard (battery, fw, temp, partition) + the board's own
   tool tabs.
3. **Tools** — replicate the WiFi (Sniffing/Attacks/Scanners), Bluetooth (Scanning/Attacks),
   Misc lists as command-dispatch screens over the generic `command()` layer, with the three
   gating patterns. Offensive lists stay behind the authorized-use shield.
4. **Fleet console** — the Terminal pattern, but multiplexed across boards (already in FleetManager).
5. **Wardrive map** — live map + Start + mode + counters, aggregating samples from the **whole
   fleet** at once (several boards wardriving in parallel = our unique value).

### WDG integration (now concrete)
- WDG Wars is the game server; apps integrate as **feeders**: upload wardrive samples
  (WiFi/BLE/mesh + GPS) to the WDG API, and **pull owned-cell data** to overlay on our map.
- Credentials belong under a "Wardrive APIs" settings screen, same as Biscuit.
- **Standout angle:** WDG has a **MESH** signal category the user is at **0** on. A fleet of
  ESP32 boards is a natural **mesh feeder** — our app could be the first to meaningfully fill it.
- Open question for the user: is there a documented WDG Wars feeder API (endpoint + auth), or do
  we mirror what Wardrive Go sends? Need that before building the uploader.

### Screenshot index (`G:\MyPetID-work\biscuit-explore\`)
bm-01 (dashboard) · bm-wifi / -attacks / -scanners · bm-bt / -bt-2 / -bt-attacks ·
bm-wardrive · bm-wdgwars-btn · bm-misc · bm-terminal · bm-settings · bm-connection ·
wdg-01 (splash) · wdg-02 (home) · wdg-map / wdg-map2.
