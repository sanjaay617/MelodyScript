/**
 * MelodyScript Compiler CLI Driver
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-Line Interface (CLI) driver for the MelodyScript DSL compiler.
 * Orchestrates the complete compilation pipeline:
 * Source -> Lexer -> Parser -> SemanticAnalyzer -> IRGenerator -> MidiFileWriter -> Binary MIDI File.
 *
 * <p>Usage:
 * <pre>
 * java Main &lt;input.ms&gt; [output.mid] [--ast] [--ir] [--hex]
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0 || hasFlag(args, "-h") || hasFlag(args, "--help")) {
            printUsage();
            return;
        }

        boolean showAst = hasFlag(args, "--ast");
        boolean showIr = hasFlag(args, "--ir");
        boolean showHex = hasFlag(args, "--hex");

        List<String> positionalArgs = extractPositionalArgs(args);

        if (positionalArgs.isEmpty()) {
            System.err.println("Error: Missing input MelodyScript source file (.ms).");
            printUsage();
            System.exit(1);
        }

        String inputFilePath = positionalArgs.get(0);
        String outputFilePath = positionalArgs.size() > 1
                ? positionalArgs.get(1)
                : deriveOutputFilename(inputFilePath);

        compile(inputFilePath, outputFilePath, showAst, showIr, showHex);
    }

    /**
     * Executes the complete compiler pipeline on the specified input file.
     */
    public static boolean compile(String inputFilePath, String outputFilePath,
                                  boolean showAst, boolean showIr, boolean showHex) {
        Path inputPath = Path.of(inputFilePath);
        if (!Files.exists(inputPath)) {
            System.err.printf("Error: Input file '%s' does not exist.%n", inputFilePath);
            return false;
        }

        String source;
        try {
            source = Files.readString(inputPath);
        } catch (IOException e) {
            System.err.printf("Error: Failed to read input file '%s': %s%n", inputFilePath, e.getMessage());
            return false;
        }

        System.out.println("================================================================================");
        System.out.printf("Compiling MelodyScript: %s -> %s%n", inputFilePath, outputFilePath);
        System.out.println("================================================================================");

        try {
            // Stage 1: Lexical Analysis
            System.out.print("[1/5] Lexical Analysis... ");
            Lexer lexer = new Lexer(source);
            System.out.println("Done.");

            // Stage 2: Syntactic Analysis (Parser)
            System.out.print("[2/5] Syntactic Analysis (LL(1) Parser)... ");
            Parser parser = new Parser(lexer);
            ProgramNode ast = parser.parse();
            System.out.println("Done.");

            if (showAst) {
                System.out.println("\n--------------------------------------------------------------------------------");
                System.out.println("Abstract Syntax Tree (--ast):");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.print(ast.renderTree(0));
                System.out.println("--------------------------------------------------------------------------------\n");
            }

            // Stage 3: Semantic Analysis
            System.out.print("[3/5] Semantic Analysis & Scoped Symbol Resolution... ");
            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(ast);
            SymbolTable symbolTable = analyzer.getSymbolTable();
            System.out.println("Done.");

            // Stage 4: Intermediate Representation Generation
            System.out.print("[4/5] IR Generation (Timeline Event Flattener)... ");
            IRGenerator irGenerator = new IRGenerator(ast, symbolTable);
            List<MidiEvent> events = irGenerator.generate();
            System.out.println("Done.");

            if (showIr) {
                System.out.println("\n--------------------------------------------------------------------------------");
                System.out.println("Chronological MIDI Event Timeline (--ir):");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.print(irGenerator.renderTimelineTable());
                System.out.println("--------------------------------------------------------------------------------\n");
            }

            // Stage 5: Binary MIDI Code Generation
            System.out.print("[5/5] Backend Byte-Writer (SMF Format 0)... ");
            byte[] midiBytes = MidiFileWriter.generateMidiBytes(events);
            MidiFileWriter.writeToFile(events, outputFilePath);
            System.out.println("Done.");

            if (showHex) {
                System.out.println("\n--------------------------------------------------------------------------------");
                System.out.println("Binary Hexadecimal Dump (--hex):");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.print(MidiFileWriter.formatHexDump(midiBytes));
                System.out.println("--------------------------------------------------------------------------------\n");
            }

            // Compilation Summary
            File outFile = new File(outputFilePath);
            long fileSize = outFile.exists() ? outFile.length() : midiBytes.length;
            long totalTicks = irGenerator.getTotalTicks();
            int bpm = irGenerator.getBpm();
            double seconds = irGenerator.getPlaybackSeconds();

            System.out.println("================================================================================");
            System.out.println(">>> COMPILATION SUCCEEDED! <<<");
            System.out.printf("  * Output File:     %s (%d bytes)%n", outputFilePath, fileSize);
            System.out.printf("  * Total Ticks:     %d ticks (480 PPQ)%n", totalTicks);
            System.out.printf("  * Tempo:           %d BPM%n", bpm);
            System.out.printf("  * Playback Length: %.2f seconds (%.2f beats)%n", seconds, totalTicks / 480.0);
            System.out.printf("  * Total Events:    %d MIDI events written%n", events.size());
            System.out.println("================================================================================");

            return true;

        } catch (LexicalException e) {
            System.err.println("\n[LEXICAL ERROR]");
            System.err.printf("  Location: Line %d, Column %d%n", e.getLine(), e.getColumn());
            System.err.printf("  Details:  %s%n", e.getMessage());
            return false;
        } catch (SyntaxException e) {
            System.err.println("\n[SYNTAX ERROR]");
            System.err.printf("  Location: Line %d, Column %d%n", e.getLine(), e.getColumn());
            System.err.printf("  Expected: %s%n", e.getExpected());
            System.err.printf("  Found:    %s%n", e.getFound());
            System.err.printf("  Details:  %s%n", e.getMessage());
            return false;
        } catch (SemanticException e) {
            System.err.println("\n[SEMANTIC ERROR]");
            System.err.printf("  Location: Line %d, Column %d%n", e.getLine(), e.getColumn());
            System.err.printf("  Rule:     %s%n", e.getRuleDescription());
            System.err.printf("  Offender: %s%n", e.getOffenderDetails());
            System.err.printf("  Details:  %s%n", e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("\n[INTERNAL COMPILER ERROR]");
            System.err.printf("  Unexpected exception: %s%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) return true;
        }
        return false;
    }

    private static List<String> extractPositionalArgs(String[] args) {
        List<String> list = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                list.add(arg);
            }
        }
        return list;
    }

    private static String deriveOutputFilename(String inputPath) {
        int dot = inputPath.lastIndexOf('.');
        if (dot > 0) {
            return inputPath.substring(0, dot) + ".mid";
        }
        return inputPath + ".mid";
    }

    private static void printUsage() {
        System.out.println("MelodyScript DSL Compiler (Pure Java JDK 17+)");
        System.out.println("Usage:");
        System.out.println("  java Main <input.ms> [output.mid] [options]");
        System.out.println("\nOptions:");
        System.out.println("  --ast    Print formatted ASCII Abstract Syntax Tree (AST) visualization");
        System.out.println("  --ir     Print chronological Intermediate Representation (IR) timeline table");
        System.out.println("  --hex    Print formatted hexadecimal dump of the generated binary MIDI file");
        System.out.println("  -h, --help Print this help information");
        System.out.println("\nExample:");
        System.out.println("  java Main song.ms song.mid --ast --ir --hex");
    }
}
