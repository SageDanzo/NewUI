ALTOS MODERN WINDOWS DESKTOP UI
===============================

This folder contains a redesigned AltOS 1.9.22 Windows desktop interface.

USE YOUR NORMAL INSTALLED ALTOS SHORTCUT
---------------------------------------
1. Close AltOS if it is open.
2. Double-click Use-Modern-UI-With-Installed-AltOS.bat in this folder.
3. Approve the Windows administrator prompt and wait for "Redesigned UI
   installed" in the window.
4. Open AltOS from your usual desktop or Start menu shortcut. It will use
   this redesigned UI without a second launcher window.
5. Check for "USGS GPS" in the bottom-left sidebar. If it is absent, the
   installed app is still running a different JAR or another installation.

The switch keeps an original backup named altosui-fat.jar.original-backup in
your installed AltOS folder (normally C:\Program Files\AltusMetrum). It leaves
the flight-computer firmware, device libraries, and shortcut alone.
To return to the old interface, close AltOS and double-click
Restore-Original-AltOS-UI.bat in this folder.

If you install a newer official AltOS version later, the installer may put
the original UI back. Only use this switch with AltOS 1.9.22.

RUN WITHOUT CHANGING THE INSTALLED APP
--------------------------------------

To run it:

1. Make sure Java 8 or newer is installed. The installed AltOS 1.9.22 files
   use Java 8 class files.
2. Double-click Run-AltOS-Modern.bat.
3. If Windows asks whether to allow the program to access a device, allow it.

The launcher starts AltOS without leaving a command-prompt or Windows Terminal
tab open behind the application.
If AltOS does not open, double-click Run-AltOS-Diagnostics.bat to see the error
message and copy it for troubleshooting.

Without the switch above, the original installed AltOS shortcut is unaffected.
This separate launcher uses altosui-redesigned.jar. The original altosui-fat.jar
is included in the extracted folder for compatibility and backup verification.

Included behavior:

- Original TeleMega mission-control layout with a guided preflight path,
  focused vehicle controls, and grouped flight/data/ground-station tools.
- The original AltOS functions remain available as clickable controls, including
  monitoring, idle checks, configuration, firmware, launch, igniter, maps,
  channel scanning, replay, download, graphing, export, and preferences.
- The unused Kalman navigation item has been removed. Callsign setup is under
  Utilities; the channel scanner can hand a selected receiver to live monitor.
- Maps, the live monitor, and the 3D flight path open as tabs in the main
  desktop window. Device setup, firmware, and some original AltOS tools still
  use their existing dialogs or windows to preserve their device workflows.
- If a receiver is already open, click Live monitor in the sidebar to return
  to its tab. Use Disconnect there before opening a recorded 3D flight or
  replay; choosing those while connected will not drop the receiver.
- In Maps, select a saved launch site or enter decimal coordinates and click
  Show on map. The source menu offers modern OpenStreetMap streets, USGS aerial
  imagery (continental U.S.), and the AltOS hybrid view. Use the mouse wheel
  or + and - buttons to zoom; drag to pan online maps. No zoom setup is needed.
  USGS imagery acquisition year varies by location and is not guaranteed to
  be newer than 2017 at every site.
- The launch-site pin stays at the selected coordinates when you pan or zoom.
  "Show on map" returns the view to those coordinates. USGS aerial starts with
  the faster tiled imagery service. On close zooms, its cached overview stays
  visible while more detailed NAIP imagery loads. New online imagery still
  requires an internet connection; the detailed service can be slower.
- Save launch-area map offline prepares a 3 km area at several useful zoom
  levels using AltOS hybrid tiles. Cancel stops the work; visited online tiles
  remain cached, but the offline button does not download OSM or USGS tiles.
  The online maps require an internet connection for new tiles. Cached offline
  tiles cannot cover every possible zoom and location. Beyond AltOS Zoom +2,
  the app switches from hybrid to online USGS aerial and keeps the selected
  magnification; use the source menu to return to offline hybrid.
- Opening a recorded flight automatically selects USGS aerial in Maps, centers
  the recorded GPS route, and marks the fix selected on the 3D timeline.
  The Map tab in a recorded-flight graph also uses USGS aerial instead of
  the older AltOS hybrid view. This includes flights graphed after download.
  In Maps, use Open recorded flight to select a recording and View in 3D
  to inspect its measured path. Maps require an internet connection for imagery;
  no track is invented for a recording without valid GPS fixes.
- Open a recorded flight in the 3D flight path tab or connect a TeleMega and
  watch valid GPS fixes arrive. Drag to rotate, scroll to zoom, and use the
  timeline or click the route to inspect a fix. Replay follows recorded GPS
  timestamps at 1x, 5x, or 20x speed. Reset, top, and side view controls
  simplify orientation. The path uses WGS84 east/north/up relative to the
  first GPS fix, and gaps in telemetry remain gaps. A flight without valid GPS
  fixes cannot have a measured 3D track. This view does not predict a landing
  location or change flight computer behavior. Date-line crossings and
  out-of-order GPS packets are handled without drawing a false route.
- Map data: © OpenStreetMap contributors (openstreetmap.org/copyright);
  aerial imagery: USGS / USDA NAIP (usgs.gov); offline hybrid: Altus Metrum.
- The dashboard, tabs, dropdowns, buttons, timeline, and scrollbars retain
  readable colors if you change Look & Feel in Preferences. Original AltOS
  prompts and device dialogs use the selected Windows or Nimbus colors.
  Callsign editing has a readable field and rejects an empty callsign.
- Map loading, missing-tile, and delayed-aerial messages use white text on a
  dark label, so they remain legible over both light aerial and dark map tiles.
- The app now opens using most of your available Windows screen space. Text,
  navigation, map controls, and tool cards grow with larger displays; smaller
  displays can scroll the tool cards vertically. The channel scanner can open
  the selected receiver. If Windows display scaling still makes the interface
  too small, adjust the system's Display > Scale setting and restart AltOS.
- Java source for the four changed desktop classes is included next to the
  JAR: AltosUI.java, AltosFlightPathPanel.java, AltosModernMapPreload.java,
  and AltosModernStreetMap.java. AltosDesktopTheme.java is also included.
- Live flight monitoring.
- Flight data saving, replay, graphing, and export.
- Altimeter and ground-station configuration.
- Firmware flashing, channel scanning, map loading, idle monitoring, and
  igniter testing through the existing AltOS version 14 libraries.

The new dashboard changes the desktop presentation. It does not change the
AltOS firmware, radio protocol, device DLLs, or hardware behavior.
