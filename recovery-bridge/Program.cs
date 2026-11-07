using System.Globalization;
using System.Text;
using System.Text.Json;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Storage.Streams;

internal static class Program
{
    private static readonly Guid ServiceId = Guid.Parse("D4A10000-3B7A-4C5F-9A13-6C7E4B201000");
    private static readonly Guid DesktopToPhoneId = Guid.Parse("D4A10001-3B7A-4C5F-9A13-6C7E4B201000");
    private static readonly Guid PhoneToDesktopId = Guid.Parse("D4A10002-3B7A-4C5F-9A13-6C7E4B201000");
    private static readonly object OutputLock = new();

    private static async Task<int> Main()
    {
        Console.OutputEncoding = new UTF8Encoding(false);
        try
        {
            await RunAsync();
            return 0;
        }
        catch (Exception exception)
        {
            WriteStatus("ERROR", exception.Message);
            Console.Error.WriteLine(exception);
            return 1;
        }
    }

    private static async Task RunAsync()
    {
        WriteStatus("SCANNING", "Open the iPhone recovery app and keep it in the foreground.");
        ulong address = await FindPhoneAsync();

        using BluetoothLEDevice device = await BluetoothLEDevice.FromBluetoothAddressAsync(address)
            ?? throw new InvalidOperationException("Windows could not open the discovered iPhone.");
        device.ConnectionStatusChanged += (sender, _) =>
        {
            if (sender.ConnectionStatus == BluetoothConnectionStatus.Disconnected)
                WriteStatus("DISCONNECTED", "The iPhone BLE connection was lost.");
        };

        GattDeviceServicesResult services = await device.GetGattServicesForUuidAsync(
            ServiceId, BluetoothCacheMode.Uncached);
        if (services.Status != GattCommunicationStatus.Success || services.Services.Count == 0)
            throw new InvalidOperationException("The iPhone did not expose the recovery BLE service.");

        GattCharacteristicsResult outgoingResult = await services.Services[0]
            .GetCharacteristicsForUuidAsync(DesktopToPhoneId, BluetoothCacheMode.Uncached);
        GattCharacteristicsResult incomingResult = await services.Services[0]
            .GetCharacteristicsForUuidAsync(PhoneToDesktopId, BluetoothCacheMode.Uncached);
        if (outgoingResult.Status != GattCommunicationStatus.Success || outgoingResult.Characteristics.Count == 0 ||
            incomingResult.Status != GattCommunicationStatus.Success || incomingResult.Characteristics.Count == 0)
            throw new InvalidOperationException("The iPhone recovery service is missing a required characteristic.");

        GattCharacteristic outgoing = outgoingResult.Characteristics[0];
        GattCharacteristic incoming = incomingResult.Characteristics[0];
        GattCommunicationStatus subscription = await incoming
            .WriteClientCharacteristicConfigurationDescriptorAsync(
                GattClientCharacteristicConfigurationDescriptorValue.Notify);
        if (subscription != GattCommunicationStatus.Success)
            throw new InvalidOperationException("Windows could not subscribe to iPhone location updates.");

        var assembler = new MessageAssembler();
        ushort sequence = 0;
        incoming.ValueChanged += (_, eventArgs) =>
        {
            try
            {
                var reader = DataReader.FromBuffer(eventArgs.CharacteristicValue);
                byte[] frame = new byte[checked((int)reader.UnconsumedBufferLength)];
                reader.ReadBytes(frame);
                byte[]? message = assembler.Add(frame);
                if (message != null)
                    HandlePhoneMessage(message);
            }
            catch (Exception exception)
            {
                WriteStatus("ERROR", "Invalid phone message: " + exception.Message);
            }
        };

        WriteStatus("CONNECTED", string.IsNullOrWhiteSpace(device.Name) ? "iPhone connected" : device.Name);
        while (true)
        {
            string? line = await Console.In.ReadLineAsync();
            if (line == null || line == "QUIT")
                break;
            if (!line.StartsWith("ROCKET\t", StringComparison.Ordinal))
            {
                WriteStatus("ERROR", "Unsupported desktop bridge message.");
                continue;
            }

            byte[] messageBytes = Encoding.UTF8.GetBytes(line.Substring("ROCKET\t".Length));
            ValidateRocketMessage(messageBytes);
            foreach (byte[] frame in CreateFrames(messageBytes, ref sequence))
            {
                using var writer = new DataWriter();
                writer.WriteBytes(frame);
                GattCommunicationStatus writeStatus = await outgoing.WriteValueAsync(
                    writer.DetachBuffer(), GattWriteOption.WriteWithResponse);
                if (writeStatus != GattCommunicationStatus.Success)
                    throw new IOException("Windows could not write rocket telemetry to the iPhone.");
            }
        }
    }

    private static async Task<ulong> FindPhoneAsync()
    {
        var completion = new TaskCompletionSource<ulong>(
            TaskCreationOptions.RunContinuationsAsynchronously);
        var filter = new BluetoothLEAdvertisementFilter();
        filter.Advertisement.ServiceUuids.Add(ServiceId);
        var watcher = new BluetoothLEAdvertisementWatcher
        {
            AdvertisementFilter = filter,
            ScanningMode = BluetoothLEScanningMode.Active
        };
        watcher.Received += (_, eventArgs) =>
        {
            if (eventArgs.Advertisement.ServiceUuids.Contains(ServiceId))
                completion.TrySetResult(eventArgs.BluetoothAddress);
        };

        watcher.Start();
        try
        {
            Task completed = await Task.WhenAny(completion.Task, Task.Delay(TimeSpan.FromMinutes(2)));
            if (completed != completion.Task)
                throw new TimeoutException("No iPhone recovery app was found within two minutes.");
            return await completion.Task;
        }
        finally
        {
            watcher.Stop();
        }
    }

