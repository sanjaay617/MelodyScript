/**
 * Comprehensive Unit Tests for MelodyScript Lexical Analyzer.
 * Zero external dependencies (uses standard Java assertions).
 */

import java.util.List;

public class LexerTest {

    public static void main(String[] args) {
        System.out.println("Running LexerTest suite...");

        testEmptyInput();
        testWhitespaceOnly();
        testKeywordsAndIdentifiers();
        testDurations();
        testPitchesMidiCalculations();
        testPitchEdgeCases();
        testComments();
        testWindowsCrlfNewlines();
        testIllegalCharacters();
        testPitchBoundaryVersusIdentifier();
        testNumberLiteralWithTrailingIdentifier();

        System.out.println("All 11 test suites passed successfully!");
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

    private static void testEmptyInput() {
        Lexer lexer = new Lexer("");
        Token token = lexer.nextToken();
        assertEquals(Token.TokenType.EOF, token.getType(), "Empty input EOF");
        assertEquals(1, token.getLine(), "Empty input line");
        assertEquals(1, token.getColumn(), "Empty input col");
    }

    private static void testWhitespaceOnly() {
        Lexer lexer = new Lexer("   \t  \n  \n  ");
        Token token = lexer.nextToken();
        assertEquals(Token.TokenType.EOF, token.getType(), "Whitespace only EOF");
        assertEquals(3, token.getLine(), "Whitespace line count");
        assertEquals(3, token.getColumn(), "Whitespace col");
    }

    private static void testKeywordsAndIdentifiers() {
        String src = "tempo instrument define play repeat main_theme piano foo_bar_1";
        Lexer lexer = new Lexer(src);
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.KEYWORD_TEMPO, tokens.get(0).getType(), "tempo");
        assertEquals(Token.TokenType.KEYWORD_INSTRUMENT, tokens.get(1).getType(), "instrument");
        assertEquals(Token.TokenType.KEYWORD_DEFINE, tokens.get(2).getType(), "define");
        assertEquals(Token.TokenType.KEYWORD_PLAY, tokens.get(3).getType(), "play");
        assertEquals(Token.TokenType.KEYWORD_REPEAT, tokens.get(4).getType(), "repeat");
        assertEquals(Token.TokenType.IDENTIFIER, tokens.get(5).getType(), "main_theme");
        assertEquals("main_theme", tokens.get(5).getLexeme(), "main_theme lexeme");
        assertEquals(Token.TokenType.IDENTIFIER, tokens.get(6).getType(), "piano");
        assertEquals(Token.TokenType.IDENTIFIER, tokens.get(7).getType(), "foo_bar_1");
        assertEquals(Token.TokenType.EOF, tokens.get(8).getType(), "EOF");
    }

    private static void testDurations() {
        String src = "whole half quarter eighth sixteenth thirtysecond";
        Lexer lexer = new Lexer(src);
        List<Token> tokens = lexer.tokenize();

        assertEquals(1920, tokens.get(0).getDurationTicks(), "whole ticks");
        assertEquals(960, tokens.get(1).getDurationTicks(), "half ticks");
        assertEquals(480, tokens.get(2).getDurationTicks(), "quarter ticks");
        assertEquals(240, tokens.get(3).getDurationTicks(), "eighth ticks");
        assertEquals(120, tokens.get(4).getDurationTicks(), "sixteenth ticks");
        assertEquals(60, tokens.get(5).getDurationTicks(), "thirtysecond ticks");
    }

    private static void testPitchesMidiCalculations() {
        // C4 -> 60, A4 -> 69, C#4 -> 61, Eb5 -> 75
        assertEquals(60, Lexer.parsePitchMidi("C4", 1, 1), "C4 MIDI");
        assertEquals(69, Lexer.parsePitchMidi("A4", 1, 1), "A4 MIDI");
        assertEquals(61, Lexer.parsePitchMidi("C#4", 1, 1), "C#4 MIDI");
        assertEquals(61, Lexer.parsePitchMidi("Db4", 1, 1), "Db4 MIDI");
        assertEquals(75, Lexer.parsePitchMidi("Eb5", 1, 1), "Eb5 MIDI");
        assertEquals(75, Lexer.parsePitchMidi("D#5", 1, 1), "D#5 MIDI");

        // Bounds: C0 -> 12, G9 -> 127
        assertEquals(12, Lexer.parsePitchMidi("C0", 1, 1), "C0 MIDI");
        assertEquals(127, Lexer.parsePitchMidi("G9", 1, 1), "G9 MIDI");
        assertEquals(126, Lexer.parsePitchMidi("F#9", 1, 1), "F#9 MIDI");
    }

