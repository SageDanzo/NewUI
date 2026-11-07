package org.altusmetrum.recovery;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FrameCodec {
    static final int HEADER_LENGTH = 4;
    static final int PAYLOAD_LENGTH = 16;
    static final int MAXIMUM_MESSAGE_LENGTH = 4096;

    private FrameCodec() {
    }

    static List<byte[]> makeFrames(byte[] message, int sequence) throws FrameException {
        if (message.length > MAXIMUM_MESSAGE_LENGTH) {
            throw new FrameException("Recovery message exceeds 4096 bytes.");
        }

        int fragmentCount = Math.max(1, (message.length + PAYLOAD_LENGTH - 1) / PAYLOAD_LENGTH);
        if (fragmentCount > 255) {
            throw new FrameException("Recovery message requires too many BLE fragments.");
        }

        List<byte[]> frames = new ArrayList<>(fragmentCount);
        for (int index = 0; index < fragmentCount; index++) {
            int start = index * PAYLOAD_LENGTH;
            int end = Math.min(start + PAYLOAD_LENGTH, message.length);
            byte[] frame = new byte[HEADER_LENGTH + end - start];
            frame[0] = (byte) (sequence & 0xff);
            frame[1] = (byte) ((sequence >>> 8) & 0xff);
            frame[2] = (byte) index;
            frame[3] = (byte) fragmentCount;
            System.arraycopy(message, start, frame, HEADER_LENGTH, end - start);
            frames.add(frame);
        }
        return Collections.unmodifiableList(frames);
    }

    static final class FrameException extends Exception {
        FrameException(String message) {
            super(message);
        }
    }

    static final class Reassembler {
        private int sequence = -1;
        private int fragmentCount;
        private int nextIndex;
        private long startedAt;
        private ByteArrayOutputStream bytes;

        Result append(byte[] frame, long nowMillis) {
            if (bytes != null && nowMillis - startedAt >= 10_000) {
                reset();
            }
            if (frame == null || frame.length < HEADER_LENGTH ||
                    frame.length > HEADER_LENGTH + PAYLOAD_LENGTH) {
                reset();
                return Result.invalid();
            }

            int incomingSequence = (frame[0] & 0xff) | ((frame[1] & 0xff) << 8);
            int index = frame[2] & 0xff;
            int count = frame[3] & 0xff;
            if (count == 0 || index >= count) {
                reset();
                return Result.invalid();
            }

            if (sequence != incomingSequence) {
                if (index != 0) {
                    reset();
                    return Result.invalid();
                }
                sequence = incomingSequence;
                fragmentCount = count;
                nextIndex = 0;
                startedAt = nowMillis;
                bytes = new ByteArrayOutputStream();
            }

            if (bytes == null || fragmentCount != count || nextIndex != index) {
                reset();
                return Result.invalid();
            }

            bytes.write(frame, HEADER_LENGTH, frame.length - HEADER_LENGTH);
            if (bytes.size() > MAXIMUM_MESSAGE_LENGTH) {
                reset();
                return Result.invalid();
            }

            if (index + 1 == count) {
                byte[] message = bytes.toByteArray();
                reset();
                return Result.complete(message);
            }
            nextIndex++;
            return Result.pending();
        }

        private void reset() {
            sequence = -1;
            fragmentCount = 0;
            nextIndex = 0;
            bytes = null;
        }
    }

    static final class Result {
        enum State { PENDING, COMPLETE, INVALID }

        final State state;
        final byte[] message;

        private Result(State state, byte[] message) {
            this.state = state;
            this.message = message;
        }

        static Result pending() {
            return new Result(State.PENDING, null);
        }

        static Result complete(byte[] message) {
            return new Result(State.COMPLETE, message);
        }

        static Result invalid() {
            return new Result(State.INVALID, null);
        }
    }
}
