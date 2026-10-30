/**
 * MelodyScript Semantic Analysis Driver
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.Map;

/**
 * Driver demonstration class for the MelodyScript Semantic Analyzer.
 * Executes the full Lexer -> Parser -> SemanticAnalyzer front-end pipeline,
 * displays resolved instrument mappings and symbol table bindings, and
 * demonstrates interception of all required semantic errors.
 */
public class SemanticDriver {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("                 MelodyScript Semantic Analyzer & Symbol Table                  ");
        System.out.println("================================================================================");

        // Required Valid DSL Input
        String validSource =
            "tempo 120;\n" +
            "instrument piano;\n" +
            "define main_theme {\n" +
            "    C4 quarter, E4 quarter, G4 quarter, R quarter\n" +
            "}\n" +
            "play main_theme;\n" +
            "play repeat 2 { A4 eighth, B4 eighth };";

        System.out.println("--- Test 1: Full Pipeline on Valid Input ---");
        System.out.println("Source Input:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(validSource);
        System.out.println("--------------------------------------------------------------------------------");

        try {
            Lexer lexer = new Lexer(validSource);
            Parser parser = new Parser(lexer);
            ProgramNode program = parser.parse();

            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(program);

            System.out.println(">>> Semantic Analysis Passed! <<<");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Symbol Table Bindings:");
            for (Map.Entry<String, PhraseDefNode> entry : analyzer.getSymbolTable().getSymbols().entrySet()) {
                PhraseDefNode def = entry.getValue();
                System.out.printf("  * Phrase \"%s\" -> Defined at line %d, col %d (%d notes/rests)%n",
                        entry.getKey(), def.getLine(), def.getColumn(), def.getNoteSequence().getNotes().size());
            }

            System.out.println("\nResolved Instruments:");
            for (Map.Entry<String, Integer> entry : analyzer.getResolvedInstruments().entrySet()) {
                System.out.printf("  * \"%s\" -> General MIDI Program #%d%n", entry.getKey(), entry.getValue());
            }

            System.out.println("\nDiagnostic Metrics:");
            System.out.print(analyzer.getDiagnosticsReport());
            System.out.println("--------------------------------------------------------------------------------");

        } catch (Exception e) {
            System.err.println("Unexpected failure in Test 1: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n--- Semantic Error Demonstrations ---");

        // Error 1: Unbound phrase call (play undefined_phrase;)
        runErrorTest(
            "1. Unbound Phrase Call",
            "tempo 120;\ninstrument piano;\nplay undefined_phrase;"
        );

        // Error 2: Invalid non-positive tempo (tempo 0;)
        runErrorTest(
            "2. Invalid Non-Positive Tempo",
            "tempo 0;\ninstrument piano;"
        );

        // Error 3: Unknown instrument name (instrument alien_synth;)
        runErrorTest(
            "3. Unknown Instrument Name",
            "tempo 120;\ninstrument alien_synth;\ndefine theme { C4 quarter }\nplay theme;"
        );

        // Error 4: Duplicate phrase definition (define theme { C4 quarter } define theme { E4 quarter })
        runErrorTest(
            "4. Duplicate Phrase Definition",
            "define theme {\n    C4 quarter\n}\ndefine theme {\n    E4 quarter\n}\nplay theme;"
        );
    }

    private static void runErrorTest(String testTitle, String source) {
        System.out.println("\n[Testing " + testTitle + "]");
        System.out.println("Input Code:\n" + source);
        try {
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer);
            ProgramNode program = parser.parse();

            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(program);

            System.out.println("FAIL: Expected SemanticException was NOT thrown!");
        } catch (SemanticException ex) {
            System.out.println("SUCCESS: Correctly intercepted SemanticException:");
            System.out.println("  -> Message:  " + ex.getMessage());
            System.out.println("  -> Rule:     " + ex.getRuleDescription());
            System.out.println("  -> Offender: " + ex.getOffenderDetails());
            System.out.println("  -> Location: Line " + ex.getLine() + ", Column " + ex.getColumn());
        } catch (Exception ex) {
            System.out.println("UNEXPECTED ERROR: " + ex);
        }
    }
}