    private static void HandlePhoneMessage(byte[] bytes)
    {
        using JsonDocument document = JsonDocument.Parse(bytes);
        JsonElement root = document.RootElement;
        if (!root.TryGetProperty("v", out JsonElement version) || version.GetInt32() != 1 ||
            !root.TryGetProperty("type", out JsonElement type) ||
            type.GetString() != "phone_location")
            throw new InvalidDataException("Only version 1 phone_location messages are supported.");

        double latitude = RequiredDouble(root, "latitude");
        double longitude = RequiredDouble(root, "longitude");
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new InvalidDataException("Phone coordinates are outside valid WGS 84 ranges.");

        string altitude = OptionalDouble(root, "altitudeM");
        string accuracy = OptionalDouble(root, "accuracyM");
        long timestamp = root.GetProperty("timestamp").GetInt64();
        WriteLine(string.Join("\t", "PHONE",
            Format(latitude), Format(longitude), altitude, accuracy,
            timestamp.ToString(CultureInfo.InvariantCulture)));
    }

    private static void ValidateRocketMessage(byte[] bytes)
    {
        if (bytes.Length == 0 || bytes.Length > 4080)
            throw new InvalidDataException("Rocket telemetry must be between 1 and 4080 UTF-8 bytes.");
        using JsonDocument document = JsonDocument.Parse(bytes);
        JsonElement root = document.RootElement;
        if (!root.TryGetProperty("v", out JsonElement version) || version.GetInt32() != 1 ||
            !root.TryGetProperty("type", out JsonElement type) ||
            type.GetString() != "rocket_telemetry")
            throw new InvalidDataException("Only version 1 rocket_telemetry messages are supported.");
    }

    private static double RequiredDouble(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out JsonElement value) || !value.TryGetDouble(out double result) ||
            !double.IsFinite(result))
            throw new InvalidDataException("Phone location is missing a valid " + name + ".");
        return result;
    }

    private static string OptionalDouble(JsonElement root, string name)
    {
        return root.TryGetProperty(name, out JsonElement value) && value.TryGetDouble(out double result) &&
            double.IsFinite(result) ? Format(result) : "";
    }

    private static string Format(double value) => value.ToString("R", CultureInfo.InvariantCulture);

    private static List<byte[]> CreateFrames(byte[] message, ref ushort sequence)
    {
        int count = Math.Max(1, (message.Length + 15) / 16);
        if (count > byte.MaxValue)
            throw new InvalidDataException("BLE message exceeds the frame count limit.");

        var frames = new List<byte[]>(count);
        ushort currentSequence = sequence++;
        for (int index = 0; index < count; index++)
        {
            int offset = index * 16;
            int length = Math.Min(16, message.Length - offset);
            var frame = new byte[4 + length];
            frame[0] = (byte)(currentSequence & 0xff);
            frame[1] = (byte)(currentSequence >> 8);
            frame[2] = (byte)index;
            frame[3] = (byte)count;
            if (length > 0)
                System.Buffer.BlockCopy(message, offset, frame, 4, length);
            frames.Add(frame);
        }
        return frames;
    }

    private static void WriteStatus(string state, string detail) =>
        WriteLine("STATUS\t" + state + "\t" + detail.Replace('\t', ' ').Replace('\r', ' ').Replace('\n', ' '));

    private static void WriteLine(string line)
    {
        lock (OutputLock)
        {
            Console.WriteLine(line);
            Console.Out.Flush();
        }
    }

    private sealed class MessageAssembler
    {
        private ushort? _sequence;
        private byte _count;
        private readonly byte[][] _parts = new byte[byte.MaxValue][];
        private DateTime _expires;

        public byte[]? Add(byte[] frame)
        {
            if (frame.Length < 4)
            {
                Reset();
                throw new InvalidDataException("BLE frame is shorter than its header.");
            }

            DateTime now = DateTime.UtcNow;
            if (_sequence.HasValue && now > _expires)
                Reset();

            ushort sequence = (ushort)(frame[0] | frame[1] << 8);
            byte index = frame[2];
            byte count = frame[3];
            if (count == 0 || index >= count)
            {
                Reset();
                throw new InvalidDataException("BLE frame index or count is invalid.");
            }

            if (_sequence != sequence)
            {
                Reset();
                if (index != 0)
                    throw new InvalidDataException("A new BLE message did not start with fragment zero.");
                _sequence = sequence;
                _count = count;
                _expires = now.AddSeconds(10);
            }
            if (_count != count)
            {
                Reset();
                throw new InvalidDataException("BLE fragments have inconsistent counts.");
            }

            _parts[index] = frame.Skip(4).ToArray();
            for (int part = 0; part < count; part++)
                if (_parts[part] == null)
                    return null;

            int length = 0;
            for (int part = 0; part < count; part++)
                length += _parts[part].Length;
            if (length > 4096)
            {
                Reset();
                throw new InvalidDataException("Reassembled BLE message exceeds 4096 bytes.");
            }

            var message = new byte[length];
            int offset = 0;
            for (int part = 0; part < count; part++)
            {
                System.Buffer.BlockCopy(_parts[part], 0, message, offset, _parts[part].Length);
                offset += _parts[part].Length;
            }
            Reset();
            return message;
        }

        private void Reset()
        {
            Array.Clear(_parts, 0, _parts.Length);
            _sequence = null;
            _count = 0;
        }
    }
}
