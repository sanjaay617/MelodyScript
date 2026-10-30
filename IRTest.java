/**
 * Comprehensive Automated Test Suite for MelodyScript Intermediate Representation (IR).
 * Zero third-party dependencies (pure Java assertions).
 */

import java.util.List;

public class IRTest {

    public static void main(String[] args) {
        System.out.println("Running IRTest suite...");

        testRequiredPipelineEvents();
        testRestHandlingNoEventsEmitted();
        testRepeatLoopUnrolling();
        testMultiplePhraseCalls();
        testPlaybackDurationFormula();
        testSameTickDeterministicSorting();

        System.out.println("All 6 IR tests passed successfully!");
    }

    private static void assertEquals(Object expected, Object actual, String testName) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(String.format("FAILED [%s]: expected <%s> but got <%s>", testName, expected, actual));
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (!condition) {
            throw new AssertionError("FAILED [" + testName + "]: condition was false");
        }
    }

    private static void testRequiredPipelineEvents() {
        String src =
            "tempo 120;\n" +
            "instrument piano;\n" +
            "define main_theme {\n" +
            "    C4 quarter, E4 quarter, G4 quarter, R quarter\n" +
            "}\n" +
            "play main_theme;\n" +
            "play repeat 2 { A4 eighth, B4 eighth };";

        ProgramNode ast = new Parser(src).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer().analyze(ast);
        IRGenerator irGen = new IRGenerator(ast, analyzer.getSymbolTable());
        List<MidiEvent> events = irGen.generate();

        assertEquals(17, events.size(), "Exact event count");

        // Event 0: TEMPO_META at tick 0
        assertEquals(0L, events.get(0).getAbsoluteTick(), "Event 0 tick");
        assertEquals(MidiEvent.EventType.TEMPO_META, events.get(0).getEventType(), "Event 0 TEMPO_META");
        assertEquals(120, events.get(0).getBpm(), "Event 0 BPM");

        // Event 1: PROGRAM_CHANGE at tick 0 (piano = 0)
        assertEquals(0L, events.get(1).getAbsoluteTick(), "Event 1 tick");
        assertEquals(MidiEvent.EventType.PROGRAM_CHANGE, events.get(1).getEventType(), "Event 1 PROGRAM_CHANGE");
        assertEquals(0, events.get(1).getProgramNumber(), "Event 1 program");

        // Event 2: C4 NOTE_ON at 0
        assertEquals(0L, events.get(2).getAbsoluteTick(), "C4 ON tick");
        assertEquals(MidiEvent.EventType.NOTE_ON, events.get(2).getEventType(), "C4 ON type");
        assertEquals(60, events.get(2).getPitch(), "C4 ON pitch");
        assertEquals(100, events.get(2).getVelocity(), "C4 ON vel");
        assertEquals(480, events.get(2).getDurationTicks(), "C4 ON dur");

        // Event 3: C4 NOTE_OFF at 480
        assertEquals(480L, events.get(3).getAbsoluteTick(), "C4 OFF tick");
        assertEquals(MidiEvent.EventType.NOTE_OFF, events.get(3).getEventType(), "C4 OFF type");
        assertEquals(60, events.get(3).getPitch(), "C4 OFF pitch");
        assertEquals(0, events.get(3).getVelocity(), "C4 OFF vel");

        // Event 4: E4 NOTE_ON at 480
        assertEquals(480L, events.get(4).getAbsoluteTick(), "E4 ON tick");
        assertEquals(64, events.get(4).getPitch(), "E4 ON pitch");

        // Event 5: E4 NOTE_OFF at 960
        assertEquals(960L, events.get(5).getAbsoluteTick(), "E4 OFF tick");

        // Event 6: G4 NOTE_ON at 960
        assertEquals(960L, events.get(6).getAbsoluteTick(), "G4 ON tick");
        assertEquals(67, events.get(6).getPitch(), "G4 ON pitch");

        // Event 7: G4 NOTE_OFF at 1440
        assertEquals(1440L, events.get(7).getAbsoluteTick(), "G4 OFF tick");

        // Rest quarter advances 1440 to 1920 (no events)

        // Event 8: A4 NOTE_ON at 1920
        assertEquals(1920L, events.get(8).getAbsoluteTick(), "A4 rep1 ON tick");
        assertEquals(69, events.get(8).getPitch(), "A4 rep1 pitch");

        // Event 16: END_OF_TRACK at 2880
        MidiEvent last = events.get(16);
        assertEquals(2880L, last.getAbsoluteTick(), "END_OF_TRACK tick");
        assertEquals(MidiEvent.EventType.END_OF_TRACK, last.getEventType(), "END_OF_TRACK type");

        assertEquals(2880L, irGen.getTotalTicks(), "Total ticks 2880");
        assertEquals(3.0, irGen.getPlaybackSeconds(), "Estimated playback seconds 3.0");
    }

    private static void testRestHandlingNoEventsEmitted() {
        String src =
            "tempo 120;\n" +
            "define rests_only { R quarter, R half }\n" +
            "play rests_only;";

        ProgramNode ast = new Parser(src).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer().analyze(ast);
        IRGenerator irGen = new IRGenerator(ast, analyzer.getSymbolTable());
        List<MidiEvent> events = irGen.generate();

        // Should only have TEMPO_META and END_OF_TRACK
        assertEquals(2, events.size(), "Only TEMPO_META and END_OF_TRACK");
        assertEquals(MidiEvent.EventType.TEMPO_META, events.get(0).getEventType(), "First event TEMPO_META");
        assertEquals(MidiEvent.EventType.END_OF_TRACK, events.get(1).getEventType(), "Second event END_OF_TRACK");
        assertEquals(1440L, events.get(1).getAbsoluteTick(), "Rest quarter (480) + half (960) = 1440 ticks");
    }

    private static void testRepeatLoopUnrolling() {
        String src =
            "tempo 120;\n" +
            "play repeat 3 { C4 quarter };";

        ProgramNode ast = new Parser(src).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer().analyze(ast);
        IRGenerator irGen = new IRGenerator(ast, analyzer.getSymbolTable());
        List<MidiEvent> events = irGen.generate();

        // TEMPO_META (1) + 3 * (NOTE_ON + NOTE_OFF) (6) + END_OF_TRACK (1) = 8 events
        assertEquals(8, events.size(), "8 total events");
        assertEquals(1440L, irGen.getTotalTicks(), "3 quarter notes = 1440 ticks");
    }

    private static void testMultiplePhraseCalls() {
        String src =
            "tempo 100;\n" +
            "define p1 { C4 quarter }\n" +
            "define p2 { D4 quarter }\n" +
            "play p1;\n" +
            "play p2;\n" +
            "play p1;";

        ProgramNode ast = new Parser(src).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer().analyze(ast);
        IRGenerator irGen = new IRGenerator(ast, analyzer.getSymbolTable());
        List<MidiEvent> events = irGen.generate();

        // 3 notes = 3 * 2 events + TEMPO_META + END_OF_TRACK = 8 events
        assertEquals(8, events.size(), "8 events");
        assertEquals(1440L, irGen.getTotalTicks(), "1440 ticks");
    }

    private static void testPlaybackDurationFormula() {
        // 480 ticks at 60 BPM -> 1 second
        String src60 = "tempo 60; play repeat 1 { C4 quarter };";
        ProgramNode ast60 = new Parser(src60).parse();
        SemanticAnalyzer an60 = new SemanticAnalyzer().analyze(ast60);
        IRGenerator ir60 = new IRGenerator(ast60, an60.getSymbolTable());
        assertEquals(1.0, ir60.getPlaybackSeconds(), "480 ticks at 60 BPM is 1.0s");

        // 480 ticks at 120 BPM -> 0.5 seconds
        String src120 = "tempo 120; play repeat 1 { C4 quarter };";
        ProgramNode ast120 = new Parser(src120).parse();
        SemanticAnalyzer an120 = new SemanticAnalyzer().analyze(ast120);
        IRGenerator ir120 = new IRGenerator(ast120, an120.getSymbolTable());
        assertEquals(0.5, ir120.getPlaybackSeconds(), "480 ticks at 120 BPM is 0.5s");

        // 480 ticks at 240 BPM -> 0.25 seconds
        String src240 = "tempo 240; play repeat 1 { C4 quarter };";
        ProgramNode ast240 = new Parser(src240).parse();
        SemanticAnalyzer an240 = new SemanticAnalyzer().analyze(ast240);
        IRGenerator ir240 = new IRGenerator(ast240, an240.getSymbolTable());
        assertEquals(0.25, ir240.getPlaybackSeconds(), "480 ticks at 240 BPM is 0.25s");
    }

    private static void testSameTickDeterministicSorting() {
        // At tick 0: TEMPO_META, PROGRAM_CHANGE, NOTE_ON
        // At tick 480: NOTE_OFF for note 1, NOTE_ON for note 2
        String src = "tempo 120; instrument violin; play repeat 1 { C4 quarter, D4 quarter };";
        ProgramNode ast = new Parser(src).parse();
        SemanticAnalyzer an = new SemanticAnalyzer().analyze(ast);
        IRGenerator ir = new IRGenerator(ast, an.getSymbolTable());
        List<MidiEvent> list = ir.generate();

        // Tick 0 order
        assertEquals(MidiEvent.EventType.TEMPO_META, list.get(0).getEventType(), "Tick 0 first is TEMPO_META");
        assertEquals(MidiEvent.EventType.PROGRAM_CHANGE, list.get(1).getEventType(), "Tick 0 second is PROGRAM_CHANGE");
        assertEquals(MidiEvent.EventType.NOTE_ON, list.get(2).getEventType(), "Tick 0 third is NOTE_ON");

        // Tick 480 order: NOTE_OFF before NOTE_ON
        assertEquals(480L, list.get(3).getAbsoluteTick(), "Tick 480");
        assertEquals(MidiEvent.EventType.NOTE_OFF, list.get(3).getEventType(), "Tick 480 first is NOTE_OFF");
        assertEquals(480L, list.get(4).getAbsoluteTick(), "Tick 480");
        assertEquals(MidiEvent.EventType.NOTE_ON, list.get(4).getEventType(), "Tick 480 second is NOTE_ON");
    }
}
