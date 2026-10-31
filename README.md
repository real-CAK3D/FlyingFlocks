# Biscuit Mesh

A clean-room Android companion/manager for your own ESP32-C5 "Biscuit DIY" boards,
built in your own vision. Where the stock Biscuit Manager app pairs with **one**
board at a time, Biscuit Mesh keeps a whole **fleet** connected at once from a
single screen.

> Biscuit is a portable Wi-Fi / Bluetooth security-research platform intended for
> **authorized** testing on systems you own or have permission to assess. This app
> is a remote control for that hardware — the firmware performs all radio
> operations. Use it only where you are authorized to.

## What makes it different

- **Fleet registry** — several boards discovered and connected simultaneously
  (bounded by the Android concurrent-GATT limit, ~4–7 depending on the phone).
- **Broadcast commands** — fan one command out to every connected board at once.
- **One live console** — protocol lines from the whole fleet in a single stream.
- **Designed for later WDG integration** — the data layer is kept adapter-friendly
  so a Watch Dogs Go / WDG export or deep-link bridge can be added without a rewrite.

## Stack

- Kotlin + Jetpack Compose (Material 3), `minSdk 26`, `compileSdk 36`.
- No third-party BLE library — direct `BluetoothGatt` with a serialized op queue.
- Talks the **public** Biscuit BLE/GATT surface (see `docs/TRANSPORT.md`).

## Architecture

```
protocol/   BiscuitGatt (UUIDs, conn params), Message (TYPE:SUBTYPE:PAYLOAD framing)
model/      BiscuitDevice, DeviceStatus, ConnectionState
ble/        BleScanner (service-UUID filter)
            DeviceConnection (per-board GATT state machine + op queue + line buffer)
            FleetManager (the multi-device registry; broadcast; aggregated log)
ui/         FleetScreen (device list), theme/
MainActivity (runtime BLE permission gate), MainViewModel
```

## Build

The toolchain lives on `G:\Android` (JDK 21, Gradle 8.14.3, SDK 35/36). `local.properties`
points at `G:\Android\sdk`.

```bash
# from G:\biscuit-mesh, with JAVA_HOME=G:\Android\jdk
gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Status

Foundation: scan → fleet list → connect → live status/console. Feature modules
(Wi-Fi monitoring, OTA, settings, the authorized-testing tools, WDG bridge) build
on top of the command-dispatch layer and come next.
