/**
 * MelodyScript Parser Driver
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

/**
 * Driver demonstration class for the MelodyScript predictive LL(1) recursive-descent parser.
 * Tests valid AST generation, renders the ASCII AST visualizer, and demonstrates
 * syntax error interception with precise coordinate reporting.
 */
public class ParserDriver {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("              MelodyScript Predictive LL(1) Parser & AST Visualizer             ");
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

        System.out.println("--- Test 1: Parsing Valid MelodyScript DSL Input ---");
        System.out.println("Source Code:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(validSource);
        System.out.println("--------------------------------------------------------------------------------");

        try {
            Lexer lexer = new Lexer(validSource);
            Parser parser = new Parser(lexer);
            ProgramNode programNode = parser.parse();

            System.out.println("Parse Succeeded! Generated Abstract Syntax Tree (AST):");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.print(programNode.renderTree(0));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Top-level statement count: " + programNode.getStatements().size());
        } catch (Exception e) {
            System.err.println("Unexpected failure in Test 1: " + e.getMessage());
            e.printStackTrace();
        }

        // Test 2: Syntax Error Interception - Missing Semicolon in Tempo Statement
        System.out.println("\n--- Test 2: Syntax Error Interception (Missing Semicolon after 'tempo 120') ---");
        String missingSemicolonSource =
            "tempo 120\n" +
            "instrument piano;";

        System.out.println("Malformed Input:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(missingSemicolonSource);
        System.out.println("--------------------------------------------------------------------------------");
        runMalformedTest("Missing Semicolon", missingSemicolonSource);

        // Test 3: Syntax Error Interception - Missing Closing Brace in Phrase Definition
        System.out.println("\n--- Test 3: Syntax Error Interception (Unclosed Brace in Phrase Definition) ---");
        String unclosedBraceSource =
            "define intro {\n" +
            "    C4 quarter, D4 quarter\n" +
            "play intro;";

        System.out.println("Malformed Input:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(unclosedBraceSource);
        System.out.println("--------------------------------------------------------------------------------");
        runMalformedTest("Unclosed Brace", unclosedBraceSource);

        // Test 4: Syntax Error Interception - Missing Duration in Note Sequence
        System.out.println("\n--- Test 4: Syntax Error Interception (Missing Duration Literal) ---");
        String missingDurationSource =
            "define melody {\n" +
            "    C4, E4 quarter\n" +
            "}";

        System.out.println("Malformed Input:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(missingDurationSource);
        System.out.println("--------------------------------------------------------------------------------");
        runMalformedTest("Missing Duration Literal", missingDurationSource);

        // Test 5: Syntax Error Interception - Missing Identifier/Repeat after 'play'
        System.out.println("\n--- Test 5: Syntax Error Interception (Empty 'play' Statement) ---");
        String emptyPlaySource = "play ;";

        System.out.println("Malformed Input:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(emptyPlaySource);
        System.out.println("--------------------------------------------------------------------------------");
        runMalformedTest("Empty Play", emptyPlaySource);
    }

    /**
     * Executes parser on malformed input and prints the intercepted SyntaxException details.
     */
    private static void runMalformedTest(String label, String source) {
        try {
            Parser parser = new Parser(source);
            parser.parse();
            System.out.println("FAIL [" + label + "]: Expected SyntaxException was NOT thrown!");
        } catch (SyntaxException ex) {
            System.out.println("SUCCESS [" + label + "]: Correctly intercepted SyntaxException:");
            System.out.println("  -> Error Message: " + ex.getMessage());
            System.out.println("  -> Line:          " + ex.getLine());
            System.out.println("  -> Column:        " + ex.getColumn());
            System.out.println("  -> Expected:      " + ex.getExpected());
            System.out.println("  -> Found:         " + ex.getFound());
        } catch (Exception ex) {
            System.out.println("UNEXPECTED [" + label + "]: Caught non-syntax exception: " + ex);
        }
    }
}
