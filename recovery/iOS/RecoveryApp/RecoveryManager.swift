import Combine
import CoreBluetooth
import CoreLocation
import Foundation
import MapKit
import SwiftUI

private enum RecoveryUUID {
    static let service = CBUUID(string: "D4A10000-3B7A-4C5F-9A13-6C7E4B201000")
    static let desktopToPhone = CBUUID(string: "D4A10001-3B7A-4C5F-9A13-6C7E4B201000")
    static let phoneToDesktop = CBUUID(string: "D4A10002-3B7A-4C5F-9A13-6C7E4B201000")
}

struct PhoneLocationMessage: Encodable {
    let v = 1
    let type = "phone_location"
    let timestamp: Int64
    let latitude: Double
    let longitude: Double
    let altitudeM: Double
    let accuracyM: Double
}

struct RocketTelemetry: Decodable {
    let v: Int
    let type: String
    let timestamp: Int64
    let serial: Int?
    let flightState: Int?
    let ascent: Bool?
    let boost: Bool?
    let landed: Bool?
    let gpsLocked: Bool?
    let latitude: Double?
    let longitude: Double?
    let gpsAltitudeM: Double?
    let altitudeM: Double?
    let heightM: Double?
    let speedMps: Double?
    let gpsSpeedMps: Double?
    let gpsClimbRateMps: Double?
    let courseDeg: Double?
    let accelerationMps2: Double?
    let accelAlongMps2: Double?
    let accelAcrossMps2: Double?
    let accelThroughMps2: Double?
    let gyroRollDps: Double?
    let gyroPitchDps: Double?
    let gyroYawDps: Double?
    let pressurePa: Double?
    let temperatureC: Double?
    let batteryV: Double?
    let mainV: Double?
    let pyroV: Double?
    let rssiDbm: Int?
    let satellites: Int?

