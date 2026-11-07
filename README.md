# AltOS Modern UI

A modern desktop interface for **AltOS 1.9.22** (AltusMetrum flight support software). It provides a redesigned telemetry, mapping, and mission control interface while preserving full compatibility with original AltOS firmware, device libraries, and hardware workflows.

---

## Installation & Setup

### Option 1: Replace Installed AltOS UI (Recommended)
Automatically patches your local AltOS installation to launch the modern UI by default.

1. Install a JDK (Java 8-compatible) and the .NET 8 SDK.
2. Run `Build-AltOS-Modern.bat` and `recovery-bridge\Build-RecoveryBridge.bat`.
3. **Close** AltOS if it is currently running.
4. **Double-click** `Use-Modern-UI-With-Installed-AltOS.bat`.
5. **Approve** the Windows Administrator prompt and wait for `"Redesigned UI installed"` to appear.
6. Launch AltOS from your standard Desktop or Start Menu shortcut.

> **Backup & Rollback:** The script creates a backup of your original interface at `altosui-fat.jar.original-backup` in your installed directory (typically `C:\Program Files\AltusMetrum`). To restore the original UI, run `Restore-Original-AltOS-UI.bat`.

*Note: Re-installing or updating official AltOS releases may restore the legacy interface. This launcher patch is specific to **AltOS 1.9.22**.*
*The rollback leaves the separate BLE bridge executable in the installation's `recovery-bridge` folder.*

---

### Option 2: Standalone Mode (No System Changes)
Run the modern interface independently without altering your installed AltOS application.

1. Ensure **Java 8** or newer is installed.
2. Install a JDK and the .NET 8 SDK, then run `Build-AltOS-Modern.bat` and `recovery-bridge\Build-RecoveryBridge.bat`.
3. **Double-click** `Run-AltOS-Modern.bat`.
4. Allow Bluetooth access if prompted by Windows.

*If AltOS does not launch, run `Run-AltOS-Diagnostics.bat` to output the diagnostic error log.*

### Portable Package (Bundled Java Runtime)
To build a portable Windows x64 package that does not require Java to be installed on the target computer:

1. Install a **64-bit Java 8 JDK** and the **.NET 8 SDK**.
2. Run `Build-AltOS-Standalone.bat`.
3. Copy the complete `build\AltOS-Modern-Portable` folder to the target computer and launch `AltOS-Modern.exe`.

The package includes the Java runtime, AltOS files, and BLE recovery bridge. Keep the executable and all adjacent files together.

---

## Key Features

* **Redesigned Dashboard & Layout:**
  * Retains the classic TeleMega mission-control path, preflight checks, focused vehicle controls, and core flight/ground-station tools.
  * Tabbed primary workspace for **Maps**, **Live Monitor**, **3D Flight Path**, and **iPhone Recovery**.
  * Legacy device setup, configuration, and firmware flashing retain native dialog windows for hardware stability.
* **Maps & Offline Support:**
  * Support for **OpenStreetMap**, **USGS Aerial Imagery** (CONUS), and **AltOS Hybrid** map sources.
  * Direct launch site coordinate targeting and fixed pin positioning.
  * **Offline Pre-loading:** Save a 3 km radius around your launch area across multiple zoom levels using AltOS hybrid tiles.
* **3D Flight Path Tracking:**
  * Real-time GPS stream monitoring or recorded telemetry playback.
  * Full 3D rotation, zooming, and fix timeline inspection (relative to launch coordinate origin).
  * Robust handling for dateline crossings and out-of-order GPS packets.
* **Phone Recovery Link:**
  * A foreground iPhone or Android app and Windows BLE bridge exchange phone GPS and live rocket telemetry.
  * Recovery data only: there are no BLE commands for arming, firing, or configuring the flight computer.
  * Requires a BLE-capable Windows 10 (version 2004 or newer) computer and the phone app open in the foreground. See [the BLE protocol](recovery/BLE-PROTOCOL.md) and [the Android app instructions](recovery/android/README.md).
* **Streamlined Navigation:**
  * Cleaned utility menu (removed unused legacy items like Kalman navigation).
  * Direct callsign editing and receiver handover from Channel Scanner to Live Monitor.
* **Voice Alerts:**
  * Uses the original FreeTTS voice with lower pitch and measured pacing for calmer, cockpit-inspired callouts.
* **High-DPI & Theme Support:**
  * UI scaling for modern high-resolution displays.
  * High-contrast labels on map overlays for legibility over both dark and light satellite tiles.
  * A neutral charcoal palette, outlined panels, rounded navigation, and restrained blue accents inspired by SDSHub.
  * Theme integration with Windows and Nimbus Look & Feel preferences.

---

## File Structure

```text
├── AltosUI.java                      # Redesigned desktop UI and recovery workspace
├── AltosDesktopTheme.java            # Desktop theme
├── AltosRecoveryLink.java            # Java IPC and telemetry encoder
├── altosui-redesigned.jar            # Redesigned UI executable JAR
├── altosui-fat.jar                   # Original AltOS JAR (included for fallback/verification)
├── recovery-bridge/                  # Windows BLE GATT client source and build script
├── recovery/android/                 # Android recovery app source and Gradle project
├── recovery/BLE-PROTOCOL.md          # Shared versioned BLE protocol
├── Build-AltOS-Modern.bat            # Compile the Java UI source into the redesigned JAR
├── Use-Modern-UI-With-Installed-AltOS.bat  # Patch installer script
├── Restore-Original-AltOS-UI.bat     # Patch rollback script
├── Run-AltOS-Modern.bat              # Standalone execution script
└── Run-AltOS-Diagnostics.bat         # Console launcher for troubleshooting
