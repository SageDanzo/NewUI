package org.altusmetrum.recovery;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RecoveryActivity extends Activity implements RecoveryBleServer.Listener {
    private static final int PERMISSION_REQUEST = 100;
    private static final int BACKGROUND = Color.rgb(11, 18, 32);
    private static final int CARD = Color.rgb(22, 32, 49);
    private static final int TEXT = Color.rgb(238, 243, 249);
    private static final int MUTED = Color.rgb(157, 172, 190);
    private static final int ACCENT = Color.rgb(77, 163, 255);
    private static final int GREEN = Color.rgb(82, 205, 143);
    private static final int ORANGE = Color.rgb(255, 181, 71);
    private static final int RED = Color.rgb(255, 105, 105);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            RecoveryActivity.this.onLocationChanged(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            refreshTelemetry();
            handler.postDelayed(this, 1000);
        }
    };

    private RecoveryBleServer bleServer;
    private LocationManager locationManager;
    private LinearLayout content;
    private TextView bluetoothStatus;
    private TextView advertisingStatus;
    private TextView subscriptionStatus;
    private TextView locationStatus;
    private TextView protocolStatus;
    private TextView phoneLocationText;
    private TextView rocketHeader;
    private TextView rocketDetails;
    private TextView rocketMetrics;
    private TextView safetyNotice;
    private Button phoneMapButton;
    private Button rocketMapButton;
    private Location phoneLocation;
    private JSONObject telemetry;
    private long telemetryReceivedAt;
    private boolean permissionRequestInFlight;
    private boolean foreground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        bleServer = new RecoveryBleServer(this, this);
        buildScreen();
    }

    @Override
    protected void onStart() {
        super.onStart();
        foreground = true;
        requestPermissionsIfNeeded();
        handler.post(clockTick);
    }

    @Override
    protected void onStop() {
        foreground = false;
        handler.removeCallbacks(clockTick);
        stopLocationUpdates();
        bleServer.stop();
        super.onStop();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = text("AltOS Recovery Link", 25, TEXT, Typeface.BOLD);
        content.addView(title, params(0, 0, 0, 16));

        LinearLayout statusCard = card("Link status");
        bluetoothStatus = statusLine(statusCard, "Bluetooth is starting…");
        advertisingStatus = statusLine(statusCard, "Recovery service is not advertising");
        subscriptionStatus = statusLine(statusCard, "Waiting for desktop");
        locationStatus = statusLine(statusCard, "Waiting for location permission");
        protocolStatus = text("", 13, ORANGE, Typeface.NORMAL);
        protocolStatus.setVisibility(View.GONE);
        statusCard.addView(protocolStatus, params(0, 8, 0, 0));
        Button retryButton = button("Retry recovery link");
        statusCard.addView(retryButton, params(0, 8, 0, 0));
        retryButton.setOnClickListener(view -> retryRecoveryLink());

        LinearLayout phoneCard = card("Phone GPS");
        phoneLocationText = text("Waiting for a valid location fix.", 15, MUTED, Typeface.NORMAL);
        phoneCard.addView(phoneLocationText, params(0, 4, 0, 10));
        phoneMapButton = button("Open phone location in maps");
        phoneCard.addView(phoneMapButton);

        LinearLayout rocketCard = card("Rocket telemetry");
        rocketHeader = text("No rocket telemetry received.", 16, MUTED, Typeface.BOLD);
        rocketCard.addView(rocketHeader, params(0, 4, 0, 6));
        rocketDetails = text("", 13, MUTED, Typeface.NORMAL);
        rocketCard.addView(rocketDetails, params(0, 0, 0, 8));
        rocketMetrics = text("", 14, TEXT, Typeface.NORMAL);
        rocketMetrics.setLineSpacing(dp(3), 1f);
        rocketCard.addView(rocketMetrics, params(0, 0, 0, 10));
        rocketMapButton = button("Open rocket location in maps");
        rocketCard.addView(rocketMapButton);

        safetyNotice = text(
                "Recovery information only; BLE data is unauthenticated. Verify the phone, coordinates, vehicle, and recovery system in person.",
                13, MUTED, Typeface.NORMAL);
        safetyNotice.setGravity(Gravity.CENTER);
        content.addView(safetyNotice, params(2, 18, 2, 0));

        phoneMapButton.setOnClickListener(view -> openMap(phoneLocation, "Phone"));
        rocketMapButton.setOnClickListener(view -> openMap(rocketLocation(), "Rocket"));
        refreshTelemetry();
        refreshPhoneLocation();
    }

    private LinearLayout card(String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(15));
        card.setBackgroundColor(CARD);
        content.addView(card, params(0, 0, 0, 12));
        TextView title = text(heading, 17, TEXT, Typeface.BOLD);
        card.addView(title, params(0, 0, 0, 7));
        return card;
    }

    private TextView statusLine(LinearLayout parent, String value) {
        TextView line = text("•  " + value, 14, MUTED, Typeface.NORMAL);
        parent.addView(line, params(0, 3, 0, 3));
        return line;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(ACCENT);
        button.setAllCaps(false);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(31, 52, 77)));
        return button;
    }

    private LinearLayout.LayoutParams params(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        result.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return result;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestPermissionsIfNeeded() {
        if (hasRequiredPermissions()) {
            permissionRequestInFlight = false;
            startForegroundServices();
            return;
        }
        if (permissionRequestInFlight) {
            return;
        }
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (!hasLocationPermission()) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!needed.isEmpty()) {
            permissionRequestInFlight = true;
            requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    private boolean hasRequiredPermissions() {
        boolean location = hasLocationPermission();
        if (Build.VERSION.SDK_INT < 31) {
            return location && checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }
        return location
                && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST) {
            return;
        }
        permissionRequestInFlight = false;
        if (hasRequiredPermissions()) {
            startForegroundServices();
        } else {
            setStatus(locationStatus, "Location permission is required for phone GPS.");
            setStatus(bluetoothStatus, "Bluetooth permissions are required for the recovery link.");
        }
    }

    private void startForegroundServices() {
        if (!foreground || !hasRequiredPermissions()) {
            return;
        }
        bleServer.start();
        startLocationUpdates();
    }

    private void retryRecoveryLink() {
        if (!hasRequiredPermissions()) {
            requestPermissionsIfNeeded();
            return;
        }
        bleServer.stop();
        bleServer.start();
    }

    private void startLocationUpdates() {
        if (!hasRequiredPermissions() || locationManager == null) {
            setStatus(locationStatus, "Location is unavailable.");
            return;
        }
        try {
            boolean requested = false;
            setStatus(locationStatus, "Waiting for a location fix…");
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 1000L, 1f, locationListener, Looper.getMainLooper());
                    requested = true;
                    Location last = locationManager.getLastKnownLocation(provider);
                    if (last != null) {
                        onLocationChanged(last);
                    }
                }
            }
            if (!requested) {
                setStatus(locationStatus, "Enable device location to receive GPS.");
            }
        } catch (SecurityException exception) {
            setStatus(locationStatus, "Location permission is required: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            setStatus(locationStatus, "Location provider is unavailable: " + exception.getMessage());
        }
    }

    private void stopLocationUpdates() {
        if (locationManager == null) {
            return;
        }
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException exception) {
            setStatus(locationStatus, "Could not stop location updates: " + exception.getMessage());
        }
    }

    private void onLocationChanged(Location location) {
        if (location == null || !isValidCoordinate(location.getLatitude(), location.getLongitude())
                || location.getAccuracy() < 0) {
            return;
        }
        phoneLocation = location;
        setStatus(locationStatus, "Location fix available");
        refreshPhoneLocation();
        bleServer.sendLocation(location.getLatitude(), location.getLongitude(),
                location.getAltitude(), location.getAccuracy(), location.getTime());
    }

    private boolean isValidCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    private void refreshPhoneLocation() {
        if (phoneLocation == null) {
            phoneLocationText.setText("Waiting for a valid location fix.");
            phoneLocationText.setTextColor(MUTED);
            phoneMapButton.setEnabled(false);
            return;
        }
        phoneLocationText.setText(String.format(Locale.US,
                "%.6f, %.6f\nAccuracy ±%.1f m  •  Altitude %.1f m\nFix: %s",
                phoneLocation.getLatitude(), phoneLocation.getLongitude(), phoneLocation.getAccuracy(),
                phoneLocation.getAltitude(), new Date(phoneLocation.getTime())));
        phoneLocationText.setTextColor(TEXT);
        phoneMapButton.setEnabled(true);
    }

    @Override
    public void onStatus(String status) {
        runOnUiThread(() -> setStatus(bluetoothStatus, status));
    }

    @Override
    public void onAdvertisingChanged(boolean isAdvertising) {
        runOnUiThread(() -> {
            setStatus(advertisingStatus, isAdvertising
                    ? "Advertising recovery service" : "Recovery service is not advertising");
        });
    }

    @Override
    public void onSubscriptionChanged(boolean isSubscribed) {
        runOnUiThread(() -> {
            setStatus(subscriptionStatus, isSubscribed ? "Desktop subscribed" : "Waiting for desktop");
        });
    }

    @Override
    public void onTelemetry(JSONObject message, long receivedAtMillis) {
        runOnUiThread(() -> {
            telemetry = message;
            telemetryReceivedAt = receivedAtMillis;
            if (protocolStatus != null) {
                protocolStatus.setVisibility(View.GONE);
            }
            refreshTelemetry();
        });
    }

    private void setStatus(TextView view, String value) {
        if (view == null) {
            return;
        }
        view.setText("•  " + value);
        view.setTextColor(value.toLowerCase(Locale.ROOT).contains("error")
                || value.toLowerCase(Locale.ROOT).contains("failed") ? RED : MUTED);
    }

    private void refreshTelemetry() {
        if (rocketHeader == null || rocketDetails == null || rocketMetrics == null) {
            return;
        }
        if (telemetry == null) {
            rocketHeader.setText("No rocket telemetry received.");
            rocketHeader.setTextColor(MUTED);
            rocketDetails.setText("");
            rocketMetrics.setText("");
            rocketMapButton.setEnabled(false);
            return;
        }
        long sourceTimestamp = telemetry.optLong("timestamp", 0);
        long now = System.currentTimeMillis();
        double sourceAge = (double) now - (double) sourceTimestamp;
        long receiveAge = Math.max(0, now - telemetryReceivedAt);
        boolean clockSkew = sourceAge < -5000;
        boolean fresh = !clockSkew && sourceTimestamp > 0 && sourceAge <= 10_000 && receiveAge <= 10_000;
        boolean gpsLocked = telemetry.optBoolean("gpsLocked", false);
        String gpsText = !telemetry.has("gpsLocked") || telemetry.isNull("gpsLocked")
                ? "GPS LOCK UNKNOWN" : gpsLocked ? "GPS LOCKED" : "GPS NOT LOCKED";
        rocketHeader.setText(gpsText + "  •  " + (clockSkew ? "CLOCK SKEW" : fresh ? "FRESH" : "STALE"));
        rocketHeader.setTextColor(fresh ? (gpsLocked ? GREEN : ORANGE) : RED);
        rocketDetails.setText(String.format(Locale.US, "Telemetry time: %s\nReceived %ds ago",
                new Date(sourceTimestamp), receiveAge / 1000));

        StringBuilder metrics = new StringBuilder();
        appendMetric(metrics, "Position", coordinate(telemetry));
        appendMetric(metrics, "State code", integer(telemetry, "flightState"));
        appendMetric(metrics, "Altitude", metric(telemetry, "altitudeM", "%.1f m"));
        appendMetric(metrics, "Height", metric(telemetry, "heightM", "%.1f m"));
        appendMetric(metrics, "Speed / GPS speed", metric(telemetry, "speedMps", "%.1f m/s")
                + " / " + metric(telemetry, "gpsSpeedMps", "%.1f m/s"));
        appendMetric(metrics, "GPS climb rate", metric(telemetry, "gpsClimbRateMps", "%.1f m/s"));
        appendMetric(metrics, "Course", metric(telemetry, "courseDeg", "%.1f°"));
        appendMetric(metrics, "Acceleration", metric(telemetry, "accelerationMps2", "%.2f m/s²"));
        appendMetric(metrics, "Accel. along / across / through",
                metric(telemetry, "accelAlongMps2", "%.2f") + " / "
                        + metric(telemetry, "accelAcrossMps2", "%.2f") + " / "
                        + metric(telemetry, "accelThroughMps2", "%.2f") + " m/s²");
        appendMetric(metrics, "Gyro roll / pitch / yaw",
                metric(telemetry, "gyroRollDps", "%.1f") + " / "
                        + metric(telemetry, "gyroPitchDps", "%.1f") + " / "
                        + metric(telemetry, "gyroYawDps", "%.1f") + " °/s");
        appendMetric(metrics, "GPS altitude", metric(telemetry, "gpsAltitudeM", "%.1f m"));
        appendMetric(metrics, "Pressure", metric(telemetry, "pressurePa", "%.0f Pa"));
        appendMetric(metrics, "Temperature", metric(telemetry, "temperatureC", "%.1f °C"));
        appendMetric(metrics, "Battery / main / pyro voltage",
                metric(telemetry, "batteryV", "%.2f") + " / "
                        + metric(telemetry, "mainV", "%.2f") + " / "
                        + metric(telemetry, "pyroV", "%.2f") + " V");
        appendMetric(metrics, "RSSI", integer(telemetry, "rssiDbm") + " dBm");
        appendMetric(metrics, "Satellites", integer(telemetry, "satellites"));
        appendMetric(metrics, "Ascent / boost / landed", flag(telemetry, "ascent") + " / "
                + flag(telemetry, "boost") + " / " + flag(telemetry, "landed"));
        appendMetric(metrics, "Serial", integer(telemetry, "serial"));
        rocketMetrics.setText(metrics.toString());
        rocketMapButton.setEnabled(rocketLocation() != null);
    }

    private void appendMetric(StringBuilder text, String label, String value) {
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(label).append(": ").append(value);
    }

    private String metric(JSONObject object, String key, String format) {
        Object value = object.opt(key);
        if (!(value instanceof Number)) {
            return "—";
        }
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) ? String.format(Locale.US, format, number) : "—";
    }

    private String integer(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof Number ? Long.toString(((Number) value).longValue()) : "—";
    }

    private String flag(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof Boolean ? (((Boolean) value) ? "yes" : "no") : "—";
    }

    private String coordinate(JSONObject object) {
        Object latitude = object.opt("latitude");
        Object longitude = object.opt("longitude");
        if (!(latitude instanceof Number) || !(longitude instanceof Number)) {
            return "unavailable";
        }
        double lat = ((Number) latitude).doubleValue();
        double lon = ((Number) longitude).doubleValue();
        return isValidCoordinate(lat, lon) ? String.format(Locale.US, "%.6f, %.6f", lat, lon) : "unavailable";
    }

    private Location rocketLocation() {
        if (telemetry == null) {
            return null;
        }
        Object latitude = telemetry.opt("latitude");
        Object longitude = telemetry.opt("longitude");
        if (!(latitude instanceof Number) || !(longitude instanceof Number)) {
            return null;
        }
        double lat = ((Number) latitude).doubleValue();
        double lon = ((Number) longitude).doubleValue();
        if (!isValidCoordinate(lat, lon)) {
            return null;
        }
        Location result = new Location("rocket");
        result.setLatitude(lat);
        result.setLongitude(lon);
        return result;
    }

    private void openMap(Location location, String label) {
        if (location == null) {
            return;
        }
        Uri uri = Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
                location.getLatitude(), location.getLongitude(),
                location.getLatitude(), location.getLongitude(), Uri.encode(label)));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException exception) {
            setStatus(protocolStatus, "No maps app is available to open this location.");
            protocolStatus.setVisibility(View.VISIBLE);
        }
    }
}
