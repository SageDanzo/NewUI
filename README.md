# AltOS Modern UI

A modern desktop interface for **AltOS 1.9.22** (AltusMetrum flight support software). It provides a redesigned telemetry, mapping, and mission control interface while preserving full compatibility with original AltOS firmware, device libraries, and hardware workflows.

---

## Installation & Setup

### Option 1: Replace Installed AltOS UI (Recommended)
Automatically patches your local AltOS installation to launch the modern UI by default.

1. **Close** AltOS if it is currently running.
2. **Double-click** `Use-Modern-UI-With-Installed-AltOS.bat`.
3. **Approve** the Windows Administrator prompt and wait for `"Redesigned UI installed"` to appear.
4. Launch AltOS from your standard Desktop or Start Menu shortcut.

> **Backup & Rollback:** The script creates a backup of your original interface at `altosui-fat.jar.original-backup` in your installed directory (typically `C:\Program Files\AltusMetrum`). To restore the original UI, run `Restore-Original-AltOS-UI.bat`.

*Note: Re-installing or updating official AltOS releases may restore the legacy interface. This launcher patch is specific to **AltOS 1.9.22**.*

---

### Option 2: Standalone Mode (No System Changes)
Run the modern interface independently without altering your installed AltOS application.

1. Ensure **Java 8** or newer is installed.
2. **Double-click** `Run-AltOS-Modern.bat`.
3. Allow device/network access if prompted by Windows.

*If AltOS does not launch, run `Run-AltOS-Diagnostics.bat` to output the diagnostic error log.*

---

## Key Features

* **Redesigned Dashboard & Layout:**
  * Retains the classic TeleMega mission-control path, preflight checks, focused vehicle controls, and core flight/ground-station tools.
  * Tabbed primary workspace for **Maps**, **Live Monitor**, and **3D Flight Path**.
  * Legacy device setup, configuration, and firmware flashing retain native dialog windows for hardware stability.
* **Maps & Offline Support:**
  * Support for **OpenStreetMap**, **USGS Aerial Imagery** (CONUS), and **AltOS Hybrid** map sources.
  * Direct launch site coordinate targeting and fixed pin positioning.
  * Opening a recorded flight selects USGS aerial imagery automatically and draws its measured GPS route. The recorded-flight graph window’s **Map** tab also uses this USGS view, including flights graphed after download. Click **Open recorded flight** in Maps to choose a file there; **View in 3D** opens the same track. The selected fix on the map follows the 3D timeline.
  * **Offline Pre-loading:** Save a 3 km radius around your launch area across multiple zoom levels using AltOS hybrid tiles.
* **3D Flight Path Tracking:**
  * Real-time GPS stream monitoring or recorded telemetry playback.
  * WGS84 east/north/up conversion relative to the first valid GPS fix, with reported GPS altitude and coordinates visible for each fix.
  * Orbit, zoom, click to select a fix, use arrow keys, and reset to top or side view. Replay uses recorded fix times with selectable 1×, 5×, and 20× speed; missing telemetry stays visibly disconnected.
  * Use **GROUND → USGS aerial** to show an aerial image of the first GPS fix beneath the 3D route, or choose **Grid** to return to the plain ground plane. Imagery is a flat reference plane, not elevation terrain. A connection is needed for tiles that have not been cached; the grid remains visible while imagery loads.
  * Robust handling for dateline crossings and out-of-order GPS packets.
* **Streamlined Navigation:**
  * Cleaned utility menu (removed unused legacy items like Kalman navigation).
  * Direct callsign editing and receiver handover from Channel Scanner to Live Monitor.
* **High-DPI & Theme Support:**
  * UI scaling for modern high-resolution displays.
  * High-contrast labels on map overlays for legibility over both dark and light satellite tiles.
  * A neutral charcoal palette, outlined panels, rounded navigation, and restrained blue accents inspired by SDSHub.
  * Theme integration with Windows and Nimbus Look & Feel preferences.

---

## File Structure

```text
├── AltosUI.java, AltosDesktopTheme.java
├── AltosFlightPathPanel.java           # 3D recorded/live GPS view
├── AltosModernMapPreload.java         # Map tab and recorded-flight selection
├── AltosModernStreetMap.java          # USGS aerial map and GPS track overlay
├── altosui-redesigned.jar            # Redesigned UI executable JAR
├── altosui-fat.jar                   # Original AltOS JAR (included for fallback/verification)
├── Use-Modern-UI-With-Installed-AltOS.bat  # Patch installer script
├── Restore-Original-AltOS-UI.bat     # Patch rollback script
├── Run-AltOS-Modern.bat              # Standalone execution script
└── Run-AltOS-Diagnostics.bat         # Console launcher for troubleshooting
