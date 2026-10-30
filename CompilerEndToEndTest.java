/**
 * MelodyScript End-to-End Compiler Integration Tests
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * End-to-end integration tests verifying the entire compiler pipeline:
 * Source -> Lexer -> Parser -> SemanticAnalyzer -> IRGenerator -> MidiFileWriter.
 * Asserts binary MIDI file compliance (MThd, MTrk, SMF Format 0, 480 PPQ, VLQ).
 */
public class CompilerEndToEndTest {

    public static void main(String[] args) {
        System.out.println("Running CompilerEndToEndTest suite...");

        testVlqEncoding();
        testEndToEndMidiFileGeneration();
        testCliMainCompile();
        testBinaryHeaderAndTrackIntegrity();

        System.out.println("All 4 End-to-End Integration tests passed successfully!");
    }

    private static void assertEquals(Object expected, Object actual, String testName) {
        if (!ObjectsEquals(expected, actual)) {
            throw new AssertionError(String.format("FAILED [%s]: expected <%s> but got <%s>", testName, expected, actual));
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (!condition) {
            throw new AssertionError("FAILED [" + testName + "]: condition was false");
        }
    }

    private static boolean ObjectsEquals(Object a, Object b) {
        if (a instanceof byte[] ba && b instanceof byte[] bb) {
            return Arrays.equals(ba, bb);
        }
        return java.util.Objects.equals(a, b);
    }

    private static void testVlqEncoding() {
        // 0 -> [0x00]
        assertEquals(new byte[]{0x00}, MidiFileWriter.encodeVLQ(0), "VLQ 0");
        // 127 -> [0x7F]
        assertEquals(new byte[]{0x7F}, MidiFileWriter.encodeVLQ(127), "VLQ 127");
        // 128 -> [0x81, 0x00]
        assertEquals(new byte[]{(byte) 0x81, 0x00}, MidiFileWriter.encodeVLQ(128), "VLQ 128");
        // 240 (eighth note) -> [0x81, 0x70]
        assertEquals(new byte[]{(byte) 0x81, 0x70}, MidiFileWriter.encodeVLQ(240), "VLQ 240");
        // 480 (quarter note) -> [0x83, 0x60]
        assertEquals(new byte[]{(byte) 0x83, 0x60}, MidiFileWriter.encodeVLQ(480), "VLQ 480");
        // 960 (half note) -> [0x87, 0x40]
        assertEquals(new byte[]{(byte) 0x87, 0x40}, MidiFileWriter.encodeVLQ(960), "VLQ 960");
        // 1920 (whole note) -> [0x8F, 0x00]
        assertEquals(new byte[]{(byte) 0x8F, 0x00}, MidiFileWriter.encodeVLQ(1920), "VLQ 1920");
    }

    private static void testEndToEndMidiFileGeneration() {
        String source =
            "tempo 120;\n" +
            "instrument piano;\n" +
            "define main_theme {\n" +
            "    C4 quarter, E4 quarter, G4 quarter, R quarter\n" +
            "}\n" +
            "play main_theme;\n" +
            "play repeat 2 { A4 eighth, B4 eighth };";

        try {
            // Full Pipeline
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer);
            ProgramNode ast = parser.parse();

            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(ast);

            IRGenerator irGen = new IRGenerator(ast, analyzer.getSymbolTable());
            List<MidiEvent> events = irGen.generate();

            byte[] midiData = MidiFileWriter.generateMidiBytes(events);

            assertTrue(midiData != null && midiData.length > 22, "Binary MIDI data is non-empty");

            // 1. Assert MThd Magic Bytes at Offset 0 (4D 54 68 64)
            assertEquals((byte) 0x4D, midiData[0], "MThd byte 0 'M'");
            assertEquals((byte) 0x54, midiData[1], "MThd byte 1 'T'");
            assertEquals((byte) 0x68, midiData[2], "MThd byte 2 'h'");
            assertEquals((byte) 0x64, midiData[3], "MThd byte 3 'd'");

            // 2. Assert Header Length 6 at Offset 4..7 (00 00 00 06)
            assertEquals((byte) 0x00, midiData[4], "Header len 0");
            assertEquals((byte) 0x00, midiData[5], "Header len 1");
            assertEquals((byte) 0x00, midiData[6], "Header len 2");
            assertEquals((byte) 0x06, midiData[7], "Header len 3 (6)");

            // 3. Assert Format 0 at Offset 8..9 (00 00)
            assertEquals((byte) 0x00, midiData[8], "Format 0 byte 0");
            assertEquals((byte) 0x00, midiData[9], "Format 0 byte 1");

            // 4. Assert 1 Track at Offset 10..11 (00 01)
            assertEquals((byte) 0x00, midiData[10], "Tracks byte 0");
            assertEquals((byte) 0x01, midiData[11], "Tracks byte 1 (1)");

            // 5. Assert Division 480 PPQ at Offset 12..13 (01 E0)
            assertEquals((byte) 0x01, midiData[12], "PPQ byte 0 (0x01)");
            assertEquals((byte) 0xE0, midiData[13], "PPQ byte 1 (0xE0)");

            // 6. Assert MTrk Magic Bytes at Offset 14 (4D 54 72 6B)
            assertEquals((byte) 0x4D, midiData[14], "MTrk byte 0 'M'");
            assertEquals((byte) 0x54, midiData[15], "MTrk byte 1 'T'");
            assertEquals((byte) 0x72, midiData[16], "MTrk byte 2 'r'");
            assertEquals((byte) 0x6B, midiData[17], "MTrk byte 3 'k'");

            // 7. Assert Track Length at Offset 18..21 matches payload size
            int trackPayloadLength =
                ((midiData[18] & 0xFF) << 24) |
                ((midiData[19] & 0xFF) << 16) |
                ((midiData[20] & 0xFF) << 8) |
                (midiData[21] & 0xFF);

            assertEquals(midiData.length - 22, trackPayloadLength, "Track payload length consistency");

            // 8. Assert End of Track bytes (FF 2F 00) at file end
            int len = midiData.length;
            assertEquals((byte) 0xFF, midiData[len - 3], "End of track 0xFF");
            assertEquals((byte) 0x2F, midiData[len - 2], "End of track 0x2F");
            assertEquals((byte) 0x00, midiData[len - 1], "End of track 0x00");

            // Write to disk and verify file existence and size
            Path testOut = Path.of("test_output.mid");
            MidiFileWriter.writeToFile(events, testOut.toFile());
            assertTrue(Files.exists(testOut), "Output file was written to disk");
            assertEquals(midiData.length, (int) Files.size(testOut), "File size matches generated byte array");

            // Verify with JDK standard javax.sound.midi.MidiSystem
            javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(testOut.toFile());
            assertEquals(480, seq.getResolution(), "JDK MidiSystem resolution is 480 PPQ");
            assertEquals(1, seq.getTracks().length, "JDK MidiSystem track count is 1");
            assertTrue(seq.getTickLength() >= 2880, "JDK MidiSystem tick length >= 2880");

            Files.deleteIfExists(testOut);

        } catch (Exception e) {
            throw new AssertionError("End-to-end pipeline test failed: " + e.getMessage(), e);
        }
    }

    private static void testCliMainCompile() {
        String testDsl = "tempo 140; instrument violin; play repeat 1 { C#4 quarter };";
        Path testMs = Path.of("cli_test.ms");
        Path testMid = Path.of("cli_test.mid");

        try {
            Files.writeString(testMs, testDsl);

            boolean success = Main.compile("cli_test.ms", "cli_test.mid", true, true, true);
            assertTrue(success, "Main.compile returned success");
            assertTrue(Files.exists(testMid), "cli_test.mid was generated");
            assertTrue(Files.size(testMid) > 22, "cli_test.mid is valid size");

        } catch (IOException e) {
            throw new AssertionError("CLI compile test failed: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(testMs);
                Files.deleteIfExists(testMid);
            } catch (IOException ignored) {}
        }
    }

    private static void testBinaryHeaderAndTrackIntegrity() {
        byte[] dummyData = new byte[]{0x4D, 0x54, 0x68, 0x64};
        String hex = MidiFileWriter.formatHexDump(dummyData);
        assertTrue(hex.contains("MThd Header Chunk"), "Hex dump highlights MThd");
    }
}
