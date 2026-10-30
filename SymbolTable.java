/**
 * MelodyScript Scoped Symbol Table
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Scoped symbol table for MelodyScript.
 * Stores phrase declarations mapping identifier names to their AST subtree (PhraseDefNode).
 * Supports hierarchical scope nesting and General MIDI Instrument resolution.
 */
public class SymbolTable {

    private final SymbolTable parent;
    private final Map<String, PhraseDefNode> symbols = new LinkedHashMap<>();

    // General MIDI Level 1 Instrument Lookup Table (0-127)
    private static final Map<String, Integer> GENERAL_MIDI_MAP = new HashMap<>();

    static {
        // Piano (0-7)
        registerInstrument(0, "acoustic_grand_piano", "piano", "grand_piano");
        registerInstrument(1, "bright_acoustic_piano", "bright_piano");
        registerInstrument(2, "electric_grand_piano");
        registerInstrument(3, "honky_tonk_piano", "honky_tonk");
        registerInstrument(4, "electric_piano_1", "electric_piano", "rhodes_piano");
        registerInstrument(5, "electric_piano_2", "chorus_electric_piano", "dx_piano");
        registerInstrument(6, "harpsichord");
        registerInstrument(7, "clavinet");

        // Chromatic Percussion (8-15)
        registerInstrument(8, "celesta");
        registerInstrument(9, "glockenspiel");
        registerInstrument(10, "music_box");
        registerInstrument(11, "vibraphone", "vibes");
        registerInstrument(12, "marimba");
        registerInstrument(13, "xylophone");
        registerInstrument(14, "tubular_bells");
        registerInstrument(15, "dulcimer");

        // Organ (16-23)
        registerInstrument(16, "drawbar_organ", "organ");
        registerInstrument(17, "percussive_organ");
        registerInstrument(18, "rock_organ");
        registerInstrument(19, "church_organ");
        registerInstrument(20, "reed_organ");
        registerInstrument(21, "accordion");
        registerInstrument(22, "harmonica");
        registerInstrument(23, "tango_accordion");

        // Guitar (24-31)
        registerInstrument(24, "acoustic_guitar_nylon", "acoustic_guitar", "guitar", "nylon_guitar");
        registerInstrument(25, "acoustic_guitar_steel", "steel_guitar");
        registerInstrument(26, "electric_guitar_jazz", "jazz_guitar");
        registerInstrument(27, "electric_guitar_clean", "clean_guitar");
        registerInstrument(28, "electric_guitar_muted", "muted_guitar");
        registerInstrument(29, "overdriven_guitar", "overdrive_guitar");
        registerInstrument(30, "distortion_guitar");
        registerInstrument(31, "guitar_harmonics");

        // Bass (32-39)
        registerInstrument(32, "acoustic_bass", "bass");
        registerInstrument(33, "electric_bass_finger", "finger_bass");
        registerInstrument(34, "electric_bass_pick", "pick_bass");
        registerInstrument(35, "fretless_bass");
        registerInstrument(36, "slap_bass_1");
        registerInstrument(37, "slap_bass_2");
        registerInstrument(38, "synth_bass_1");
        registerInstrument(39, "synth_bass_2");

        // Strings (40-47)
        registerInstrument(40, "violin");
        registerInstrument(41, "viola");
        registerInstrument(42, "cello");
        registerInstrument(43, "contrabass", "double_bass");
        registerInstrument(44, "tremolo_strings");
        registerInstrument(45, "pizzicato_strings");
        registerInstrument(46, "orchestral_harp", "harp");
        registerInstrument(47, "timpani");

        // Ensemble (48-55)
        registerInstrument(48, "string_ensemble_1", "strings", "string_ensemble");
        registerInstrument(49, "string_ensemble_2", "slow_strings");
        registerInstrument(50, "synth_strings_1");
        registerInstrument(51, "synth_strings_2");
        registerInstrument(52, "choir_aahs", "choir");
        registerInstrument(53, "voice_oohs");
        registerInstrument(54, "synth_voice");
        registerInstrument(55, "orchestra_hit");

        // Brass (56-63)
        registerInstrument(56, "trumpet");
        registerInstrument(57, "trombone");
        registerInstrument(58, "tuba");
        registerInstrument(59, "muted_trumpet");
        registerInstrument(60, "french_horn", "horn");
        registerInstrument(61, "brass_section", "brass");
        registerInstrument(62, "synth_brass_1");
        registerInstrument(63, "synth_brass_2");

        // Reed (64-71)
        registerInstrument(64, "soprano_sax");
        registerInstrument(65, "alto_sax", "sax", "saxophone");
        registerInstrument(66, "tenor_sax");
        registerInstrument(67, "baritone_sax");
        registerInstrument(68, "oboe");
        registerInstrument(69, "english_horn");
        registerInstrument(70, "bassoon");
        registerInstrument(71, "clarinet");

        // Pipe (72-79)
        registerInstrument(72, "piccolo");
        registerInstrument(73, "flute");
        registerInstrument(74, "recorder");
        registerInstrument(75, "pan_flute");
        registerInstrument(76, "blown_bottle");
        registerInstrument(77, "shakuhachi");
        registerInstrument(78, "whistle");
        registerInstrument(79, "ocarina");

        // Synth Lead (80-87)
        registerInstrument(80, "lead_1_square", "synth_lead", "lead_square", "square_lead");
        registerInstrument(81, "lead_2_sawtooth", "lead_sawtooth", "sawtooth_lead");
        registerInstrument(82, "lead_3_calliope");
        registerInstrument(83, "lead_4_chiff");
        registerInstrument(84, "lead_5_charang");
        registerInstrument(85, "lead_6_voice");
        registerInstrument(86, "lead_7_fifths");
        registerInstrument(87, "lead_8_bass_lead");

        // Synth Pad (88-95)
        registerInstrument(88, "pad_1_new_age", "synth_pad", "new_age_pad");
        registerInstrument(89, "pad_2_warm", "warm_pad");
        registerInstrument(90, "pad_3_polysynth");
        registerInstrument(91, "pad_4_choir");
        registerInstrument(92, "pad_5_bowed");
        registerInstrument(93, "pad_6_metallic");
        registerInstrument(94, "pad_7_halo");
        registerInstrument(95, "pad_8_sweep");
    }

