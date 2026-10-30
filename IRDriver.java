/**
 * MelodyScript Intermediate Representation (IR) Driver
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.List;

/**
 * Driver demonstration class for the MelodyScript IR Generator.
 * Executes the complete compiler front-end pipeline:
 * Lexer -> Parser -> SemanticAnalyzer -> IRGenerator.
 * Prints the flattened chronological event timeline, total tick duration,
 * and estimated audio playback time in seconds.
 */
public class IRDriver {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("           MelodyScript Intermediate Representation (IR) Generator              ");
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

        System.out.println("--- Input MelodyScript Program ---");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(validSource);
        System.out.println("--------------------------------------------------------------------------------");

        try {
            // Step 1: Lexical Analysis
            System.out.println("[Step 1/4] Lexical Analysis (Scanning tokens)...");
            Lexer lexer = new Lexer(validSource);

            // Step 2: Syntactic Analysis
            System.out.println("[Step 2/4] Syntactic Analysis (Building AST)...");
            Parser parser = new Parser(lexer);
            ProgramNode ast = parser.parse();

            // Step 3: Semantic Analysis
            System.out.println("[Step 3/4] Semantic Analysis (Validating domain rules & SymbolTable)...");
            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(ast);
            SymbolTable symbolTable = analyzer.getSymbolTable();

            // Step 4: Intermediate Representation Generation
            System.out.println("[Step 4/4] IR Generation (Unrolling phrases/loops into chronological timeline)...");
            IRGenerator irGenerator = new IRGenerator(ast, symbolTable);
            List<MidiEvent> events = irGenerator.generate();

            System.out.println("\n>>> Chronological MIDI Event Timeline (IR) <<<");
            System.out.println("==================================================================================================");
            System.out.print(irGenerator.renderTimelineTable());
            System.out.println("==================================================================================================");

            // Summary Metrics
            long totalTicks = irGenerator.getTotalTicks();
            int bpm = irGenerator.getBpm();
            double seconds = irGenerator.getPlaybackSeconds();

            System.out.println("Timeline Summary Statistics:");
            System.out.printf("  * Total Events Emitted:     %d events%n", events.size());
            System.out.printf("  * Total Ticks (at 480 PPQ): %d ticks%n", totalTicks);
            System.out.printf("  * Composition Tempo:        %d BPM%n", bpm);
            System.out.printf("  * Total Quarter Beats:      %.2f beats%n", totalTicks / 480.0);
            System.out.printf("  * Estimated Playback Time:  %.3f seconds%n", seconds);
            System.out.printf("    Formula: (totalTicks / 480.0) * (60.0 / bpm) => (%.1f / 480.0) * (60.0 / %d) = %.3fs%n",
                    (double) totalTicks, bpm, seconds);

        } catch (Exception e) {
            System.err.println("Pipeline failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
