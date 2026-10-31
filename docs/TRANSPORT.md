# Biscuit BLE Transport — implementation notes

Reference for the public Biscuit BLE/GATT surface, as implemented by this app's
`protocol/` and `ble/` layers. Source: CodeHedge Biscuit Wiki, *3rd-Party
Integration*. This describes the **device**; the app is an independent client.

## Advertising & discovery
- Advertised local name is `Biscuit` on **every** board — not a usable filter.
- Scan/filter on the **service UUID** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`.
  (`BleScanner` uses exactly this filter.)

## Primary service `4fafc201-…-914b`
| Char | UUID | Props | Use |
|------|------|-------|-----|
| Command  | `beb5483e-…-26a8` | Write / WriteNoRsp | `CMD:` lines to device |
| Response | `beb5483e-…-26a9` | Notify | RSP / STATUS / DATA / ERROR / TIME |
| Status   | `beb5483e-…-26aa` | Read   | JSON health snapshot |
| Settings | `beb5483e-…-26ab` | R/W/Notify | JSON configuration |
| Security | `beb5483e-…-26ac` | Encrypted Read | optional bonding trigger |

Device Information Service `0x180A`: Model `0x2A24`, Firmware `0x2A26`,
Software `0x2A28`. On single-chip DIY/Nyan/Crumb, `0x2A26` is the C5 revision and
`0x2A28` is absent.

## Connection sequence (as coded in `DeviceConnection`)
1. Connect GATT (`TRANSPORT_LE`).
2. Request MTU 517 (device target); anything ≥ 247 avoids fragmentation.
3. Discover services.
4. Read Device Info (model, firmware).
5. `setCharacteristicNotification(Response)` + write `0x0001` to its CCCD.
   **Nothing is answered until notifications are enabled.**
6. Mark READY; read Status.
7. Write `CMD:` lines; expect `RSP` within ~100 ms (acceptance only), then `DATA`
   lines, then a terminal `STATUS`.

Recommended link params: connection interval 30–60 ms, slave latency 0,
supervision timeout 8000 ms. **Do not** send periodic pings or connection-priority
bumps (documented to cause drops). Use BLE 4.2 features only (no 2M/Coded PHY,
no Extended Advertising). Session state is **not** resumed after a drop — re-issue
the command.

## Framing (`protocol/Message.kt`)
`TYPE:SUBTYPE:PAYLOAD\n` — single LF terminator; payload may be empty but both
colons remain (`CMD:stopscan:`). Only the first two colons are structural; a
payload may itself contain colons.

Types: `CMD` (to device), `RSP` (accepted), `STATUS` (state change), `DATA`
(record), `ERROR`, `TIME` (time-sync request).

**Notification packing:** several `DATA` records may arrive in one notification
separated by `\n`, and a record may be split across notifications. The app buffers
incoming bytes and splits on `\n` before parsing each line.

### STATUS subtype codes
`0` Booting · `1` Ready · `2` Busy · `3` OK · `4` Warning · `5` Error ·
`6` Scan complete.

### Status JSON keys (treated as opaque; absent ⇒ unknown, never assumed)
`status`, `battery` (0–100 or −1), `temperatureC` (nullable/stale), `sd`,
`op` (`pktmon`/`eapol` during capture), `wroomPartition`/`c5Partition`,
`otaProtocol` (2 = base64 creds, 3 = adds normal/force), `lastOtaResult`, etc.

## Command families
The app models commands as framed strings sent to the Command characteristic and
telemetry parsed from Response. Families, from the wiki:

- **Monitoring / read:** Wi-Fi scans (`scanap`, `scansta`, `scanall`, sniff*),
  Bluetooth scans (`sniffbt`, …), channel/packet stats. Emit `DATA:` records.
- **Device management:** `stopscan`, status polling, firmware update (`ota:*`,
  base64 Wi-Fi credentials), settings read/write, bond clearing.
- **Authorized testing tools:** active radio operations the firmware performs.
  The app treats these as **opaque pass-through command strings gated behind an
  explicit authorized-use acknowledgment** — it does not special-case or
  encourage them, it only dispatches and renders results. Operators are
  responsible for authorization on the target networks/devices.

Command dispatch is generic: `command(name, params)` frames any line, so new
families need UI, not new transport code.
