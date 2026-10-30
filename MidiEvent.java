/**
 * MelodyScript Intermediate Representation (IR)
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.Objects;

/**
 * Represents a flattened, chronological timeline MIDI event in the MelodyScript IR.
 * Each event captures its absolute tick timestamp (PPQ 480), event type,
 * pitch information, velocity, duration, channel, and metadata.
 */
public class MidiEvent implements Comparable<MidiEvent> {

    /**
     * Types of timeline MIDI events in the IR.
     */
    public enum EventType {
        TEMPO_META,      // Meta event setting playback tempo in BPM
        PROGRAM_CHANGE,  // General MIDI instrument patch change
        NOTE_OFF,        // Note off event (velocity 0)
        NOTE_ON,         // Note on event (velocity > 0)
        END_OF_TRACK     // Final track boundary delimiter
    }

    private final long absoluteTick;
    private final EventType eventType;
    private final int pitch;              // 0-127, or -1 for non-note events
    private final String pitchStr;        // e.g. "C4", "A4", or "REST" / "N/A"
    private final int velocity;           // 100 for NOTE_ON, 0 for NOTE_OFF, 0 for others
    private final int durationTicks;      // Duration in ticks (480 PPQ), or 0 for instant
    private final int channel;            // MIDI Channel (default 0)
    private final int programNumber;      // GM Instrument program number (0-127), or -1
    private final int bpm;                // Tempo in BPM for TEMPO_META, or -1

    /**
     * Constructs a fully specified MidiEvent.
     */
    public MidiEvent(long absoluteTick, EventType eventType, int pitch, String pitchStr,
                     int velocity, int durationTicks, int channel, int programNumber, int bpm) {
        this.absoluteTick = absoluteTick;
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.pitch = pitch;
        this.pitchStr = pitchStr != null ? pitchStr : "N/A";
        this.velocity = velocity;
        this.durationTicks = durationTicks;
        this.channel = channel;
        this.programNumber = programNumber;
        this.bpm = bpm;
    }

    // Static Factory Methods for Clean Event Construction

    public static MidiEvent noteOn(long tick, int pitch, String pitchStr, int velocity, int durationTicks, int channel) {
        return new MidiEvent(tick, EventType.NOTE_ON, pitch, pitchStr, velocity, durationTicks, channel, -1, -1);
    }

    public static MidiEvent noteOff(long tick, int pitch, String pitchStr, int channel) {
        return new MidiEvent(tick, EventType.NOTE_OFF, pitch, pitchStr, 0, 0, channel, -1, -1);
    }

    public static MidiEvent programChange(long tick, int programNumber, int channel) {
        return new MidiEvent(tick, EventType.PROGRAM_CHANGE, -1, "N/A", 0, 0, channel, programNumber, -1);
    }

    public static MidiEvent tempoMeta(long tick, int bpm) {
        return new MidiEvent(tick, EventType.TEMPO_META, -1, "N/A", 0, 0, 0, -1, bpm);
    }

    public static MidiEvent endOfTrack(long tick) {
        return new MidiEvent(tick, EventType.END_OF_TRACK, -1, "N/A", 0, 0, 0, -1, -1);
    }

    // Getters

    public long getAbsoluteTick() {
        return absoluteTick;
    }

    public EventType getEventType() {
        return eventType;
    }

    public int getPitch() {
        return pitch;
    }

    public String getPitchStr() {
        return pitchStr;
    }

    public int getVelocity() {
        return velocity;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getChannel() {
        return channel;
    }

    public int getProgramNumber() {
        return programNumber;
    }

    public int getBpm() {
        return bpm;
    }

    /**
     * Deterministic chronological comparator:
     * 1. Primary order: absoluteTick ascending
     * 2. Secondary order (tie-break at same tick):
     *    TEMPO_META (0) -> PROGRAM_CHANGE (1) -> NOTE_OFF (2) -> NOTE_ON (3) -> END_OF_TRACK (4)
     * 3. Tertiary order: pitch ascending
     */
    @Override
    public int compareTo(MidiEvent other) {
        int tickCmp = Long.compare(this.absoluteTick, other.absoluteTick);
        if (tickCmp != 0) return tickCmp;

        int typeCmp = Integer.compare(typePriority(this.eventType), typePriority(other.eventType));
        if (typeCmp != 0) return typeCmp;

        return Integer.compare(this.pitch, other.pitch);
    }

    private static int typePriority(EventType type) {
        return switch (type) {
            case TEMPO_META -> 0;
            case PROGRAM_CHANGE -> 1;
            case NOTE_OFF -> 2;
            case NOTE_ON -> 3;
            case END_OF_TRACK -> 4;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MidiEvent that)) return false;
        return absoluteTick == that.absoluteTick &&
               pitch == that.pitch &&
               velocity == that.velocity &&
               durationTicks == that.durationTicks &&
               channel == that.channel &&
               programNumber == that.programNumber &&
               bpm == that.bpm &&
               eventType == that.eventType &&
               pitchStr.equals(that.pitchStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absoluteTick, eventType, pitch, pitchStr, velocity, durationTicks, channel, programNumber, bpm);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%05d] %-14s", absoluteTick, eventType));

        if (eventType == EventType.NOTE_ON || eventType == EventType.NOTE_OFF) {
            sb.append(String.format(" %-4s (MIDI %3d) dur=%-4d vel=%-3d ch=%d",
                    pitchStr, pitch, durationTicks, velocity, channel));
        } else if (eventType == EventType.PROGRAM_CHANGE) {
            sb.append(String.format(" Program #%-3d ch=%d", programNumber, channel));
        } else if (eventType == EventType.TEMPO_META) {
            sb.append(String.format(" %d BPM", bpm));
        } else if (eventType == EventType.END_OF_TRACK) {
            sb.append(" Final Track Boundary");
        }
        return sb.toString();
    }
}
