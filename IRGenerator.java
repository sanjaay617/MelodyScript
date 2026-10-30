/**
 * MelodyScript Intermediate Representation (IR) Generator
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generates a flattened, chronological timeline of MIDI events (Intermediate Representation)
 * from a validated AST and SymbolTable.
 * Unrolls named phrase calls and repeat loops into linear, sequentially ordered MidiEvents.
 */
public class IRGenerator {

    private final ProgramNode program;
    private final SymbolTable symbolTable;
    private final List<MidiEvent> events = new ArrayList<>();

    private long currentTick = 0;
    private int currentChannel = 0;
    private int currentBpm = 120;
    private int currentProgram = 0;
    private boolean isGenerated = false;

    /**
     * Constructs an IRGenerator with an AST and SymbolTable.
     *
     * @param program     the validated ProgramNode AST
     * @param symbolTable the populated SymbolTable
     */
    public IRGenerator(ProgramNode program, SymbolTable symbolTable) {
        this.program = Objects.requireNonNull(program, "ProgramNode cannot be null");
        this.symbolTable = Objects.requireNonNull(symbolTable, "SymbolTable cannot be null");
    }

    /**
     * Flattens and unrolls the entire AST into a chronological list of MidiEvents.
     *
     * @return unmodifiable sorted list of timeline MidiEvents
     */
    public List<MidiEvent> generate() {
        if (isGenerated) {
            return Collections.unmodifiableList(events);
        }

        currentTick = 0;
        events.clear();

        for (ASTNode statement : program.getStatements()) {
            if (statement instanceof TempoNode tempoNode) {
                currentBpm = tempoNode.getBpm();
                events.add(MidiEvent.tempoMeta(currentTick, currentBpm));
            } else if (statement instanceof InstrumentNode instrumentNode) {
                int programNumber = SymbolTable.resolveInstrument(instrumentNode.getInstrumentName());
                currentProgram = programNumber >= 0 ? programNumber : 0;
                events.add(MidiEvent.programChange(currentTick, currentProgram, currentChannel));
            } else if (statement instanceof PhraseDefNode) {
                // Phrase definitions are stored in SymbolTable; unrolled on 'play'
            } else if (statement instanceof PlayNode playNode) {
                processPlay(playNode);
            }
        }

        // Emit final END_OF_TRACK marker at total tick duration
        events.add(MidiEvent.endOfTrack(currentTick));

        // Sort events deterministically by absoluteTick and event priority
        Collections.sort(events);
        isGenerated = true;

        return Collections.unmodifiableList(events);
    }

    /**
     * Unrolls play statements (phrase calls or repeat loops).
     */
    private void processPlay(PlayNode play) {
        if (play.isRepeat()) {
            int count = play.getRepeatCount();
            NoteSequenceNode seq = play.getInlineSequence();
            if (seq != null) {
                for (int i = 0; i < count; i++) {
                    processNoteSequence(seq);
                }
            }
        } else {
            String phraseName = play.getPhraseName();
            PhraseDefNode definition = symbolTable.lookup(phraseName);
            if (definition != null && definition.getNoteSequence() != null) {
                processNoteSequence(definition.getNoteSequence());
            }
        }
    }

    /**
     * Sequentially generates NOTE_ON/NOTE_OFF pairs and advances the currentTick cursor.
     */
    private void processNoteSequence(NoteSequenceNode sequence) {
        if (sequence == null) return;

        for (ASTNode noteOrRest : sequence.getNotes()) {
            if (noteOrRest instanceof NoteNode note) {
                int pitch = note.getPitchValue();
                String pitchStr = note.getPitchStr();
                int duration = note.getDurationTicks();

                // 1. Emit NOTE_ON at start tick with velocity 100
                events.add(MidiEvent.noteOn(currentTick, pitch, pitchStr, 100, duration, currentChannel));

                // 2. Advance timeline cursor
                currentTick += duration;

                // 3. Emit NOTE_OFF at end tick with velocity 0
                events.add(MidiEvent.noteOff(currentTick, pitch, pitchStr, currentChannel));

            } else if (noteOrRest instanceof RestNode rest) {
                // Rests advance currentTick without emitting sound events
                currentTick += rest.getDurationTicks();
            }
        }
    }

    /**
     * Total composition duration in ticks (480 PPQ).
     */
    public long getTotalTicks() {
        if (!isGenerated) generate();
        return currentTick;
    }

    /**
     * Active composition tempo in BPM.
     */
    public int getBpm() {
        return currentBpm;
    }

    /**
     * Calculates estimated playback duration in seconds.
     * Formula: (totalTicks / 480.0) * (60.0 / bpm)
     */
    public double getPlaybackSeconds() {
        long total = getTotalTicks();
        double bpm = currentBpm > 0 ? currentBpm : 120.0;
        return (total / 480.0) * (60.0 / bpm);
    }

    /**
     * Renders a formatted tabular timeline representation of all generated IR events.
     */
    public String renderTimelineTable() {
        List<MidiEvent> list = generate();
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("%-5s | %-12s | %-16s | %-8s | %-12s | %-8s | %-8s | %-8s%n",
                "#", "Tick Offset", "Event Type", "Pitch", "MIDI Note #", "Velocity", "Duration", "Channel"));
        sb.append("------------------------------------------------------------------------------------------------\n");

        int index = 1;
        for (MidiEvent event : list) {
            String pitchStr = event.getPitch() >= 0 ? event.getPitchStr() : "-";
            String midiNote = event.getPitch() >= 0 ? String.valueOf(event.getPitch()) : "-";
            String duration = event.getDurationTicks() > 0 ? String.valueOf(event.getDurationTicks()) : "-";
            String velocity = (event.getEventType() == MidiEvent.EventType.NOTE_ON || event.getEventType() == MidiEvent.EventType.NOTE_OFF)
                    ? String.valueOf(event.getVelocity()) : "-";
            String channel = (event.getEventType() != MidiEvent.EventType.TEMPO_META && event.getEventType() != MidiEvent.EventType.END_OF_TRACK)
                    ? String.valueOf(event.getChannel()) : "-";

            sb.append(String.format("%-5d | %-12d | %-16s | %-8s | %-12s | %-8s | %-8s | %-8s%n",
                    index++,
                    event.getAbsoluteTick(),
                    event.getEventType(),
                    pitchStr,
                    midiNote,
                    velocity,
                    duration,
                    channel));
        }

        return sb.toString();
    }
}