    private static void registerInstrument(int programNumber, String... names) {
        for (String name : names) {
            String normalized = normalize(name);
            GENERAL_MIDI_MAP.put(normalized, programNumber);
        }
    }

    private static String normalize(String name) {
        if (name == null) return "";
        return name.toLowerCase().trim().replace(" ", "_").replace("-", "_");
    }

    /**
     * Resolves an instrument name to its General MIDI Level 1 program number (0-127).
     *
     * @param name instrument name (case and punctuation insensitive)
     * @return MIDI program number 0-127, or -1 if unmapped/unknown
     */
    public static int resolveInstrument(String name) {
        if (name == null) return -1;
        String key = normalize(name);
        return GENERAL_MIDI_MAP.getOrDefault(key, -1);
    }

    /**
     * Creates a root/global symbol table.
     */
    public SymbolTable() {
        this(null);
    }

    /**
     * Creates a nested child symbol table with a parent scope.
     *
     * @param parent enclosing parent scope, or null for root
     */
    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    /**
     * Returns the parent enclosing scope.
     *
     * @return parent SymbolTable, or null if root
     */
    public SymbolTable getParent() {
        return parent;
    }

    /**
     * Enters a new nested scope.
     *
     * @return newly created child SymbolTable
     */
    public SymbolTable enterScope() {
        return new SymbolTable(this);
    }

    /**
     * Exits the current scope and returns its parent scope.
     *
     * @return parent scope, or this if already root
     */
    public SymbolTable exitScope() {
        return parent != null ? parent : this;
    }

    /**
     * Defines a new phrase symbol in the current scope.
     *
     * @param name phrase identifier
     * @param node AST subtree for the phrase definition
     * @return true if successfully defined; false if already defined in the current scope
     */
    public boolean define(String name, PhraseDefNode node) {
        Objects.requireNonNull(name, "Phrase name cannot be null");
        Objects.requireNonNull(node, "PhraseDefNode cannot be null");
        if (symbols.containsKey(name)) {
            return false;
        }
        symbols.put(name, node);
        return true;
    }

    /**
     * Checks if a phrase is defined in the current scope (without checking ancestors).
     *
     * @param name phrase identifier
     * @return true if defined in current scope
     */
    public boolean containsCurrentScope(String name) {
        return symbols.containsKey(name);
    }

    /**
     * Looks up a phrase by name in the current scope, cascading upward to ancestors.
     *
     * @param name phrase identifier
     * @return PhraseDefNode if found; null otherwise
     */
    public PhraseDefNode lookup(String name) {
        if (symbols.containsKey(name)) {
            return symbols.get(name);
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return null;
    }

    /**
     * Returns an unmodifiable view of the symbols declared in this immediate scope.
     *
     * @return Map of phrase name to PhraseDefNode
     */
    public Map<String, PhraseDefNode> getSymbols() {
        return Collections.unmodifiableMap(symbols);
    }

    /**
     * Returns the total count of symbols defined in this scope.
     */
    public int size() {
        return symbols.size();
    }
}