    var coordinate: CLLocationCoordinate2D? {
        guard let latitude, let longitude,
              CLLocationCoordinate2DIsValid(CLLocationCoordinate2D(latitude: latitude, longitude: longitude)) else {
            return nil
        }
        return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

struct RecoveryMapPoint: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let title: String
    let color: Color
}

final class RecoveryManager: NSObject, ObservableObject {
    @Published private(set) var bluetoothState = "Starting Bluetooth…"
    @Published private(set) var isAdvertising = false
    @Published private(set) var isSubscribed = false
    @Published private(set) var locationStatus = "Waiting for location permission"
    @Published private(set) var phoneLocation: CLLocation?
    @Published private(set) var telemetry: RocketTelemetry?
    @Published private(set) var telemetryReceivedAt: Date?
    @Published private(set) var protocolNotice: String?
    @Published var mapRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 37.421998, longitude: -122.084),
        span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
    )

    private let locationManager = CLLocationManager()
    private var peripheralManager: CBPeripheralManager!
    private var foreground = false
    private var serviceAdded = false
    private var serviceAddRequested = false
    private var locationUpdatesStarted = false
    private var outboundSequence: UInt16 = 0
    private var outboundFrames: [Data] = []
    private var reassembler = FrameReassembler()

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 1
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }

    var mapPoints: [RecoveryMapPoint] {
        var points: [RecoveryMapPoint] = []
        if let phoneLocation {
            points.append(RecoveryMapPoint(
                id: "phone",
                coordinate: phoneLocation.coordinate,
                title: Date().timeIntervalSince(phoneLocation.timestamp) > 10 ? "Phone • stale" : "Phone",
                color: .blue
            ))
        }
        if let telemetry, let coordinate = telemetry.coordinate {
            let stale = telemetryReceivedAt.map { Date().timeIntervalSince($0) > 10 } ?? true
            let title: String
            let color: Color
            switch telemetry.gpsLocked {
            case .some(true):
                title = stale ? "Rocket • stale" : "Rocket • GPS locked"
                color = stale ? .gray : .red
            case .some(false):
                title = stale ? "Rocket • stale" : "Rocket • GPS not locked"
                color = stale ? .gray : .orange
            case .none:
                title = stale ? "Rocket • stale" : "Rocket • GPS lock unknown"
                color = .gray
            }
            points.append(RecoveryMapPoint(
                id: "rocket",
                coordinate: coordinate,
                title: title,
                color: color
            ))
        }
        return points
    }

    func setForeground(_ active: Bool) {
        foreground = active
        if active {
            updateLocationAuthorization()
            startAdvertisingIfReady()
        } else {
            peripheralManager.stopAdvertising()
            isAdvertising = false
            outboundFrames.removeAll()
            locationManager.stopUpdatingLocation()
            locationUpdatesStarted = false
        }
    }

    func refreshMapRegion() {
        let coordinates = mapPoints.map(\.coordinate)
        guard !coordinates.isEmpty else { return }

        let latitudes = coordinates.map(\.latitude)
        var longitudes = coordinates.map(\.longitude)
        if longitudes.count == 2 {
            if longitudes[1] - longitudes[0] > 180 {
                longitudes[1] -= 360
            } else if longitudes[0] - longitudes[1] > 180 {
                longitudes[1] += 360
            }
        }
        let minLongitude = longitudes.min()!
        let maxLongitude = longitudes.max()!
        var centerLongitude = (minLongitude + maxLongitude) / 2
        if centerLongitude > 180 { centerLongitude -= 360 }
        if centerLongitude < -180 { centerLongitude += 360 }
        let latitudeDelta = max(0.01, (latitudes.max()! - latitudes.min()!) * 1.8)
        let longitudeDelta = max(0.01, (maxLongitude - minLongitude) * 1.8)
        mapRegion = MKCoordinateRegion(
            center: CLLocationCoordinate2D(
                latitude: (latitudes.min()! + latitudes.max()!) / 2,
                longitude: centerLongitude
            ),
            span: MKCoordinateSpan(
                latitudeDelta: min(latitudeDelta, 120),
                longitudeDelta: min(longitudeDelta, 120)
            )
        )
    }

    private func updateLocationAuthorization() {
        switch locationManager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            locationStatus = "Location permission granted"
            if foreground && !locationUpdatesStarted {
                locationManager.startUpdatingLocation()
                locationUpdatesStarted = true
            }
        case .notDetermined:
            locationStatus = "Requesting location permission"
            locationManager.requestWhenInUseAuthorization()
        case .denied:
            locationStatus = "Location permission denied"
        case .restricted:
            locationStatus = "Location access restricted"
        @unknown default:
            locationStatus = "Location permission unavailable"
        }
    }

    private func startAdvertisingIfReady() {
        guard foreground, peripheralManager.state == .poweredOn, serviceAdded, !isAdvertising else { return }
        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [RecoveryUUID.service]
        ])
    }

    private func consume(_ frame: Data) {
        let message: Data
        switch reassembler.append(frame) {
        case .pending:
            return
        case .complete(let completeMessage):
            message = completeMessage
        case .invalid:
            protocolNotice = "Ignored invalid BLE telemetry frame"
            return
        }
        do {
            let decoded = try JSONDecoder().decode(RocketTelemetry.self, from: message)
            guard decoded.v == 1, decoded.type == "rocket_telemetry" else {
                protocolNotice = "Ignored unsupported recovery message"
                return
            }
            telemetry = decoded
            telemetryReceivedAt = Date()
            protocolNotice = nil
            refreshMapRegion()
        } catch {
            protocolNotice = "Ignored invalid telemetry message"
        }
    }

    private func sendPhoneLocation(_ location: CLLocation) {
        guard foreground, isSubscribed, outboundFrames.isEmpty,
              location.horizontalAccuracy >= 0,
              CLLocationCoordinate2DIsValid(location.coordinate) else { return }

        let message = PhoneLocationMessage(
            timestamp: Int64((location.timestamp.timeIntervalSince1970 * 1000).rounded()),
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            altitudeM: location.altitude,
            accuracyM: location.horizontalAccuracy
        )

        do {
            let bytes = try JSONEncoder().encode(message)
            outboundFrames = try FrameCodec.makeFrames(for: bytes, sequence: outboundSequence)
            outboundSequence &+= 1
            drainOutboundFrames()
        } catch {
            protocolNotice = "Unable to frame phone location"
        }
    }

    private func drainOutboundFrames() {
        guard foreground, isSubscribed else {
            outboundFrames.removeAll()
            return
        }
        while let frame = outboundFrames.first {
            guard peripheralManager.updateValue(frame, for: phoneToDesktopCharacteristic, onSubscribedCentrals: nil) else {
                return
            }
            outboundFrames.removeFirst()
        }
    }

    private var phoneToDesktopCharacteristic: CBMutableCharacteristic {
        guard let characteristic = recoveryService?.characteristics?.first(where: {
            $0.uuid == RecoveryUUID.phoneToDesktop
        }) as? CBMutableCharacteristic else {
            return CBMutableCharacteristic(
                type: RecoveryUUID.phoneToDesktop,
                properties: [.notify],
                value: nil,
                permissions: []
            )
        }
        return characteristic
    }

    private var recoveryService: CBMutableService? {
        peripheralManager.services?.first(where: { $0.uuid == RecoveryUUID.service }) as? CBMutableService
    }

    private func createService() {
        guard !serviceAdded, !serviceAddRequested else { return }
        let service = CBMutableService(type: RecoveryUUID.service, primary: true)
        let desktopToPhone = CBMutableCharacteristic(
            type: RecoveryUUID.desktopToPhone,
            properties: [.write],
            value: nil,
            permissions: [.writeable]
        )
        let phoneToDesktop = CBMutableCharacteristic(
            type: RecoveryUUID.phoneToDesktop,
            properties: [.notify],
            value: nil,
            permissions: []
        )
        service.characteristics = [desktopToPhone, phoneToDesktop]
        serviceAddRequested = true
        peripheralManager.add(service)
    }
}

