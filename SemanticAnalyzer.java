/**
 * MelodyScript Semantic Analyzer
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Semantic analyzer for MelodyScript.
 * Traverses the AST produced by {@link Parser}, enforces static domain constraints,
 * manages the {@link SymbolTable}, and gathers comprehensive diagnostic metrics.
 */
public class SemanticAnalyzer {

    private final SymbolTable symbolTable;
    private final List<String> validatedSymbols = new ArrayList<>();
    private final Map<String, Integer> resolvedInstruments = new LinkedHashMap<>();

    // Diagnostic Metrics
    private int statementCount = 0;
    private int tempoBpm = -1;
    private int totalPhrasesDefined = 0;
    private int totalPlayDirectives = 0;
    private int totalNotesCount = 0;
    private int totalRestsCount = 0;
    private int minPitch = Integer.MAX_VALUE;
    private int maxPitch = Integer.MIN_VALUE;
    private int totalTicks = 0;

    /**
     * Constructs a SemanticAnalyzer with a fresh root symbol table.
     */
    public SemanticAnalyzer() {
        this(new SymbolTable());
    }

    /**
     * Constructs a SemanticAnalyzer with a custom symbol table.
     *
     * @param symbolTable base symbol table
     */
    public SemanticAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = Objects.requireNonNull(symbolTable, "SymbolTable cannot be null");
    }

    /**
     * Traverses and validates a ProgramNode AST.
     *
     * @param program the root ProgramNode AST
     * @return this analyzer instance after successful validation
     * @throws SemanticException on semantic rule violation
     */
    public SemanticAnalyzer analyze(ProgramNode program) {
        Objects.requireNonNull(program, "ProgramNode cannot be null");

        for (ASTNode statement : program.getStatements()) {
            statementCount++;
            analyzeStatement(statement);
        }

        return this;
    }

    private void analyzeStatement(ASTNode statement) {
        if (statement instanceof TempoNode tempoNode) {
            analyzeTempo(tempoNode);
        } else if (statement instanceof InstrumentNode instrumentNode) {
            analyzeInstrument(instrumentNode);
        } else if (statement instanceof PhraseDefNode phraseDefNode) {
            analyzePhraseDef(phraseDefNode);
        } else if (statement instanceof PlayNode playNode) {
            analyzePlay(playNode);
        } else {
            throw new SemanticException(
                "Unknown Statement Node",
                "Unsupported AST statement node type: " + statement.getClass().getSimpleName(),
                statement
            );
        }
    }

    /**
     * Rule 4 (Positive Tempo): Asserts that TempoNode bpm > 0.
     */
    private void analyzeTempo(TempoNode tempo) {
        if (tempo.getBpm() <= 0) {
            throw new SemanticException(
                "Rule 4 (Positive Tempo)",
                "Tempo BPM must be positive, found " + tempo.getBpm(),
                tempo
            );
        }
        this.tempoBpm = tempo.getBpm();
    }

    /**
     * Rule 5 (Instrument Resolution): Validates that InstrumentNode instrumentName exists in General MIDI map.
     */
    private void analyzeInstrument(InstrumentNode instrument) {
        String name = instrument.getInstrumentName();
        int programNumber = SymbolTable.resolveInstrument(name);

        if (programNumber < 0 || programNumber > 127) {
            throw new SemanticException(
                "Rule 5 (Instrument Resolution)",
                "Unknown instrument '" + name + "' cannot be mapped to General MIDI (0-127)",
                instrument
            );
        }

        resolvedInstruments.put(name, programNumber);
    }

    /**
     * Rule 2 (Duplicate Definition): Throws SemanticException if a phrase is defined more than once in the same scope.
     * Also recursively validates the note sequence.
     */
    private void analyzePhraseDef(PhraseDefNode phraseDef) {
        String phraseName = phraseDef.getPhraseName();

        if (symbolTable.containsCurrentScope(phraseName)) {
            PhraseDefNode existing = symbolTable.lookup(phraseName);
            int origLine = existing != null ? existing.getLine() : -1;
            throw new SemanticException(
                "Rule 2 (Duplicate Definition)",
                String.format("Duplicate phrase definition '%s' (previously declared at line %d)", phraseName, origLine),
                phraseDef
            );
        }

        symbolTable.define(phraseName, phraseDef);
        validatedSymbols.add(phraseName);
        totalPhrasesDefined++;

        analyzeNoteSequence(phraseDef.getNoteSequence());
    }

    /**
     * Rule 1 (Declaration-Before-Use): Every play <phrase_name> directive MUST reference a phrase
     * defined previously in the Symbol Table.
     */
    private void analyzePlay(PlayNode play) {
        totalPlayDirectives++;

        if (play.isRepeat()) {
            if (play.getRepeatCount() <= 0) {
                throw new SemanticException(
                    "Rule 4 (Positive Repeat Count)",
                    "Repeat loop count must be positive, found " + play.getRepeatCount(),
                    play
                );
            }
            if (play.getInlineSequence() != null) {
                analyzeNoteSequence(play.getInlineSequence());
            }
        } else {
            String phraseName = play.getPhraseName();
            PhraseDefNode definition = symbolTable.lookup(phraseName);

            if (definition == null) {
                throw new SemanticException(
                    "Rule 1 (Declaration-Before-Use)",
                    "Phrase '" + phraseName + "' is played but has not been defined in the current scope",
                    play
                );
            }
        }
    }

    /**
     * Rule 3 (Pitch Bounds): Asserts that all NoteNode pitch values satisfy 0 <= pitch <= 127.
     */
    private void analyzeNoteSequence(NoteSequenceNode sequence) {
        if (sequence == null) return;

        for (ASTNode noteOrRest : sequence.getNotes()) {
            if (noteOrRest instanceof NoteNode note) {
                totalNotesCount++;
                int pitch = note.getPitchValue();

                if (pitch < 0 || pitch > 127) {
                    throw new SemanticException(
                        "Rule 3 (Pitch Bounds)",
                        String.format("Pitch '%s' with MIDI value %d is out of valid range (0-127)", note.getPitchStr(), pitch),
                        note
                    );
                }

                if (note.getDurationTicks() <= 0) {
                    throw new SemanticException(
                        "Duration Validation",
                        "Note duration ticks must be positive, found " + note.getDurationTicks(),
                        note
                    );
                }

                minPitch = Math.min(minPitch, pitch);
                maxPitch = Math.max(maxPitch, pitch);
                totalTicks += note.getDurationTicks();

            } else if (noteOrRest instanceof RestNode rest) {
                totalRestsCount++;

                if (rest.getDurationTicks() <= 0) {
                    throw new SemanticException(
                        "Duration Validation",
                        "Rest duration ticks must be positive, found " + rest.getDurationTicks(),
                        rest
                    );
                }

                totalTicks += rest.getDurationTicks();
            }
        }
    }

    // Getters for Diagnostics and Testing
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public List<String> getValidatedSymbols() {
        return Collections.unmodifiableList(validatedSymbols);
    }

    public Map<String, Integer> getResolvedInstruments() {
        return Collections.unmodifiableMap(resolvedInstruments);
    }

    public int getStatementCount() {
        return statementCount;
    }

    public int getTempoBpm() {
        return tempoBpm;
    }

    public int getTotalPhrasesDefined() {
        return totalPhrasesDefined;
    }

    public int getTotalPlayDirectives() {
        return totalPlayDirectives;
    }

    public int getTotalNotesCount() {
        return totalNotesCount;
    }

    public int getTotalRestsCount() {
        return totalRestsCount;
    }

    public int getMinPitch() {
        return minPitch == Integer.MAX_VALUE ? -1 : minPitch;
    }

    public int getMaxPitch() {
        return maxPitch == Integer.MIN_VALUE ? -1 : maxPitch;
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    /**
     * Formats a summary of diagnostic metrics for reporting.
     */
    public String getDiagnosticsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Statements Validated:    ").append(statementCount).append("\n");
        sb.append("Configured Tempo:        ").append(tempoBpm > 0 ? tempoBpm + " BPM" : "Not Set").append("\n");
        sb.append("Resolved Instruments:    ").append(resolvedInstruments.size()).append(" (");
        resolvedInstruments.forEach((name, prog) -> sb.append(name).append(" -> GM ").append(prog).append(", "));
        if (!resolvedInstruments.isEmpty()) sb.setLength(sb.length() - 2);
        sb.append(")\n");
        sb.append("Defined Phrases:         ").append(validatedSymbols).append("\n");
        sb.append("Play Directives:         ").append(totalPlayDirectives).append("\n");
        sb.append("Total Pitched Notes:     ").append(totalNotesCount).append("\n");
        sb.append("Total Rest Notes:        ").append(totalRestsCount).append("\n");
        if (minPitch != Integer.MAX_VALUE) {
            sb.append("MIDI Pitch Range:        [").append(minPitch).append(" to ").append(maxPitch).append("]\n");
        }
        sb.append("Total Duration Ticks:    ").append(totalTicks).append(" ticks\n");
        return sb.toString();
    }
}
