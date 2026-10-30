/**
 * MelodyScript Binary MIDI Byte-Writer
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Encodes chronological {@link MidiEvent} sequences into standard binary
 * Standard MIDI File (SMF Format 0) format (.mid).
 * Handles delta-time calculations, Variable-Length Quantity (VLQ) encoding,
 * MThd header chunk generation, and MTrk track chunk serialization.
 */
public class MidiFileWriter {

    // Magic Bytes Constants
    public static final byte[] MTHD_MAGIC = {0x4D, 0x54, 0x68, 0x64}; // "MThd"
    public static final byte[] MTRK_MAGIC = {0x4D, 0x54, 0x72, 0x6B}; // "MTrk"
    public static final int DEFAULT_PPQ = 480;

    private MidiFileWriter() {
        // Utility class
    }

    /**
     * Converts a chronological list of MidiEvents into a complete binary SMF Format 0 byte array.
     *
     * @param events chronological list of MidiEvents
     * @return raw binary MIDI file bytes
     * @throws IOException on byte serialization errors
     */
    public static byte[] generateMidiBytes(List<MidiEvent> events) throws IOException {
        Objects.requireNonNull(events, "events list cannot be null");

        // 1. Serialize Track Events Payload
        ByteArrayOutputStream trackPayload = new ByteArrayOutputStream();
        long lastTick = 0;

        for (MidiEvent event : events) {
            long deltaTick = event.getAbsoluteTick() - lastTick;
            if (deltaTick < 0) {
                deltaTick = 0; // Guard against unsorted events
            }
            lastTick = event.getAbsoluteTick();

            // Write delta-time in VLQ format
            trackPayload.write(encodeVLQ(deltaTick));

            // Write event message bytes
            writeEventPayload(trackPayload, event);
        }

        byte[] trackBytes = trackPayload.toByteArray();

        // 2. Assemble Complete SMF Format 0 File
        ByteArrayOutputStream midiFile = new ByteArrayOutputStream();

        // --- MThd Header Chunk (14 bytes) ---
        // Magic (4 bytes)
        midiFile.write(MTHD_MAGIC);
        // Header length: 6 bytes (4 bytes big-endian)
        midiFile.write(new byte[]{0x00, 0x00, 0x00, 0x06});
        // Format 0 (2 bytes)
        midiFile.write(new byte[]{0x00, 0x00});
        // 1 Track (2 bytes)
        midiFile.write(new byte[]{0x00, 0x01});
        // Division: 480 PPQ (0x01E0) (2 bytes)
        midiFile.write(new byte[]{(byte) ((DEFAULT_PPQ >> 8) & 0xFF), (byte) (DEFAULT_PPQ & 0xFF)});

        // --- MTrk Track Chunk ---
        // Magic (4 bytes)
        midiFile.write(MTRK_MAGIC);
        // Track length (4 bytes big-endian)
        int length = trackBytes.length;
        midiFile.write(new byte[]{
            (byte) ((length >> 24) & 0xFF),
            (byte) ((length >> 16) & 0xFF),
            (byte) ((length >> 8) & 0xFF),
            (byte) (length & 0xFF)
        });
        // Track events payload
        midiFile.write(trackBytes);

        return midiFile.toByteArray();
    }

    /**
     * Serializes an individual MIDI event's status and data payload into the track stream.
     */
    private static void writeEventPayload(OutputStream out, MidiEvent event) throws IOException {
        int ch = event.getChannel() & 0x0F;

        switch (event.getEventType()) {
            case TEMPO_META -> {
                // Meta Event: 0xFF 0x51 0x03 [3-byte microseconds/quarter note]
                out.write(0xFF);
                out.write(0x51);
                out.write(0x03);

                int bpm = event.getBpm() > 0 ? event.getBpm() : 120;
                int mpqn = Math.round(60_000_000.0f / bpm); // Microseconds per quarter note
                out.write((mpqn >> 16) & 0xFF);
                out.write((mpqn >> 8) & 0xFF);
                out.write(mpqn & 0xFF);
            }
            case PROGRAM_CHANGE -> {
                // Channel Event: 0xC0 | ch [programNumber]
                out.write(0xC0 | ch);
                out.write(event.getProgramNumber() & 0x7F);
            }
            case NOTE_ON -> {
                // Channel Event: 0x90 | ch [pitch] [velocity]
                out.write(0x90 | ch);
                out.write(event.getPitch() & 0x7F);
                out.write(event.getVelocity() & 0x7F);
            }
            case NOTE_OFF -> {
                // Channel Event: 0x80 | ch [pitch] [velocity: 0]
                out.write(0x80 | ch);
                out.write(event.getPitch() & 0x7F);
                out.write(event.getVelocity() & 0x7F);
            }
            case END_OF_TRACK -> {
                // Meta Event: 0xFF 0x2F 0x00
                out.write(0xFF);
                out.write(0x2F);
                out.write(0x00);
            }
        }
    }

