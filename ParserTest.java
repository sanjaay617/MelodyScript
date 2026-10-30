/**
 * Comprehensive Automated Test Suite for MelodyScript LL(1) Parser.
 * Zero third-party dependencies (pure Java assertions).
 */

import java.util.List;

public class ParserTest {

    public static void main(String[] args) {
        System.out.println("Running ParserTest suite...");

        testValidRequiredProgram();
        testIndividualTempoStmt();
        testIndividualInstrumentStmt();
        testPhraseDefWithRestsAndAccidentals();
        testPlayPhraseAndRepeat();
        testMissingSemicolonTempo();
        testMissingSemicolonInstrument();
        testMissingPhraseName();
        testMissingLbracePhraseDef();
        testMissingRbracePhraseDef();
        testMissingNoteAfterComma();
        testMissingDurationInNote();
        testInvalidPlayStmt();
        testMissingSemicolonAfterRepeatPlay();
        testAstTreeRendering();

        System.out.println("All 15 Parser tests passed successfully!");
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

    private static void testValidRequiredProgram() {
        String input =
            "tempo 120;\n" +
            "instrument piano;\n" +
            "define main_theme {\n" +
            "    C4 quarter, E4 quarter, G4 quarter, R quarter\n" +
            "}\n" +
            "play main_theme;\n" +
            "play repeat 2 { A4 eighth, B4 eighth };";

        Parser parser = new Parser(input);
        ProgramNode root = parser.parse();

        assertEquals(5, root.getStatements().size(), "Statement count");

        // Stmt 0: Tempo
        assertTrue(root.getStatements().get(0) instanceof TempoNode, "Stmt 0 is TempoNode");
        TempoNode tempo = (TempoNode) root.getStatements().get(0);
        assertEquals(120, tempo.getBpm(), "Tempo BPM");

        // Stmt 1: Instrument
        assertTrue(root.getStatements().get(1) instanceof InstrumentNode, "Stmt 1 is InstrumentNode");
        InstrumentNode inst = (InstrumentNode) root.getStatements().get(1);
        assertEquals("piano", inst.getInstrumentName(), "Instrument name");

        // Stmt 2: PhraseDef
        assertTrue(root.getStatements().get(2) instanceof PhraseDefNode, "Stmt 2 is PhraseDefNode");
        PhraseDefNode phrase = (PhraseDefNode) root.getStatements().get(2);
        assertEquals("main_theme", phrase.getPhraseName(), "Phrase name");
        assertEquals(4, phrase.getNoteSequence().getNotes().size(), "Note sequence length");

        // Stmt 3: Play Phrase
        assertTrue(root.getStatements().get(3) instanceof PlayNode, "Stmt 3 is PlayNode");
        PlayNode playPhrase = (PlayNode) root.getStatements().get(3);
        assertEquals("main_theme", playPhrase.getPhraseName(), "Play phrase name");
        assertEquals(false, playPhrase.isRepeat(), "Play phrase isRepeat");
        assertEquals(1, playPhrase.getRepeatCount(), "Play phrase repeat count");

        // Stmt 4: Play Repeat
        assertTrue(root.getStatements().get(4) instanceof PlayNode, "Stmt 4 is PlayNode");
        PlayNode playRepeat = (PlayNode) root.getStatements().get(4);
        assertEquals(true, playRepeat.isRepeat(), "Play repeat isRepeat");
        assertEquals(2, playRepeat.getRepeatCount(), "Play repeat count");
        assertEquals(2, playRepeat.getInlineSequence().getNotes().size(), "Inline sequence length");
    }

    private static void testIndividualTempoStmt() {
        Parser parser = new Parser("tempo 144;");
        ProgramNode root = parser.parse();
        assertEquals(1, root.getStatements().size(), "One statement");
        TempoNode t = (TempoNode) root.getStatements().get(0);
        assertEquals(144, t.getBpm(), "BPM 144");
        assertEquals(1, t.getLine(), "Line 1");
        assertEquals(1, t.getColumn(), "Column 1");
    }

    private static void testIndividualInstrumentStmt() {
        Parser parser = new Parser("instrument violin;");
        ProgramNode root = parser.parse();
        InstrumentNode inst = (InstrumentNode) root.getStatements().get(0);
        assertEquals("violin", inst.getInstrumentName(), "violin");
    }

    private static void testPhraseDefWithRestsAndAccidentals() {
        String src = "define jazz_lick { C#4 eighth, Eb5 half, R whole }";
        Parser parser = new Parser(src);
        ProgramNode root = parser.parse();
        PhraseDefNode phrase = (PhraseDefNode) root.getStatements().get(0);
        assertEquals("jazz_lick", phrase.getPhraseName(), "phrase name");

        List<ASTNode> notes = phrase.getNoteSequence().getNotes();
        assertEquals(3, notes.size(), "3 notes");

        NoteNode n1 = (NoteNode) notes.get(0);
        assertEquals("C#4", n1.getPitchStr(), "n1 pitch");
        assertEquals(61, n1.getPitchValue(), "n1 midi");
        assertEquals("eighth", n1.getDurationStr(), "n1 dur");
        assertEquals(240, n1.getDurationTicks(), "n1 ticks");

        NoteNode n2 = (NoteNode) notes.get(1);
        assertEquals("Eb5", n2.getPitchStr(), "n2 pitch");
        assertEquals(75, n2.getPitchValue(), "n2 midi");
        assertEquals("half", n2.getDurationStr(), "n2 dur");
        assertEquals(960, n2.getDurationTicks(), "n2 ticks");

        RestNode r = (RestNode) notes.get(2);
        assertEquals("whole", r.getDurationStr(), "r dur");
        assertEquals(1920, r.getDurationTicks(), "r ticks");
    }

    private static void testPlayPhraseAndRepeat() {
        String src = "play intro; play repeat 4 { G4 quarter };";
        Parser parser = new Parser(src);
        ProgramNode root = parser.parse();
        assertEquals(2, root.getStatements().size(), "2 play statements");

        PlayNode p1 = (PlayNode) root.getStatements().get(0);
        assertEquals(false, p1.isRepeat(), "p1 not repeat");
        assertEquals("intro", p1.getPhraseName(), "p1 phrase");

        PlayNode p2 = (PlayNode) root.getStatements().get(1);
        assertEquals(true, p2.isRepeat(), "p2 is repeat");
        assertEquals(4, p2.getRepeatCount(), "p2 count 4");
    }

    private static void testMissingSemicolonTempo() {
        try {
            new Parser("tempo 120 instrument flute;").parse();
            throw new AssertionError("Expected SyntaxException for missing semicolon in tempo");
        } catch (SyntaxException ex) {
            assertEquals(1, ex.getLine(), "Error line");
            assertEquals(11, ex.getColumn(), "Error col");
            assertTrue(ex.getMessage().contains("';' after tempo declaration"), "Message checks semicolon");
        }
    }

    private static void testMissingSemicolonInstrument() {
        try {
            new Parser("instrument guitar\nplay main;").parse();
            throw new AssertionError("Expected SyntaxException for missing semicolon in instrument");
        } catch (SyntaxException ex) {
            assertEquals(2, ex.getLine(), "Error line");
            assertEquals(1, ex.getColumn(), "Error col");
            assertTrue(ex.getMessage().contains("';' after instrument declaration"), "Message checks semicolon");
        }
    }

    private static void testMissingPhraseName() {
        try {
            new Parser("define { C4 quarter }").parse();
            throw new AssertionError("Expected SyntaxException for missing phrase identifier");
        } catch (SyntaxException ex) {
            assertTrue(ex.getMessage().contains("phrase name identifier"), "Message checks phrase identifier");
        }
    }

    private static void testMissingLbracePhraseDef() {
        try {
            new Parser("define melody C4 quarter }").parse();
            throw new AssertionError("Expected SyntaxException for missing '{'");
        } catch (SyntaxException ex) {
            assertTrue(ex.getMessage().contains("'{' to open phrase definition block"), "Message checks '{'");
        }
    }

    private static void testMissingRbracePhraseDef() {
        try {
            new Parser("define melody { C4 quarter ;").parse();
            throw new AssertionError("Expected SyntaxException for missing '}'");
        } catch (SyntaxException ex) {
            assertTrue(ex.getMessage().contains("'}' to close phrase definition block"), "Message checks '}'");
        }
    }

    private static void testMissingNoteAfterComma() {
        try {
            new Parser("define melody { C4 quarter, }").parse();
            throw new AssertionError("Expected SyntaxException for missing note after comma");
        } catch (SyntaxException ex) {
            assertTrue(ex.getMessage().contains("note pitch"), "Message checks note pitch or rest");
        }
    }

    private static void testMissingDurationInNote() {
        try {
            new Parser("define melody { C4, E4 quarter }").parse();
            throw new AssertionError("Expected SyntaxException for missing duration");
        } catch (SyntaxException ex) {
            assertTrue(ex.getMessage().contains("note duration"), "Message checks note duration");
        }
    }

    private static void testInvalidPlayStmt() {
        try {
            new Parser("play ;").parse();
            throw new AssertionError("Expected SyntaxException for empty play statement");
        } catch (SyntaxException ex) {
            assertTrue(ex.getMessage().contains("phrase identifier or 'repeat' after 'play'"), "Message checks play target");
        }
    }

    private static void testMissingSemicolonAfterRepeatPlay() {
        try {
            new Parser("play repeat 2 { C4 quarter } \n tempo 100;").parse();
            throw new AssertionError("Expected SyntaxException for missing semicolon after play repeat");
        } catch (SyntaxException ex) {
            assertEquals(2, ex.getLine(), "Error line");
            assertEquals(2, ex.getColumn(), "Error col");
            assertTrue(ex.getMessage().contains("';' after repeat play statement"), "Message checks repeat semicolon");
        }
    }

    private static void testAstTreeRendering() {
        String src = "tempo 100;\ndefine intro { C4 quarter }\nplay intro;";
        Parser parser = new Parser(src);
        ProgramNode root = parser.parse();
        String tree = root.renderTree(0);

        assertTrue(tree.contains("ProgramNode"), "Tree contains ProgramNode");
        assertTrue(tree.contains("TempoNode: bpm=100"), "Tree contains TempoNode");
        assertTrue(tree.contains("PhraseDefNode: phrase=\"intro\""), "Tree contains PhraseDefNode");
        assertTrue(tree.contains("NoteNode: pitch=\"C4\" (MIDI: 60)"), "Tree contains NoteNode");
        assertTrue(tree.contains("PlayNode: phrase=\"intro\""), "Tree contains PlayNode");
    }
}
