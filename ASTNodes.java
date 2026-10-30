/**
 * MelodyScript Abstract Syntax Tree (AST) Nodes
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Container class and namespace for MelodyScript AST components.
 */
public class ASTNodes {
    private ASTNodes() {
        // Utility container
    }
}

/**
 * Base abstract class for all AST nodes in MelodyScript.
 * Provides coordinates tracking and recursive formatted ASCII tree visualization.
 */
abstract class ASTNode {

    private final int line;
    private final int column;

    /**
     * Constructs an ASTNode with source coordinates.
     *
     * @param line   1-based line number in source
     * @param column 1-based column number in source
     */
    public ASTNode(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /**
     * Renders a formatted ASCII tree representation of this node and its sub-tree.
     *
     * @param indent indentation level (0 for root)
     * @return multiline formatted string representing the sub-tree
     */
    public abstract String renderTree(int indent);

    /**
     * Internal helper to render the sub-tree with full branch connectors.
     *
     * @param prefix      indentation prefix string
     * @param isTail      whether this node is the last sibling in its parent list
     * @param isRootChild whether this is directly under root
     * @return formatted tree fragment
     */
    protected abstract String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild);

    /**
     * Formats 1-based source coordinate tag.
     */
    protected String coordString() {
        return "[" + line + ":" + column + "]";
    }
}

/**
 * Root AST node representing a complete MelodyScript program.
 */
class ProgramNode extends ASTNode {

    private final List<ASTNode> statements;

    public ProgramNode(List<ASTNode> statements, int line, int column) {
        super(line, column);
        this.statements = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(statements, "statements cannot be null")));
    }

    public ProgramNode(List<ASTNode> statements) {
        this(statements, statements.isEmpty() ? 1 : statements.get(0).getLine(), statements.isEmpty() ? 1 : statements.get(0).getColumn());
    }

    public List<ASTNode> getStatements() {
        return statements;
    }

    @Override
    public String renderTree(int indent) {
        StringBuilder sb = new StringBuilder();
        String baseIndent = "  ".repeat(Math.max(0, indent));
        sb.append(baseIndent).append("ProgramNode ").append(coordString()).append("\n");

        for (int i = 0; i < statements.size(); i++) {
            boolean isLast = (i == statements.size() - 1);
            sb.append(statements.get(i).renderTreeInternal(baseIndent, isLast, true));
        }
        return sb.toString();
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        return renderTree(0);
    }
}

/**
 * AST node for "tempo <BPM>;" statements.
 */
class TempoNode extends ASTNode {

    private final int bpm;

    public TempoNode(int bpm, int line, int column) {
        super(line, column);
        this.bpm = bpm;
    }

    public int getBpm() {
        return bpm;
    }

    @Override
    public String renderTree(int indent) {
        return "  ".repeat(Math.max(0, indent)) + "TempoNode: bpm=" + bpm + " " + coordString() + "\n";
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        String branch = isTail ? "└── " : "├── ";
        return prefix + branch + "TempoNode: bpm=" + bpm + " " + coordString() + "\n";
    }
}

/**
 * AST node for "instrument <IDENTIFIER>;" statements.
 */
class InstrumentNode extends ASTNode {

    private final String instrumentName;

    public InstrumentNode(String instrumentName, int line, int column) {
        super(line, column);
        this.instrumentName = Objects.requireNonNull(instrumentName, "instrumentName cannot be null");
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    @Override
    public String renderTree(int indent) {
        return "  ".repeat(Math.max(0, indent)) + "InstrumentNode: instrument=\"" + instrumentName + "\" " + coordString() + "\n";
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        String branch = isTail ? "└── " : "├── ";
        return prefix + branch + "InstrumentNode: instrument=\"" + instrumentName + "\" " + coordString() + "\n";
    }
}

/**
 * AST node for "define <IDENTIFIER> { <NoteSequence> }" phrase definition statements.
 */
class PhraseDefNode extends ASTNode {

