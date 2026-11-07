# iOS recovery app

This is a native SwiftUI/CoreBluetooth/CoreLocation source project with no
third-party dependencies. Open `RecoveryApp.xcodeproj` in Xcode on macOS, select
the `RecoveryApp` scheme, set a signing team if prompted, then build and run on
an iPhone running iOS 15 or later. Bluetooth advertising/peripheral behavior
should be tested on a physical iPhone; the simulator does not provide the
required BLE peripheral operation.

On first run, allow Bluetooth and While Using location access. The app advertises
the recovery GATT service and sends phone GPS while foregrounded. It stops
advertising and location updates when backgrounded. The telemetry display marks
data stale when its source timestamp or last local receipt is more than 10
seconds old.

The app implements only the recovery-information protocol in
`../BLE-PROTOCOL.md`. It has no flight-computer control, arming, firing, or
configuration commands.
