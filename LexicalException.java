/**
 * MelodyScript Lexical Analyzer
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

/**
 * Custom runtime exception thrown when the lexical analyzer encounters
 * an illegal character, malformed literal, or unrecognized sequence.
 * Includes precise 1-based line and column coordinates for diagnostic reporting.
 */
public class LexicalException extends RuntimeException {

    private final int line;
    private final int column;

    /**
     * Constructs a new LexicalException with descriptive message and source coordinates.
     *
     * @param message error description
     * @param line    1-based line number where the error occurred
     * @param column  1-based column number where the error occurred
     */
    public LexicalException(String message, int line, int column) {
        super(String.format("Lexical error at [%d:%d]: %s", line, column, message));
        this.line = line;
        this.column = column;
    }

    /**
     * Constructs a new LexicalException with an offending character and source coordinates.
     *
     * @param unexpectedChar the illegal character encountered
     * @param line           1-based line number
     * @param column         1-based column number
     */
    public LexicalException(char unexpectedChar, int line, int column) {
        this(String.format("Unrecognized character '%c' (Unicode: U+%04X)", unexpectedChar, (int) unexpectedChar), line, column);
    }

    /**
     * Constructs a new LexicalException with cause, message, and coordinates.
     *
     * @param message error description
     * @param cause   underlying cause
     * @param line    1-based line number
     * @param column  1-based column number
     */
    public LexicalException(String message, Throwable cause, int line, int column) {
        super(String.format("Lexical error at [%d:%d]: %s", line, column, message), cause);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
