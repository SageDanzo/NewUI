import CoreLocation
import Combine
import SwiftUI
import MapKit

struct ContentView: View {
    @ObservedObject var recovery: RecoveryManager
    @State private var now = Date()

    private let clock = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    statusCard
                    phoneLocationCard
                    rocketCard
                    mapCard
                    Text("Recovery information only; BLE data is unauthenticated. Verify the phone, coordinates, vehicle, and recovery system in person.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
                .padding()
            }
            .navigationTitle("Recovery Link")
            .onReceive(clock) { now = $0 }
        }
        .navigationViewStyle(.stack)
    }

    private var statusCard: some View {
        GroupBox("Link status") {
            VStack(alignment: .leading, spacing: 8) {
                Label(recovery.bluetoothState, systemImage: "dot.radiowaves.left.and.right")
                Label(recovery.isAdvertising ? "Advertising recovery service" : "Not advertising",
                      systemImage: recovery.isAdvertising ? "antenna.radiowaves.left.and.right" : "antenna.radiowaves.left.and.right.slash")
                Label(recovery.isSubscribed ? "Desktop subscribed" : "Waiting for desktop",
                      systemImage: recovery.isSubscribed ? "link" : "link.badge.plus")
                Label(recovery.locationStatus, systemImage: "location")
                if let protocolNotice = recovery.protocolNotice {
                    Text(protocolNotice)
                        .font(.footnote)
                        .foregroundColor(.orange)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var phoneLocationCard: some View {
        GroupBox("Phone GPS") {
            if let location = recovery.phoneLocation {
                VStack(alignment: .leading, spacing: 6) {
                    Text(coordinateText(location.coordinate))
                        .font(.system(.body, design: .monospaced))
                    Text("Accuracy ±\(location.horizontalAccuracy, specifier: "%.1f") m • Altitude \(location.altitude, specifier: "%.1f") m")
                        .font(.subheadline)
                    Text("Fix: \(location.timestamp, style: .date) \(location.timestamp, style: .time)")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text("Waiting for a valid location fix.")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var rocketCard: some View {
        GroupBox("Rocket telemetry") {
            if let telemetry = recovery.telemetry {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(gpsLockText(telemetry))
                            .font(.headline)
                            .foregroundColor(telemetry.gpsLocked == true ? .green : .orange)
                        Spacer()
                        Text(freshnessText(for: telemetry))
                            .font(.subheadline)
                            .foregroundColor(isFresh(telemetry) ? .green : .red)
                    }
                    Text("Telemetry time: \(Date(timeIntervalSince1970: Double(telemetry.timestamp) / 1000), style: .date) \(Date(timeIntervalSince1970: Double(telemetry.timestamp) / 1000), style: .time)")
                        .font(.footnote)
                    Text("Received \(recovery.telemetryReceivedAt.map { relativeTime(since: $0) } ?? "—") ago")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    if let coordinate = telemetry.coordinate {
                        Text("Position: \(coordinateText(coordinate))")
                            .font(.system(.body, design: .monospaced))
                    } else {
                        Text("Position unavailable")
                            .foregroundColor(.secondary)
                    }
                    metricRow("State code", value: telemetry.flightState.map { String($0) } ?? "—")
                    metricRow("Altitude", value: meters(telemetry.altitudeM))
                    metricRow("Height", value: meters(telemetry.heightM))
                    metricRow("Speed", value: speed(telemetry.speedMps))
                    metricRow("GPS speed", value: speed(telemetry.gpsSpeedMps))
                    metricRow("GPS climb rate", value: speed(telemetry.gpsClimbRateMps))
                    metricRow("Course", value: degrees(telemetry.courseDeg))
                    metricRow("Acceleration", value: acceleration(telemetry.accelerationMps2))
                    metricRow("Accel. along / across / through", value:
                        "\(acceleration(telemetry.accelAlongMps2)) / \(acceleration(telemetry.accelAcrossMps2)) / \(acceleration(telemetry.accelThroughMps2))")
                    metricRow("Gyro roll / pitch / yaw", value:
                        "\(degreesPerSecond(telemetry.gyroRollDps)) / \(degreesPerSecond(telemetry.gyroPitchDps)) / \(degreesPerSecond(telemetry.gyroYawDps))")
                    metricRow("GPS altitude", value: meters(telemetry.gpsAltitudeM))
                    metricRow("Pressure", value: pressure(telemetry.pressurePa))
                    metricRow("Temperature", value: temperature(telemetry.temperatureC))
                    metricRow("Battery / main / pyro voltage", value:
                        "\(voltage(telemetry.batteryV)) / \(voltage(telemetry.mainV)) / \(voltage(telemetry.pyroV))")
                    metricRow("RSSI", value: telemetry.rssiDbm.map { "\($0) dBm" } ?? "—")
                    metricRow("Satellites", value: telemetry.satellites.map { String($0) } ?? "—")
                    metricRow("Flight flags", value:
                        "Ascent \(flag(telemetry.ascent)) · Boost \(flag(telemetry.boost)) · Landed \(flag(telemetry.landed))")
                    if let serial = telemetry.serial {
                        metricRow("Serial", value: String(serial))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text("No rocket telemetry received.")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var mapCard: some View {
        GroupBox("Locations") {
            if recovery.mapPoints.isEmpty {
                Text("Map locations will appear when GPS coordinates are available.")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 160, alignment: .center)
            } else {
                Map(coordinateRegion: $recovery.mapRegion, annotationItems: recovery.mapPoints) { point in
                    MapAnnotation(coordinate: point.coordinate) {
                        VStack(spacing: 2) {
                            Text(point.title)
                                .font(.caption2)
                                .padding(3)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 4))
                            Image(systemName: "mappin.circle.fill")
                                .font(.title)
                                .foregroundColor(point.color)
                        }
                    }
                }
                .frame(height: 260)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                HStack(spacing: 16) {
                    Label("Phone", systemImage: "location.fill").foregroundColor(.blue)
                    Label("Rocket", systemImage: "smallcircle.filled.circle").foregroundColor(.red)
                }
                .font(.footnote)
                .padding(.top, 6)
            }
        }
    }

    private func metricRow(_ name: String, value: String) -> some View {
        HStack {
            Text(name).foregroundColor(.secondary)
            Spacer()
            Text(value).multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
    }

    private func coordinateText(_ coordinate: CLLocationCoordinate2D) -> String {
        String(format: "%.6f, %.6f", coordinate.latitude, coordinate.longitude)
    }

    private func meters(_ value: Double?) -> String {
        value.map { String(format: "%.1f m", $0) } ?? "—"
    }

    private func speed(_ value: Double?) -> String {
        value.map { String(format: "%.1f m/s", $0) } ?? "—"
    }

    private func acceleration(_ value: Double?) -> String {
        value.map { String(format: "%.2f m/s²", $0) } ?? "—"
    }

    private func degrees(_ value: Double?) -> String {
        value.map { String(format: "%.1f°", $0) } ?? "—"
    }

    private func degreesPerSecond(_ value: Double?) -> String {
        value.map { String(format: "%.1f°/s", $0) } ?? "—"
    }

    private func pressure(_ value: Double?) -> String {
        value.map { String(format: "%.0f Pa", $0) } ?? "—"
    }

    private func temperature(_ value: Double?) -> String {
        value.map { String(format: "%.1f °C", $0) } ?? "—"
    }

    private func voltage(_ value: Double?) -> String {
        value.map { String(format: "%.2f V", $0) } ?? "—"
    }

    private func flag(_ value: Bool?) -> String {
        value.map { $0 ? "yes" : "no" } ?? "—"
    }

    private func gpsLockText(_ telemetry: RocketTelemetry) -> String {
        switch telemetry.gpsLocked {
        case .some(true): return "GPS LOCKED"
        case .some(false): return "GPS NOT LOCKED"
        case .none: return "GPS LOCK UNKNOWN"
        }
    }

    private func relativeTime(since date: Date) -> String {
        let seconds = max(0, Int(now.timeIntervalSince(date)))
        return "\(seconds)s"
    }

    private func isFresh(_ telemetry: RocketTelemetry) -> Bool {
        let sourceAge = now.timeIntervalSince1970 - Double(telemetry.timestamp) / 1000
        let receiveAge = recovery.telemetryReceivedAt.map { now.timeIntervalSince($0) } ?? .infinity
        return sourceAge >= -5 && sourceAge <= 10 && receiveAge <= 10
    }

    private func freshnessText(for telemetry: RocketTelemetry) -> String {
        if now.timeIntervalSince1970 - Double(telemetry.timestamp) / 1000 < -5 {
            return "Clock skew"
        }
        return isFresh(telemetry) ? "Fresh" : "STALE"
    }
}
