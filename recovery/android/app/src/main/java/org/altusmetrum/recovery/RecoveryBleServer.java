package org.altusmetrum.recovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class RecoveryBleServer {
    interface Listener {
        void onStatus(String status);
        void onAdvertisingChanged(boolean advertising);
        void onSubscriptionChanged(boolean subscribed);
        void onTelemetry(JSONObject telemetry, long receivedAtMillis);
    }

    private static final UUID SERVICE_UUID = UUID.fromString("D4A10000-3B7A-4C5F-9A13-6C7E4B201000");
    private static final UUID DESKTOP_TO_PHONE_UUID = UUID.fromString("D4A10001-3B7A-4C5F-9A13-6C7E4B201000");
    private static final UUID PHONE_TO_DESKTOP_UUID = UUID.fromString("D4A10002-3B7A-4C5F-9A13-6C7E4B201000");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final Listener listener;
    private final BluetoothManager bluetoothManager;
    private final FrameCodec.Reassembler reassembler = new FrameCodec.Reassembler();
    private final ArrayDeque<byte[]> outboundFrames = new ArrayDeque<>();
    private final BluetoothGattServerCallback gattCallback = new GattCallback();
    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer server;
    private BluetoothGattCharacteristic phoneToDesktop;
    private boolean serviceReady;
    private BluetoothDevice subscriber;
    private byte[] latestLocation;
    private int outboundSequence;
    private boolean foreground;
    private boolean waitingForNotification;

    RecoveryBleServer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    void start() {
        foreground = true;
        try {
            if (bluetoothManager == null) {
                listener.onStatus("Bluetooth service is unavailable.");
                return;
            }
            adapter = bluetoothManager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                listener.onStatus("Bluetooth is off or unavailable.");
                return;
            }
            advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                listener.onStatus("This device cannot advertise a BLE recovery service.");
                return;
            }
            if (server == null) {
                server = bluetoothManager.openGattServer(context, gattCallback);
                if (server == null) {
                    listener.onStatus("Could not open the Bluetooth GATT server.");
                    return;
                }
                if (!server.addService(createService())) {
                    listener.onStatus("Could not add the recovery BLE service.");
                    closeServer();
                }
            } else if (serviceReady) {
                startAdvertising();
            }
        } catch (SecurityException exception) {
            listener.onStatus("Bluetooth permission is required: " + exception.getMessage());
        } catch (RuntimeException exception) {
            listener.onStatus("Could not start Bluetooth: " + exception.getMessage());
        }
    }

    void stop() {
        foreground = false;
        outboundFrames.clear();
        waitingForNotification = false;
        subscriber = null;
        listener.onSubscriptionChanged(false);
        stopAdvertising();
        closeServer();
        listener.onAdvertisingChanged(false);
    }

    void sendLocation(double latitude, double longitude, double altitudeM, double accuracyM, long timestampMillis) {
        try {
            JSONObject message = new JSONObject();
            message.put("v", 1);
            message.put("type", "phone_location");
            message.put("timestamp", timestampMillis);
            message.put("latitude", latitude);
            message.put("longitude", longitude);
            message.put("altitudeM", altitudeM);
            message.put("accuracyM", accuracyM);
            latestLocation = message.toString().getBytes(StandardCharsets.UTF_8);
            if (foreground && subscriber != null && outboundFrames.isEmpty()) {
                enqueueLocation();
            }
        } catch (org.json.JSONException exception) {
            listener.onStatus("Could not encode phone location: " + exception.getMessage());
        }
    }

    private BluetoothGattService createService() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic desktopToPhone = new BluetoothGattCharacteristic(
                DESKTOP_TO_PHONE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic phoneToDesktop = new BluetoothGattCharacteristic(
                PHONE_TO_DESKTOP_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        phoneToDesktop.addDescriptor(new BluetoothGattDescriptor(
                CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        this.phoneToDesktop = phoneToDesktop;
        service.addCharacteristic(desktopToPhone);
        service.addCharacteristic(phoneToDesktop);
        return service;
    }

    private void startAdvertising() {
        if (!foreground || advertiser == null || server == null) {
            return;
        }
        try {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                    .setIncludeDeviceName(false)
                    .build();
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException exception) {
            listener.onStatus("Bluetooth advertising permission is required: " + exception.getMessage());
        } catch (RuntimeException exception) {
            listener.onStatus("Could not start BLE advertising: " + exception.getMessage());
        }
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            if (foreground) {
                listener.onAdvertisingChanged(true);
                listener.onStatus("Bluetooth ready");
            } else {
                stopAdvertising();
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            listener.onAdvertisingChanged(false);
            listener.onStatus(advertisingError(errorCode));
        }
    };

    private String advertisingError(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "BLE advertising is already running.";
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "BLE recovery service advertisement is too large.";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "BLE advertising is not supported on this device.";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "Android reported an internal BLE advertising error.";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "No BLE advertising slot is available.";
            default:
                return "BLE advertising failed (code " + errorCode + ").";
        }
    }

    private void stopAdvertising() {
        if (advertiser == null) {
            return;
        }
        try {
            advertiser.stopAdvertising(advertiseCallback);
        } catch (SecurityException exception) {
            listener.onStatus("Could not stop BLE advertising: " + exception.getMessage());
        }
    }

    private void closeServer() {
        if (server != null) {
            try {
                server.close();
            } catch (SecurityException exception) {
                listener.onStatus("Could not close the Bluetooth GATT server: " + exception.getMessage());
            }
            server = null;
        }
        serviceReady = false;
    }

    private void enqueueLocation() {
        if (latestLocation == null || subscriber == null) {
            return;
        }
        try {
            List<byte[]> frames = FrameCodec.makeFrames(latestLocation, outboundSequence);
            outboundSequence = (outboundSequence + 1) & 0xffff;
            outboundFrames.addAll(frames);
            sendNextFrame();
        } catch (FrameCodec.FrameException exception) {
            listener.onStatus("Could not frame phone location: " + exception.getMessage());
        }
    }

    private void sendNextFrame() {
        if (!foreground || server == null || subscriber == null || waitingForNotification || outboundFrames.isEmpty()) {
            return;
        }
        byte[] frame = outboundFrames.peek();
        try {
            int result;
            if (Build.VERSION.SDK_INT >= 33) {
                result = server.notifyCharacteristicChanged(subscriber, phoneToDesktop, false, frame);
            } else {
                phoneToDesktop.setValue(frame);
                result = server.notifyCharacteristicChanged(subscriber, phoneToDesktop, false);
            }
            if (result != BluetoothGatt.GATT_SUCCESS) {
                outboundFrames.clear();
                listener.onStatus("Android could not send a BLE location notification (status " + result + ").");
                return;
            }
            waitingForNotification = true;
        } catch (SecurityException exception) {
            outboundFrames.clear();
            listener.onStatus("Bluetooth permission is required to send location: " + exception.getMessage());
        }
    }

    private void consumeFrame(byte[] frame) {
        FrameCodec.Result result = reassembler.append(frame, System.currentTimeMillis());
        if (result.state == FrameCodec.Result.State.PENDING) {
            return;
        }
        if (result.state == FrameCodec.Result.State.INVALID) {
            listener.onStatus("Ignored invalid BLE telemetry frame.");
            return;
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(result.message)).toString();
            JSONObject message = new JSONObject(json);
            Object version = message.opt("v");
            Object type = message.opt("type");
            Object timestamp = message.opt("timestamp");
            if (!(version instanceof Number) || ((Number) version).intValue() != 1
                    || !"rocket_telemetry".equals(type) || !(timestamp instanceof Number)) {
                listener.onStatus("Ignored unsupported recovery telemetry.");
                return;
            }
            listener.onTelemetry(message, System.currentTimeMillis());
        } catch (CharacterCodingException | org.json.JSONException exception) {
            listener.onStatus("Ignored invalid telemetry message.");
        }
    }

    private final class GattCallback extends BluetoothGattServerCallback {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onStatus("Could not add recovery GATT service (status " + status + ").");
                closeServer();
                return;
            }
            serviceReady = true;
            startAdvertising();
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED && device.equals(subscriber)) {
                subscriber = null;
                outboundFrames.clear();
                waitingForNotification = false;
                listener.onSubscriptionChanged(false);
                listener.onStatus("Desktop disconnected.");
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onStatus("Desktop connected; waiting for telemetry subscription.");
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onStatus("BLE connection changed with status " + status + ".");
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean preparedWrite,
                                             boolean responseNeeded, int offset, byte[] value) {
            int status = BluetoothGatt.GATT_SUCCESS;
            boolean enable = value != null && java.util.Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean disable = value != null && java.util.Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            if (descriptor.getUuid().equals(CCCD_UUID) && descriptor.getCharacteristic().getUuid().equals(PHONE_TO_DESKTOP_UUID)
                    && !preparedWrite && offset == 0 && (enable || disable)) {
                descriptor.setValue(value);
                subscriber = enable ? device : null;
                outboundFrames.clear();
                waitingForNotification = false;
                listener.onSubscriptionChanged(enable);
                if (enable) {
                    listener.onStatus("Desktop subscribed to recovery updates.");
                    enqueueLocation();
                } else {
                    listener.onStatus("Desktop unsubscribed from recovery updates.");
                }
            } else {
                status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            }
            if (responseNeeded && server != null) {
                server.sendResponse(device, requestId, status, offset, value);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            int status = BluetoothGatt.GATT_SUCCESS;
            if (!characteristic.getUuid().equals(DESKTOP_TO_PHONE_UUID) || preparedWrite || offset != 0 || value == null) {
                status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            } else {
                consumeFrame(value);
            }
            if (responseNeeded && server != null) {
                server.sendResponse(device, requestId, status, offset, value);
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            waitingForNotification = false;
            if (!outboundFrames.isEmpty()) {
                outboundFrames.remove();
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                outboundFrames.clear();
                listener.onStatus("BLE location notification failed (status " + status + ").");
                return;
            }
            sendNextFrame();
        }
    }
}