    /**
     * Encodes a non-negative integer into a standard MIDI Variable-Length Quantity (VLQ).
     * Splits values into 7-bit chunks with bit 7 set as continuation flag on all but the last byte.
     *
     * @param value non-negative value (0 to 0x0FFFFFFF)
     * @return 1 to 4 bytes VLQ representation
     */
    public static byte[] encodeVLQ(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("VLQ delta value cannot be negative: " + value);
        }
        if (value == 0) {
            return new byte[]{0x00};
        }

        byte[] temp = new byte[4];
        int count = 0;

        // Least-significant 7 bits (bit 7 clear)
        temp[count++] = (byte) (value & 0x7F);
        value >>>= 7;

        // Remaining 7-bit chunks (bit 7 set)
        while (value > 0) {
            temp[count++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        // Reverse into big-endian byte array
        byte[] result = new byte[count];
        for (int i = 0; i < count; i++) {
            result[i] = temp[count - 1 - i];
        }
        return result;
    }

    /**
     * Serializes a list of MidiEvents to a binary MIDI file on disk.
     *
     * @param events     chronological list of MidiEvents
     * @param outputFile destination .mid file
     * @throws IOException on file I/O errors
     */
    public static void writeToFile(List<MidiEvent> events, File outputFile) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile cannot be null");
        byte[] data = generateMidiBytes(events);

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            bos.write(data);
            bos.flush();
        }
    }

    /**
     * Serializes a list of MidiEvents to a binary MIDI file on disk.
     *
     * @param events   chronological list of MidiEvents
     * @param filePath destination file path string
     * @throws IOException on file I/O errors
     */
    public static void writeToFile(List<MidiEvent> events, String filePath) throws IOException {
        writeToFile(events, new File(filePath));
    }

    /**
     * Generates a formatted hexadecimal dump of the binary MIDI file with
     * header chunks ('MThd' and 'MTrk') highlighted.
     *
     * @param data binary bytes of the MIDI file
     * @return multiline formatted hexadecimal representation
     */
    public static String formatHexDump(byte[] data) {
        if (data == null || data.length == 0) return "<Empty Binary Data>";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Offset (h)  -- -- -- -- -- -- -- --  -- -- -- -- -- -- -- --  Decoded Text%n"));
        sb.append("-------------------------------------------------------------------------\n");

        for (int i = 0; i < data.length; i += 16) {
            // Offset
            sb.append(String.format("%08X    ", i));

            // Hex Bytes (with middle delimiter)
            StringBuilder textPart = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                int index = i + j;
                if (j == 8) sb.append(" ");

                if (index < data.length) {
                    byte b = data[index];
                    sb.append(String.format("%02X ", b));

                    char ch = (b >= 32 && b <= 126) ? (char) b : '.';
                    textPart.append(ch);
                } else {
                    sb.append("   ");
                }
            }

            sb.append(" ");
            sb.append(textPart);

            // Highlight MThd or MTrk markers
            if (i == 0 && data.length >= 4 && isMagicAt(data, 0, MTHD_MAGIC)) {
                sb.append("  <-- [MThd Header Chunk]");
            } else if (i == 0 && data.length >= 18 && isMagicAt(data, 14, MTRK_MAGIC)) {
                sb.append("  <-- [MTrk Track Chunk at +14]");
            } else if (i == 16 && data.length >= 18 && isMagicAt(data, 14, MTRK_MAGIC)) {
                sb.append("  <-- [MTrk Track Chunk]");
            }

            sb.append("\n");
        }
        return sb.toString();
    }

    private static boolean isMagicAt(byte[] data, int offset, byte[] magic) {
        if (offset + magic.length > data.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[offset + i] != magic[i]) return false;
        }
        return true;
    }
}