    private static void testPitchEdgeCases() {
        // Out of MIDI range (G#9 = 128 > 127)
        try {
            Lexer.parsePitchMidi("G#9", 1, 1);
            throw new AssertionError("Expected out-of-range exception for G#9");
        } catch (LexicalException ex) {
            assertTrue(ex.getMessage().contains("exceeding valid MIDI range"), "G#9 error message");
        }

        // Out of MIDI range (A9 = 129 > 127)
        try {
            Lexer.parsePitchMidi("A9", 1, 1);
            throw new AssertionError("Expected out-of-range exception for A9");
        } catch (LexicalException ex) {
            assertTrue(ex.getMessage().contains("exceeding valid MIDI range"), "A9 error message");
        }
    }

    private static void testComments() {
        String src =
            "// comment at start\n" +
            "tempo 100; // inline comment\n" +
            "// comment at end without newline";
        Lexer lexer = new Lexer(src);
        List<Token> tokens = lexer.tokenize();

        assertEquals(4, tokens.size(), "Comment token count (tempo, 100, ;, EOF)");
        assertEquals(Token.TokenType.KEYWORD_TEMPO, tokens.get(0).getType(), "tempo after comment");
        assertEquals(2, tokens.get(0).getLine(), "tempo line");
        assertEquals(1, tokens.get(0).getColumn(), "tempo col");
        assertEquals(Token.TokenType.NUMBER, tokens.get(1).getType(), "100");
        assertEquals(Token.TokenType.SEMICOLON, tokens.get(2).getType(), ";");
        assertEquals(Token.TokenType.EOF, tokens.get(3).getType(), "EOF");
    }

    private static void testWindowsCrlfNewlines() {
        String src = "tempo 120;\r\ninstrument piano;\r\nplay main;";
        Lexer lexer = new Lexer(src);
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.KEYWORD_INSTRUMENT, tokens.get(3).getType(), "instrument on CRLF line 2");
        assertEquals(2, tokens.get(3).getLine(), "line 2");
        assertEquals(1, tokens.get(3).getColumn(), "col 1");

        assertEquals(Token.TokenType.KEYWORD_PLAY, tokens.get(6).getType(), "play on CRLF line 3");
        assertEquals(3, tokens.get(6).getLine(), "line 3");
        assertEquals(1, tokens.get(6).getColumn(), "col 1");
    }

    private static void testIllegalCharacters() {
        String src = "tempo 120; $";
        Lexer lexer = new Lexer(src);
        try {
            lexer.tokenize();
            throw new AssertionError("Expected exception for '$'");
        } catch (LexicalException ex) {
            assertEquals(1, ex.getLine(), "Error line");
            assertEquals(12, ex.getColumn(), "Error col");
            assertTrue(ex.getMessage().contains("'$'"), "Error message contains '$'");
        }
    }

    private static void testPitchBoundaryVersusIdentifier() {
        // C4_variant is an identifier, not pitch C4 followed by _variant
        String src = "C4_variant C4";
        Lexer lexer = new Lexer(src);
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.IDENTIFIER, tokens.get(0).getType(), "C4_variant is IDENTIFIER");
        assertEquals("C4_variant", tokens.get(0).getLexeme(), "lexeme");
        assertEquals(Token.TokenType.NOTE_PITCH, tokens.get(1).getType(), "C4 is NOTE_PITCH");
        assertEquals(60, tokens.get(1).getPitchValue(), "C4 pitch");
    }

    private static void testNumberLiteralWithTrailingIdentifier() {
        String src = "120bpm";
        Lexer lexer = new Lexer(src);
        try {
            lexer.tokenize();
            throw new AssertionError("Expected exception for 120bpm");
        } catch (LexicalException ex) {
            assertEquals(1, ex.getLine(), "Error line for 120bpm");
            assertEquals(1, ex.getColumn(), "Error col for 120bpm");
            assertTrue(ex.getMessage().contains("cannot immediately follow digits"), "Error message");
        }
    }
}
