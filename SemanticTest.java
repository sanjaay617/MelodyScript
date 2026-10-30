/**
 * Comprehensive Automated Test Suite for MelodyScript Semantic Analyzer.
 * Zero third-party dependencies (pure Java assertions).
 */

public class SemanticTest {

    public static void main(String[] args) {
        System.out.println("Running SemanticTest suite...");

        testGeneralMidiResolution();
        testScopeHierarchy();
        testValidPipeline();
        testUnboundPhraseError();
        testDuplicatePhraseError();
        testInvalidTempoZero();
        testUnknownInstrument();
        testInvalidRepeatCount();
        testPitchBoundsValidation();
        testMultiplePhrasesValid();

        System.out.println("All 10 Semantic tests passed successfully!");
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

    private static void testGeneralMidiResolution() {
        assertEquals(0, SymbolTable.resolveInstrument("piano"), "piano -> 0");
        assertEquals(0, SymbolTable.resolveInstrument("acoustic_grand_piano"), "acoustic_grand_piano -> 0");
        assertEquals(1, SymbolTable.resolveInstrument("bright_piano"), "bright_piano -> 1");
        assertEquals(40, SymbolTable.resolveInstrument("violin"), "violin -> 40");
        assertEquals(73, SymbolTable.resolveInstrument("flute"), "flute -> 73");
        assertEquals(56, SymbolTable.resolveInstrument("trumpet"), "trumpet -> 56");
        assertEquals(80, SymbolTable.resolveInstrument("synth_lead"), "synth_lead -> 80");
        assertEquals(24, SymbolTable.resolveInstrument("acoustic_guitar"), "acoustic_guitar -> 24");

        // Case-insensitivity & normalization
        assertEquals(0, SymbolTable.resolveInstrument("Acoustic Grand Piano"), "normalized acoustic grand piano");
        assertEquals(40, SymbolTable.resolveInstrument("VIOLIN"), "uppercase VIOLIN");
        assertEquals(80, SymbolTable.resolveInstrument("synth-lead"), "synth-lead with hyphen");

        // Unknown instrument
        assertEquals(-1, SymbolTable.resolveInstrument("alien_synth"), "alien_synth -> -1");
        assertEquals(-1, SymbolTable.resolveInstrument("nonexistent_device"), "nonexistent -> -1");
    }

    private static void testScopeHierarchy() {
        SymbolTable root = new SymbolTable();
        PhraseDefNode node1 = new PhraseDefNode("p1", new NoteSequenceNode(java.util.List.of()), 1, 1);
        PhraseDefNode node2 = new PhraseDefNode("p2", new NoteSequenceNode(java.util.List.of()), 2, 1);

        assertTrue(root.define("p1", node1), "define p1 in root");
        assertEquals(false, root.define("p1", node1), "p1 duplicate in root rejected");

        SymbolTable child = root.enterScope();
        assertTrue(child.define("p2", node2), "define p2 in child");

        assertEquals(node1, child.lookup("p1"), "child lookup p1 from root");
        assertEquals(node2, child.lookup("p2"), "child lookup p2 in child");
        assertEquals(null, root.lookup("p2"), "root cannot see child symbol p2");

        SymbolTable parent = child.exitScope();
        assertEquals(root, parent, "exitScope returns root");
    }

    private static void testValidPipeline() {
        String src =
            "tempo 120;\n" +
            "instrument piano;\n" +
            "define main_theme { C4 quarter, E4 quarter }\n" +
            "play main_theme;\n" +
            "play repeat 2 { A4 eighth };";

        ProgramNode ast = new Parser(src).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer().analyze(ast);

        assertEquals(120, analyzer.getTempoBpm(), "Tempo 120");
        assertEquals(1, analyzer.getValidatedSymbols().size(), "1 defined phrase");
        assertEquals("main_theme", analyzer.getValidatedSymbols().get(0), "main_theme validated");
        assertEquals(Integer.valueOf(0), analyzer.getResolvedInstruments().get("piano"), "piano GM 0");
        assertEquals(3, analyzer.getTotalNotesCount(), "3 notes count");
    }

    private static void testUnboundPhraseError() {
        String src = "play missing_phrase;";
        try {
            ProgramNode ast = new Parser(src).parse();
            new SemanticAnalyzer().analyze(ast);
            throw new AssertionError("Expected SemanticException for unbound phrase");
        } catch (SemanticException ex) {
            assertEquals(1, ex.getLine(), "Error line");
            assertEquals(1, ex.getColumn(), "Error col");
            assertTrue(ex.getRuleDescription().contains("Rule 1"), "Rule 1 in description");
            assertTrue(ex.getOffenderDetails().contains("missing_phrase"), "Offender missing_phrase");
        }
    }

    private static void testDuplicatePhraseError() {
        String src = "define intro { C4 quarter }\ndefine intro { E4 quarter }";
        try {
            ProgramNode ast = new Parser(src).parse();
            new SemanticAnalyzer().analyze(ast);
            throw new AssertionError("Expected SemanticException for duplicate phrase");
        } catch (SemanticException ex) {
            assertEquals(2, ex.getLine(), "Error line 2");
            assertEquals(1, ex.getColumn(), "Error col 1");
            assertTrue(ex.getRuleDescription().contains("Rule 2"), "Rule 2 in description");
            assertTrue(ex.getOffenderDetails().contains("intro"), "Offender intro");
        }
    }

    private static void testInvalidTempoZero() {
        String src = "tempo 0;";
        try {
            ProgramNode ast = new Parser(src).parse();
            new SemanticAnalyzer().analyze(ast);
            throw new AssertionError("Expected SemanticException for tempo 0");
        } catch (SemanticException ex) {
            assertTrue(ex.getRuleDescription().contains("Rule 4"), "Rule 4 in description");
            assertTrue(ex.getOffenderDetails().contains("0"), "Offender 0");
        }
    }

    private static void testUnknownInstrument() {
        String src = "instrument space_flute;";
        try {
            ProgramNode ast = new Parser(src).parse();
            new SemanticAnalyzer().analyze(ast);
            throw new AssertionError("Expected SemanticException for space_flute");
        } catch (SemanticException ex) {
            assertTrue(ex.getRuleDescription().contains("Rule 5"), "Rule 5 in description");
            assertTrue(ex.getOffenderDetails().contains("space_flute"), "Offender space_flute");
        }
    }

    private static void testInvalidRepeatCount() {
        // Construct AST directly with non-positive repeat count
        PlayNode play = new PlayNode(null, 0, new NoteSequenceNode(java.util.List.of()), true, 5, 1);
        ProgramNode program = new ProgramNode(java.util.List.of(play));
        try {
            new SemanticAnalyzer().analyze(program);
            throw new AssertionError("Expected SemanticException for repeat count 0");
        } catch (SemanticException ex) {
            assertTrue(ex.getRuleDescription().contains("Repeat Count"), "Repeat count rule");
        }
    }

    private static void testPitchBoundsValidation() {
        // Construct AST directly with out-of-bounds note
        NoteNode badNote = new NoteNode("BAD", 128, "quarter", 480, 2, 5);
        PhraseDefNode def = new PhraseDefNode("theme", new NoteSequenceNode(java.util.List.of(badNote)), 1, 1);
        ProgramNode program = new ProgramNode(java.util.List.of(def));
        try {
            new SemanticAnalyzer().analyze(program);
            throw new AssertionError("Expected SemanticException for pitch 128");
        } catch (SemanticException ex) {
            assertTrue(ex.getRuleDescription().contains("Rule 3"), "Rule 3 in description");
            assertTrue(ex.getOffenderDetails().contains("128"), "Offender 128");
        }
    }

    private static void testMultiplePhrasesValid() {
        String src =
            "tempo 100;\n" +
            "instrument flute;\n" +
            "define phrase_a { C4 quarter, D4 quarter }\n" +
            "define phrase_b { E4 quarter, F4 quarter }\n" +
            "play phrase_a;\n" +
            "play phrase_b;\n" +
            "play repeat 3 { G4 eighth, A4 eighth };";

        ProgramNode ast = new Parser(src).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer().analyze(ast);

        assertEquals(2, analyzer.getValidatedSymbols().size(), "2 validated phrases");
        assertEquals(3, analyzer.getTotalPlayDirectives(), "3 play directives");
        assertEquals(6, analyzer.getTotalNotesCount(), "6 pitched notes");
    }
}
