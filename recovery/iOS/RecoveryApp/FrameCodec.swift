import Foundation

enum FrameCodecError: Error {
    case messageTooLarge
    case tooManyFragments
}

enum FrameCodec {
    static let headerLength = 4
    static let payloadLength = 16
    static let maximumMessageLength = 4096

    static func makeFrames(for message: Data, sequence: UInt16) throws -> [Data] {
        guard message.count <= maximumMessageLength else {
            throw FrameCodecError.messageTooLarge
        }

        let fragmentCount = max(1, (message.count + payloadLength - 1) / payloadLength)
        guard fragmentCount <= Int(UInt8.max) else {
            throw FrameCodecError.tooManyFragments
        }

        return (0..<fragmentCount).map { index in
            let start = index * payloadLength
            let end = min(start + payloadLength, message.count)
            let bytes = start < end ? message.subdata(in: start..<end) : Data()
            return Data([
                UInt8(sequence & 0xff),
                UInt8((sequence >> 8) & 0xff),
                UInt8(index),
                UInt8(fragmentCount)
            ]) + bytes
        }
    }
}

struct FrameReassembler {
    private struct Assembly {
        let sequence: UInt16
        let fragmentCount: UInt8
        var nextIndex: Int
        let startedAt: Date
        var bytes = Data()
    }

    private var assembly: Assembly?
    private let timeout: TimeInterval = 10

    mutating func append(_ frame: Data, now: Date = Date()) -> FrameAppendResult {
        if let current = assembly, now.timeIntervalSince(current.startedAt) >= timeout {
            assembly = nil
        }

        guard frame.count >= FrameCodec.headerLength,
              frame.count <= FrameCodec.headerLength + FrameCodec.payloadLength else {
            assembly = nil
            return .invalid
        }

        let sequence = UInt16(frame[0]) | (UInt16(frame[1]) << 8)
        let index = Int(frame[2])
        let count = frame[3]
        guard count > 0, index < Int(count) else {
            assembly = nil
            return .invalid
        }

        if assembly?.sequence != sequence {
            guard index == 0 else {
                assembly = nil
                return .invalid
            }
            assembly = Assembly(sequence: sequence, fragmentCount: count, nextIndex: 0, startedAt: now)
        }

        guard var current = assembly,
              current.fragmentCount == count,
              current.nextIndex == index else {
            assembly = nil
            return .invalid
        }

        current.bytes.append(frame.dropFirst(FrameCodec.headerLength))
        guard current.bytes.count <= FrameCodec.maximumMessageLength else {
            assembly = nil
            return .invalid
        }

        if index + 1 == Int(count) {
            assembly = nil
            return .complete(current.bytes)
        }

        current.nextIndex += 1
        assembly = current
        return .pending
    }
}

enum FrameAppendResult {
    case pending
    case complete(Data)
    case invalid
}
