# BLE recovery link protocol

This protocol connects the Windows AltOS ground station to a phone recovery
app. The phone advertises a BLE GATT service while the app is open; Windows
scans for and connects to that service.

The link is for recovery information only. It has no characteristic or message
for arming, firing, configuring, or otherwise controlling a flight computer.
Do not treat this link or its displayed data as a substitute for checking the
vehicle and recovery system in person.
The BLE link is not authenticated; only use it in a trusted environment and
confirm that the connected phone and displayed coordinates are the intended
ones.

## GATT service

All UUIDs are custom 128-bit UUIDs:

| Item | UUID | Properties |
|---|---|---|
| Recovery service | `D4A10000-3B7A-4C5F-9A13-6C7E4B201000` | Primary service |
| Desktop to phone | `D4A10001-3B7A-4C5F-9A13-6C7E4B201000` | Write with response |
| Phone to desktop | `D4A10002-3B7A-4C5F-9A13-6C7E4B201000` | Notify |

The phone is the GATT peripheral and advertises the recovery service. The
Windows computer is the GATT central. The phone sends its location by
notifying `Phone to desktop`; Windows sends rocket telemetry by writing
`Desktop to phone`.

## Framing

Messages are UTF-8 JSON. A message is split into BLE characteristic values
with a four-byte header followed by at most 16 message bytes:

| Offset | Size | Meaning |
|---|---:|---|
| 0 | 2 | Message sequence number, unsigned little-endian |
| 2 | 1 | Zero-based fragment index |
| 3 | 1 | Fragment count, 1–255 |
| 4 | 0–16 | UTF-8 message bytes |

Frames from one message must be sent in index order. Sequence numbers increase
independently in each direction and wrap from 65535 to 0. Receivers discard
incomplete messages after 10 seconds, reject inconsistent fragment counts,
and reject reassembled messages larger than 4096 bytes. Do not mix frames from
different messages.

The conservative 20-byte frame size works with the default 23-byte ATT MTU;
implementations must not assume a larger negotiated MTU.

## JSON messages

Every message has protocol version `v: 1` and a string `type`.

### Phone location (`phone_location`)

Sent by the phone when a valid location update is available. All coordinates
are WGS 84 decimal degrees; distances are metres and timestamps are Unix
milliseconds.

```json
{
  "v": 1,
  "type": "phone_location",
  "timestamp": 1793995200000,
  "latitude": 37.421998,
  "longitude": -122.084,
  "altitudeM": 12.4,
  "accuracyM": 5.0
}
```

### Rocket telemetry (`rocket_telemetry`)

Sent by Windows from the latest live AltOS state. Fields whose values are not
available are omitted or `null`. SI units are used. `flightState` is the
AltOS numeric state code; clients should retain/display unknown codes rather
than guessing a phase name. Windows sends the latest snapshot at most once per
second; this is a live recovery view, not a complete flight-recording stream.

```json
{
  "v": 1,
  "type": "rocket_telemetry",
  "timestamp": 1793995200000,
  "serial": 1234,
  "flightState": 4,
  "ascent": false,
  "boost": false,
  "landed": false,
  "gpsLocked": true,
  "latitude": 37.4221,
  "longitude": -122.0839,
  "gpsAltitudeM": 28.1,
  "altitudeM": 25.7,
  "heightM": 24.9,
  "speedMps": 3.2,
  "gpsSpeedMps": 2.8,
  "gpsClimbRateMps": -0.5,
  "courseDeg": 91,
  "accelerationMps2": 1.1,
  "accelAlongMps2": 0.8,
  "accelAcrossMps2": 0.2,
  "accelThroughMps2": 0.5,
  "gyroRollDps": 0.1,
  "gyroPitchDps": 0.2,
  "gyroYawDps": -0.1,
  "pressurePa": 100430,
  "temperatureC": 21.3,
  "batteryV": 4.0,
  "mainV": 3.9,
  "pyroV": 3.8,
  "rssiDbm": -75,
  "satellites": 8
}
```

The phone should present the timestamp and GPS-lock state prominently so that
stale or unqualified positions are not mistaken for current fixes. This
protocol does not define landing prediction or flight-computer commands.
