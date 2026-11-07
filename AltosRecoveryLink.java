package altosui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.altusmetrum.altoslib_14.AltosGPS;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;

/** Recovery-only IPC client for the Windows BLE bridge. */
final class AltosRecoveryLink {
	interface Listener {
		void status(String state, String detail);
		void phoneLocation(String[] fields);
	}

	private final Listener listener;
	private volatile Process process;
	private BufferedWriter input;
	private boolean connected;
	private volatile boolean stopping;
	private String latestTelemetry;
	private long lastTelemetrySentAt;

	AltosRecoveryLink(Listener listener) {
		this.listener = listener;
	}

	synchronized boolean isRunning() {
		return process != null && process.isAlive();
	}

	synchronized void start() {
		if (isRunning())
			return;
		try {
			File helper = findHelper();
			if (!helper.isFile())
				throw new IllegalStateException("BLE bridge not found at " + helper.getAbsolutePath()
					+ ". Build it with recovery-bridge\\Build-RecoveryBridge.bat.");
			process = new ProcessBuilder(helper.getAbsolutePath()).start();
			input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
			connected = false;
			stopping = false;
			lastTelemetrySentAt = 0;
			readOutput(process);
			readErrors(process);
			listener.status("SCANNING", "Open the iPhone app and keep it in the foreground.");
		} catch (Exception exception) {
			process = null;
			input = null;
			connected = false;
			listener.status("ERROR", exception.getMessage() == null ? exception.toString() : exception.getMessage());
		}
	}

	synchronized void stop() {
		Process child = process;
		if (child == null)
			return;
		stopping = true;
		connected = false;
		try {
			if (input != null)
				input.close();
		} catch (Exception exception) {
			listener.status("ERROR", "Could not stop BLE bridge: " + exception.getMessage());
		} finally {
			child.destroyForcibly();
			process = null;
			input = null;
			listener.status("DISCONNECTED", "Recovery link stopped.");
		}
	}

	synchronized void sendState(AltosState state, int serial) {
		latestTelemetry = telemetryJson(state, serial);
		if (connected)
			writeLatest();
	}

	private void writeLatest() {
		if (input == null || latestTelemetry == null)
			return;
		long now = System.currentTimeMillis();
		if (connected && now - lastTelemetrySentAt < 1000)
			return;
		try {
			input.write("ROCKET\t");
			input.write(latestTelemetry);
			input.newLine();
			input.flush();
			lastTelemetrySentAt = now;
		} catch (Exception exception) {
			connected = false;
			listener.status("ERROR", "Could not send telemetry to BLE bridge: " + exception.getMessage());
		}
	}

	private void readOutput(final Process child) {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(
						child.getInputStream(), StandardCharsets.UTF_8));
					String line;
					while ((line = reader.readLine()) != null)
						handleLine(line);
					if (!stopping && child == process) {
						synchronized (AltosRecoveryLink.this) {
							connected = false;
						}
						listener.status("DISCONNECTED", "BLE bridge closed its output.");
					}
				} catch (Exception exception) {
					if (!stopping && child == process)
						listener.status("ERROR", "Could not read BLE bridge output: " + exception.getMessage());
				}
			}
		}, "AltOS-BLE-output");
		thread.setDaemon(true);
		thread.start();
	}

	private void readErrors(final Process child) {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(
						child.getErrorStream(), StandardCharsets.UTF_8));
					String line;
					while ((line = reader.readLine()) != null)
						if (child == process)
							listener.status("ERROR", line);
				} catch (Exception exception) {
					if (!stopping && child == process)
						listener.status("ERROR", "Could not read BLE bridge diagnostics: " + exception.getMessage());
				}
			}
		}, "AltOS-BLE-errors");
		thread.setDaemon(true);
		thread.start();
	}

	private void handleLine(String line) {
		String[] fields = line.split("\t", 3);
		if (fields.length == 0)
			return;
		if ("STATUS".equals(fields[0]) && fields.length == 3) {
			synchronized (this) {
				connected = "CONNECTED".equals(fields[1]);
				listener.status(fields[1], fields[2]);
				if (connected)
					writeLatest();
			}
		} else if ("PHONE".equals(fields[0])) {
			String[] values = line.split("\t", -1);
			if (values.length == 6)
				listener.phoneLocation(values);
			else
				listener.status("ERROR", "BLE bridge returned an invalid phone location.");
		} else if ("ERROR".equals(fields[0])) {
			listener.status("ERROR", fields.length > 1 ? fields[1] : "BLE bridge error.");
		} else {
			listener.status("ERROR", "BLE bridge returned an unknown message.");
		}
	}

	private File findHelper() throws Exception {
		String configured = System.getProperty("altos.recovery.bridge");
		if (configured != null && !configured.trim().isEmpty())
			return new File(configured);
		URI location = AltosRecoveryLink.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		File appDirectory = new File(location).isDirectory() ? new File(location) : new File(location).getParentFile();
		return new File(appDirectory, "recovery-bridge\\AltosRecoveryBridge.exe");
	}

	private static String telemetryJson(AltosState state, int serial) {
		StringBuilder json = new StringBuilder(700);
		json.append("{\"v\":1,\"type\":\"rocket_telemetry\",\"timestamp\":")
			.append(System.currentTimeMillis())
			.append(",\"serial\":").append(serial)
			.append(",\"flightState\":").append(state.status)
			.append(",\"ascent\":").append(state.ascent)
			.append(",\"boost\":").append(state.boost)
			.append(",\"landed\":").append(state.landed);

		AltosGPS gps = state.gps;
		json.append(",\"gpsLocked\":").append(gps != null && gps.locked);
		if (gps != null && gps.locked) {
			number(json, "latitude", gps.lat);
			number(json, "longitude", gps.lon);
			number(json, "gpsAltitudeM", state.gps_altitude());
			json.append(",\"satellites\":").append(gps.nsat);
		}
		number(json, "altitudeM", state.altitude());
		number(json, "heightM", state.height());
		number(json, "speedMps", state.speed());
		number(json, "gpsSpeedMps", state.gps_speed());
		number(json, "gpsClimbRateMps", state.gps_ascent_rate());
		number(json, "courseDeg", state.gps_course());
		number(json, "accelerationMps2", state.acceleration());
		number(json, "accelAlongMps2", state.accel_along());
		number(json, "accelAcrossMps2", state.accel_across());
		number(json, "accelThroughMps2", state.accel_through());
		number(json, "gyroRollDps", state.gyro_roll());
		number(json, "gyroPitchDps", state.gyro_pitch());
		number(json, "gyroYawDps", state.gyro_yaw());
		number(json, "pressurePa", state.pressure());
		number(json, "temperatureC", state.temperature);
		number(json, "batteryV", state.battery_voltage);
		number(json, "mainV", state.main_voltage);
		number(json, "pyroV", state.pyro_voltage);
		json.append(",\"rssiDbm\":").append(state.rssi());
		json.append('}');
		return json.toString();
	}

	private static void number(StringBuilder json, String name, double value) {
		json.append(",\"").append(name).append("\":");
		if (Double.isNaN(value) || Double.isInfinite(value) || value == AltosLib.MISSING)
			json.append("null");
		else
			json.append(Double.toString(value));
	}
}
