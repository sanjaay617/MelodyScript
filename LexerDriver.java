/**
 * MelodyScript Lexical Analyzer
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.List;

/**
 * Test driver for the MelodyScript lexical analyzer.
 * Executes the required DSL input test case and displays detailed token diagnostics.
 */
public class LexerDriver {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("                     MelodyScript DSL Lexical Analyzer                          ");
        System.out.println("================================================================================");

        // Required DSL input per specification
        String requiredInput =
            "tempo 120;\n" +
            "instrument piano;\n" +
            "define main_theme {\n" +
            "    C4 quarter, E4 quarter, G4 quarter, R quarter\n" +
            "}\n" +
            "play main_theme;";

        System.out.println("--- Test 1: Required Specification DSL Input ---");
        System.out.println("Input Source:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(requiredInput);
        System.out.println("--------------------------------------------------------------------------------");

        scanAndPrintTokens(requiredInput);

        // Additional Test 2: Accidentals, Repeats, Comments, and Various Durations
        String advancedInput =
            "// Advanced MelodyScript test with accidentals and comments\n" +
            "tempo 144;\n" +
            "instrument violin;\n" +
            "define complex_melody {\n" +
            "    C#4 eighth, Eb5 half, A4 whole, Bb3 sixteenth, R thirtysecond\n" +
            "}\n" +
            "repeat 4 {\n" +
            "    play complex_melody;\n" +
            "}";

        System.out.println("\n--- Test 2: Accidentals, Durations, Comments, and Repeat ---");
        System.out.println("Input Source:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(advancedInput);
        System.out.println("--------------------------------------------------------------------------------");

        scanAndPrintTokens(advancedInput);

        // Additional Test 3: Error Handling Demonstration
        System.out.println("\n--- Test 3: Lexical Error Handling Demonstration ---");
        String illegalInput = "tempo 120;\nplay @theme;";
        System.out.println("Input with illegal character '@':");
        System.out.println(illegalInput);
        System.out.println("Lexer output:");
        try {
            Lexer errorLexer = new Lexer(illegalInput);
            errorLexer.tokenize();
            System.out.println("FAIL: Expected LexicalException was not thrown.");
        } catch (LexicalException ex) {
            System.out.println("SUCCESS: Caught expected exception -> " + ex.getMessage());
            System.out.println("  Line:   " + ex.getLine());
            System.out.println("  Column: " + ex.getColumn());
        }
    }

    /**
     * Scans an input string using Lexer and prints each token line-by-line.
     *
     * @param source the source DSL string
     */
    public static void scanAndPrintTokens(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        System.out.printf("%-4s | %-20s | %-14s | %-12s | %-15s | %-10s%n",
                "#", "Token Type", "Lexeme", "Pitch (MIDI)", "Duration Ticks", "Line:Col");
        System.out.println("--------------------------------------------------------------------------------");

        int index = 1;
        for (Token token : tokens) {
            String pitchStr = token.hasPitch()
                    ? String.valueOf(token.getPitchValue())
                    : (token.getPitchValue() == -1 ? "-1 (N/A)" : String.valueOf(token.getPitchValue()));

            String durationStr = token.hasDuration()
                    ? String.valueOf(token.getDurationTicks())
                    : (token.getDurationTicks() == -1 ? "-1 (N/A)" : String.valueOf(token.getDurationTicks()));

            String positionStr = String.format("[%d:%d]", token.getLine(), token.getColumn());

            System.out.printf("%-4d | %-20s | %-14s | %-12s | %-15s | %-10s%n",
                    index++,
                    token.getType(),
                    "\"" + token.getLexeme() + "\"",
                    pitchStr,
                    durationStr,
                    positionStr);
        }
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Total Tokens Scanned: " + tokens.size());
    }
}