    private final String phraseName;
    private final NoteSequenceNode noteSequence;

    public PhraseDefNode(String phraseName, NoteSequenceNode noteSequence, int line, int column) {
        super(line, column);
        this.phraseName = Objects.requireNonNull(phraseName, "phraseName cannot be null");
        this.noteSequence = Objects.requireNonNull(noteSequence, "noteSequence cannot be null");
    }

    public String getPhraseName() {
        return phraseName;
    }

    public NoteSequenceNode getNoteSequence() {
        return noteSequence;
    }

    @Override
    public String renderTree(int indent) {
        StringBuilder sb = new StringBuilder();
        String baseIndent = "  ".repeat(Math.max(0, indent));
        sb.append(baseIndent).append("PhraseDefNode: phrase=\"").append(phraseName).append("\" ").append(coordString()).append("\n");
        sb.append(noteSequence.renderTreeInternal(baseIndent + "  ", true, false));
        return sb.toString();
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        StringBuilder sb = new StringBuilder();
        String branch = isTail ? "└── " : "├── ";
        sb.append(prefix).append(branch).append("PhraseDefNode: phrase=\"").append(phraseName).append("\" ").append(coordString()).append("\n");

        String childPrefix = prefix + (isTail ? "    " : "│   ");
        sb.append(noteSequence.renderTreeInternal(childPrefix, true, false));
        return sb.toString();
    }
}

/**
 * AST node for "play <IDENTIFIER>;" or "play repeat <NUMBER> { <NoteSequence> };".
 */
class PlayNode extends ASTNode {

    private final String phraseName;                // Phrase call identifier, or null if repeat block
    private final int repeatCount;                  // Repeat count if repeat block, else 1
    private final NoteSequenceNode inlineSequence;  // Inline sequence if repeat block, else null
    private final boolean isRepeat;                 // True if repeat block, false if phrase call

    public PlayNode(String phraseName, int repeatCount, NoteSequenceNode inlineSequence, boolean isRepeat, int line, int column) {
        super(line, column);
        this.phraseName = phraseName;
        this.repeatCount = repeatCount;
        this.inlineSequence = inlineSequence;
        this.isRepeat = isRepeat;
    }

    public String getPhraseName() {
        return phraseName;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public NoteSequenceNode getInlineSequence() {
        return inlineSequence;
    }

    public boolean isRepeat() {
        return isRepeat;
    }

    @Override
    public String renderTree(int indent) {
        StringBuilder sb = new StringBuilder();
        String baseIndent = "  ".repeat(Math.max(0, indent));
        if (isRepeat) {
            sb.append(baseIndent).append("PlayNode: repeat ").append(repeatCount).append(" times ").append(coordString()).append("\n");
            if (inlineSequence != null) {
                sb.append(inlineSequence.renderTreeInternal(baseIndent + "  ", true, false));
            }
        } else {
            sb.append(baseIndent).append("PlayNode: phrase=\"").append(phraseName).append("\" ").append(coordString()).append("\n");
        }
        return sb.toString();
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        StringBuilder sb = new StringBuilder();
        String branch = isTail ? "└── " : "├── ";
        if (isRepeat) {
            sb.append(prefix).append(branch).append("PlayNode: repeat ").append(repeatCount).append(" times ").append(coordString()).append("\n");
            if (inlineSequence != null) {
                String childPrefix = prefix + (isTail ? "    " : "│   ");
                sb.append(inlineSequence.renderTreeInternal(childPrefix, true, false));
            }
        } else {
            sb.append(prefix).append(branch).append("PlayNode: phrase=\"").append(phraseName).append("\" ").append(coordString()).append("\n");
        }
        return sb.toString();
    }
}

/**
 * AST node containing an ordered sequence of NoteNode and/or RestNode elements.
 */
class NoteSequenceNode extends ASTNode {

    private final List<ASTNode> notes;

