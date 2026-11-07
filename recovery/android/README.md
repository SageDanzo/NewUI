# Android recovery app

This is the Android companion to the iOS recovery app. Open the
`recovery/android` directory in Android Studio and sync the Gradle project.
The checked-in Gradle wrapper selects Gradle 8.9 and Android Gradle Plugin
8.7.3. You can also build from the repository root with
`cd recovery\android && gradlew.bat assembleDebug` on Windows or
`cd recovery/android && bash ./gradlew assembleDebug` on macOS/Linux. An
Android SDK platform 35 and JDK 17 are required.

The root `codemagic.yaml` builds the debug APK on Codemagic. To run the app,
install the `app/build/outputs/apk/debug/app-debug.apk` artifact on a physical
Android device running Android 8.0 (API 26) or newer. The device must support
BLE peripheral advertising; not all Android phones do.

On first run, allow **Nearby devices** (Bluetooth) and **Location** access, and
turn on Bluetooth and device location. Keep the app open in the foreground
while connected: Android stops advertising and location updates when the app
is backgrounded. If Bluetooth was off when the app started, turn it on and tap
**Retry recovery link**. The app advertises the same recovery GATT service and
characteristics as iOS, shares phone GPS, and displays live rocket telemetry
from the Windows ground station. Location buttons open the selected position in
an installed maps app.

The app implements only the recovery-information protocol in
`../BLE-PROTOCOL.md`. It has no flight-computer control, arming, firing, or
configuration commands. The BLE link is unauthenticated; verify the connected
phone and displayed coordinates in person.