extension RecoveryManager: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            bluetoothState = "Bluetooth ready"
            createService()
            startAdvertisingIfReady()
        case .poweredOff:
            bluetoothState = "Bluetooth is off"
            isAdvertising = false
            isSubscribed = false
            serviceAdded = false
            serviceAddRequested = false
        case .unauthorized:
            bluetoothState = "Bluetooth permission denied"
            isAdvertising = false
        case .unsupported:
            bluetoothState = "Bluetooth is unavailable"
            isAdvertising = false
        case .resetting:
            bluetoothState = "Bluetooth is resetting"
            isAdvertising = false
        case .unknown:
            bluetoothState = "Bluetooth state unknown"
            isAdvertising = false
        @unknown default:
            bluetoothState = "Bluetooth unavailable"
            isAdvertising = false
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        guard service.uuid == RecoveryUUID.service else { return }
        if let error {
            serviceAdded = false
            serviceAddRequested = false
            bluetoothState = "Could not create recovery service: \(error.localizedDescription)"
            return
        }
        serviceAddRequested = false
        serviceAdded = true
        startAdvertisingIfReady()
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error {
            isAdvertising = false
            bluetoothState = "Advertising failed: \(error.localizedDescription)"
        } else if foreground {
            isAdvertising = true
        } else {
            peripheral.stopAdvertising()
            isAdvertising = false
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        guard characteristic.uuid == RecoveryUUID.phoneToDesktop else { return }
        isSubscribed = true
        if let phoneLocation {
            sendPhoneLocation(phoneLocation)
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        guard characteristic.uuid == RecoveryUUID.phoneToDesktop else { return }
        isSubscribed = false
        outboundFrames.removeAll()
    }

    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        drainOutboundFrames()
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            guard request.characteristic.uuid == RecoveryUUID.desktopToPhone,
                  let value = request.value else {
                peripheral.respond(to: request, withResult: .requestNotSupported)
                continue
            }
            consume(value)
            peripheral.respond(to: request, withResult: .success)
        }
    }
}

extension RecoveryManager: CLLocationManagerDelegate {
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        updateLocationAuthorization()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last,
              location.horizontalAccuracy >= 0,
              CLLocationCoordinate2DIsValid(location.coordinate) else { return }
        phoneLocation = location
        locationStatus = "Location fix available"
        refreshMapRegion()
        sendPhoneLocation(location)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        locationStatus = "Location error: \(error.localizedDescription)"
    }
}