    public NoteSequenceNode(List<ASTNode> notes, int line, int column) {
        super(line, column);
        this.notes = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(notes, "notes cannot be null")));
    }

    public NoteSequenceNode(List<ASTNode> notes) {
        this(notes, notes.isEmpty() ? 1 : notes.get(0).getLine(), notes.isEmpty() ? 1 : notes.get(0).getColumn());
    }

    public List<ASTNode> getNotes() {
        return notes;
    }

    @Override
    public String renderTree(int indent) {
        StringBuilder sb = new StringBuilder();
        String baseIndent = "  ".repeat(Math.max(0, indent));
        sb.append(baseIndent).append("NoteSequenceNode (").append(notes.size()).append(" elements) ").append(coordString()).append("\n");
        for (int i = 0; i < notes.size(); i++) {
            boolean isLast = (i == notes.size() - 1);
            sb.append(notes.get(i).renderTreeInternal(baseIndent + "  ", isLast, false));
        }
        return sb.toString();
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        StringBuilder sb = new StringBuilder();
        String branch = isTail ? "└── " : "├── ";
        sb.append(prefix).append(branch).append("NoteSequenceNode (").append(notes.size()).append(" elements) ").append(coordString()).append("\n");

        String childPrefix = prefix + (isTail ? "    " : "│   ");
        for (int i = 0; i < notes.size(); i++) {
            boolean isLast = (i == notes.size() - 1);
            sb.append(notes.get(i).renderTreeInternal(childPrefix, isLast, false));
        }
        return sb.toString();
    }
}

/**
 * AST node representing a musical pitched note (pitch and duration).
 */
class NoteNode extends ASTNode {

    private final String pitchStr;
    private final int pitchValue;
    private final String durationStr;
    private final int durationTicks;

    public NoteNode(String pitchStr, int pitchValue, String durationStr, int durationTicks, int line, int column) {
        super(line, column);
        this.pitchStr = Objects.requireNonNull(pitchStr, "pitchStr cannot be null");
        this.pitchValue = pitchValue;
        this.durationStr = Objects.requireNonNull(durationStr, "durationStr cannot be null");
        this.durationTicks = durationTicks;
    }

    public String getPitchStr() {
        return pitchStr;
    }

    public int getPitchValue() {
        return pitchValue;
    }

    public String getDurationStr() {
        return durationStr;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    @Override
    public String renderTree(int indent) {
        return "  ".repeat(Math.max(0, indent)) +
               "NoteNode: pitch=\"" + pitchStr + "\" (MIDI: " + pitchValue + "), duration=\"" +
               durationStr + "\" (" + durationTicks + " ticks) " + coordString() + "\n";
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        String branch = isTail ? "└── " : "├── ";
        return prefix + branch + "NoteNode: pitch=\"" + pitchStr + "\" (MIDI: " + pitchValue + "), duration=\"" +
               durationStr + "\" (" + durationTicks + " ticks) " + coordString() + "\n";
    }
}

/**
 * AST node representing a musical rest (silence for a specified duration).
 */
class RestNode extends ASTNode {

    private final String durationStr;
    private final int durationTicks;

    public RestNode(String durationStr, int durationTicks, int line, int column) {
        super(line, column);
        this.durationStr = Objects.requireNonNull(durationStr, "durationStr cannot be null");
        this.durationTicks = durationTicks;
    }

    public String getDurationStr() {
        return durationStr;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    @Override
    public String renderTree(int indent) {
        return "  ".repeat(Math.max(0, indent)) +
               "RestNode: duration=\"" + durationStr + "\" (" + durationTicks + " ticks) " + coordString() + "\n";
    }

    @Override
    protected String renderTreeInternal(String prefix, boolean isTail, boolean isRootChild) {
        String branch = isTail ? "└── " : "├── ";
        return prefix + branch + "RestNode: duration=\"" + durationStr + "\" (" + durationTicks + " ticks) " + coordString() + "\n";
    }
}
