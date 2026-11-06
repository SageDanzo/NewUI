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
  * **Offline Pre-loading:** Save a 3 km radius around your launch area across multiple zoom levels using AltOS hybrid tiles.
* **3D Flight Path Tracking:**
  * Real-time GPS stream monitoring or recorded telemetry playback.
  * Full 3D rotation, zooming, and fix timeline inspection (relative to launch coordinate origin).
  * Robust handling for dateline crossings and out-of-order GPS packets.
* **Streamlined Navigation:**
  * Cleaned utility menu (removed unused legacy items like Kalman navigation).
  * Direct callsign editing and receiver handover from Channel Scanner to Live Monitor.
* **High-DPI & Theme Support:**
  * UI scaling for modern high-resolution displays.
  * High-contrast labels on map overlays for legibility over both dark and light satellite tiles.
  * Theme integration with Windows and Nimbus Look & Feel preferences.

---

## File Structure

```text
├── source/                           # Java source code for the 6 modified UI classes
├── altosui-redesigned.jar            # Redesigned UI executable JAR
├── altosui-fat.jar                   # Original AltOS JAR (included for fallback/verification)
├── Use-Modern-UI-With-Installed-AltOS.bat  # Patch installer script
├── Restore-Original-AltOS-UI.bat     # Patch rollback script
├── Run-AltOS-Modern.bat              # Standalone execution script
└── Run-AltOS-Diagnostics.bat         # Console launcher for troubleshooting
