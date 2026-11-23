# Recreating Orbital Radar natively — scoping notes

**Status:** background planning. Current decision is **portal + native intel** — the embedded
Orbital Radar Vision portal stays the live 32k-object globe; we build the lightweight intel
(passes, alerts, light pollution, Kp/crew) natively. This doc is what it would take to replace
the *globe itself* natively, if/when we want to cut the portal loose.

## What Orbital Radar actually does (from driving it on the Fold)

- **Live globe** — 32,152 objects plotted at their real current position (CesiumJS + Cesium ion
  tiles). Drag to spin, pinch to zoom, **tap any object → selection card**. City-lights night
  layer, atmosphere glow, your location + range rings, labeled ISS/Tiangong.
- **Selection card** — type (SPACE STATION / PAYLOAD / DEBRIS / ROCKET BODY), name, "NOW OVER
  <place>", **altitude / speed / inclination / period**, **NEXT PASS YOU CAN SEE** (computed for
  your lat/lon), actions Follow / Sky Lens (AR) / Full profile.
- **Color by orbit class** — LEO yellow · MEO cyan · GEO orange · HEO purple; toggles for
  Starlink / OneWeb / GPS / Rocket bodies / **Debris**; a Tracks toggle draws the orbital path.
- **Alerts** — Re-entries (N in next 48 h), Conjunctions (close approaches, "1 under 1 km"),
  Anomalies, Launches (agency/site/countdown). Filter tabs + push-worthy events.
- **Search** — name / NORAD ID / constellation; browse by constellation with counts + operator +
  orbit class (Starlink 11,200, OneWeb 654, GPS 73, Galileo, GLONASS, Beidou, Iridium, NOAA…).
- **Profile** — live position + **ground-track map** (past solid / future dashed), orbital params,
  amateur-radio frequencies & passes, crew manifest.
- **Home intel tiles** — Tracked count, **In Space** (crew count), **Kp** (geomagnetic index),
  rotating "X VISIBLE — <time> · <max°> up · in <countdown>" overhead banner.

## What we already have in the app

- `geo/Ephemeris.kt` — low-precision Sun/Moon/planet + bright-star az/el for observer+time.
  **Covers twilight and moonlight directly** → the light-pollution/sky-conditions readout.
- `sensors/GnssManager.kt` + `GlobeScreen`/`SatellitesScreen` — but these are **GPS/GNSS sats in
  view from the phone receiver**, NOT TLE-propagated orbital objects. No reuse for arbitrary sats.
- `geo/AuroraClient.kt` — space weather (likely Kp/aurora from NOAA SWPC) → reuse for the Kp tile.
- `AdsbClient` (Sky AR / Aircraft) — ADS-B aircraft, no-key. Pattern for a polled REST client.
- 3D/render: **MapLibre GL 11.8.8** (already a dep, has a globe projection), osmdroid (2D maps),
  CameraX (AR / Sky Lens equivalent). No Cesium.
- **Missing entirely:** TLE ingest, SGP4/SDP4 propagation, ECI↔geodetic/topocentric transforms,
  SATCAT metadata (type/operator/constellation), pass prediction, GPU point rendering at 32k.

## What a native rebuild needs (the gap)

1. **TLE / GP data pipeline** — Celestrak GP (`/NORAD/elements/gp.php?GROUP=active&FORMAT=tle` and
   per-group: starlink, oneweb, gps-ops, galileo, glonass, beidou, iridium, noaa, stations,
   visual, 1999-025 debris, cosmos-2251-debris, last-30-days…). ~32k objects. Cache on disk, refresh
   daily (TLEs age; >1–2 weeks degrades). SATCAT (`satcat.php`) for object type / country /
   launch / RCS / operational status (payload vs rocket body vs debris).
2. **SGP4/SDP4 propagator (Kotlin)** — the real work. Port a vetted implementation (Vallado's is the
   reference; `predict4java`/`orekit` are JVM options but heavy). Input TLE + time → **TEME ECI**
   position/velocity.
3. **Coordinate transforms** — TEME→ECEF (GMST rotation), ECEF→geodetic (lat/lon/alt for "now over"
   + ground track), ECEF→topocentric ENU (az/el/range for the observer → selection card + passes).
4. **Pass prediction** — step the propagator forward (e.g. 10 s steps over 3–5 days), find windows
   where el > threshold (10° default); report AOS/LOS/max-el/time. **Visible** pass = satellite
   sunlit AND observer in darkness (sun el < -6°) — both already derivable (SGP4 sun + Ephemeris).
5. **Orbit classification + color** — from mean motion / semi-major axis: LEO/MEO/GEO/HEO buckets →
   LEO yellow, MEO cyan, GEO orange, HEO purple. Constellation/type from SATCAT + name prefixes.
6. **Renderer** — plot ~32k points at interactive FPS on a phone. Options, best → worst fit:
   (a) **MapLibre GL** 3D globe + a custom symbol/circle layer fed a GeoJSON/feature buffer updated
       off-thread (reuses an existing dep, has the globe projection & gestures/tap built in);
   (b) a bespoke **OpenGL ES / Filament** point cloud (GPU-instanced, full control, most work);
   (c) Compose Canvas (fine for a few hundred, **not** 32k — only for the selected subset/overlay).
   MapLibre (a) is the pragmatic path: globe, pan/zoom/tap, tiles, and a lights raster for the
   night layer all come for free.
7. **Performance budget** — don't propagate 32k every frame. Propagate on a background thread every
   ~1–5 s, write positions into a reused buffer, let the GPU draw. Cull by viewport. Full SGP4 for
   32k is affordable at 1 Hz on a modern phone; the renderer is the real constraint.
8. **Alerts data** — Launches: **Launch Library 2** (`ll.thespacedevs.com`, free, no key). Re-entries:
   Celestrak decay / Space-Track (needs login) / `satcat` decay-date. Conjunctions: Celestrak
   **SOCRATES** CDMs. Kp: NOAA SWPC (reuse AuroraClient). Crew in space: `corsproxy`→ Open Notify /
   a maintained crew JSON. All pollable like AdsbClient.
9. **Sky Lens (AR)** — already have the pieces: CameraX preview + rotation-vector attitude (Sky AR).
   Add the SGP4 sat az/el to the existing overlay to point at a satellite in the sky.

## Recommended phasing (if we go native)

- **P0 (done/next, no SGP4):** light-pollution/sky-conditions readout (Ephemeris), Kp + crew tiles,
  launches alerts (Launch Library 2). Pure additive native intel — ship alongside the portal.
- **P1:** SGP4 core + transforms + a **single-object tracker** (ISS first): live position, ground
  track, az/el, real pass predictions. This alone delivers "accurate time till overhead" natively.
- **P2:** multi-object catalog + classification + Search/browse by constellation; selection card.
- **P3:** the 32k MapLibre globe layer with color-by-class + filters + Tracks path. This is the big
  one; only worth it if the portal proves insufficient.
- **P4:** re-entry/conjunction alerts, Sky Lens integration, Full-profile depth.

## Bottom line

The globe (P3) is weeks of work and competes with a portal that already does it perfectly. The
**intel** (P0–P2) is where native wins — it's always available, drives push notifications, and
reuses Ephemeris/AuroraClient/CameraX we already have. SGP4 (P1) is the one hard dependency and the
unlock for everything pass-related. Build intel-first; treat the native globe as optional.
