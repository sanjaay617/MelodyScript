/**
 * MelodyScript Lexical Analyzer
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.Objects;

/**
 * Represents a lexical token in the MelodyScript DSL.
 * Each token captures its syntactic category (TokenType), original lexeme,
 * optional pitch value (for NOTE_PITCH), optional duration ticks (for NOTE_DURATION),
 * and exact source coordinates (1-based line and column).
 */
public class Token {

    /**
     * Enumeration of all token categories in MelodyScript DSL.
     */
    public enum TokenType {
        // Keywords
        KEYWORD_TEMPO,       // "tempo"
        KEYWORD_INSTRUMENT,  // "instrument"
        KEYWORD_DEFINE,      // "define"
        KEYWORD_PLAY,        // "play"
        KEYWORD_REPEAT,      // "repeat"

        // Literals
        NOTE_PITCH,          // Pitch literals, e.g. C0 to G9, C#4, Eb5 (parsed to MIDI 0-127)
        NOTE_DURATION,       // Duration literals: "quarter", "eighth", "half", "whole"
        REST,                // Rest literal: "R"
        NUMBER,              // Positive integer literals, e.g. 120, 4
        IDENTIFIER,          // Alphanumeric/underscore identifier names, e.g. piano, main_theme

        // Delimiters
        SEMICOLON,           // ";"
        COMMA,               // ","
        LBRACE,              // "{"
        RBRACE,              // "}"

        // Control
        EOF,                 // End of file / input stream
        UNKNOWN              // Unrecognized / error token placeholder
    }

    private final TokenType type;
    private final String lexeme;
    private final int pitchValue;      // 0-127 for NOTE_PITCH, or -1 for non-pitch tokens
    private final int durationTicks;   // e.g. whole=1920, half=960, quarter=480, eighth=240, or -1
    private final int line;            // 1-based line coordinate in source
    private final int column;          // 1-based column coordinate in source

    /**
     * Constructs a fully specified Token.
     *
     * @param type          the token type
     * @param lexeme        the textual representation from source
     * @param pitchValue    the MIDI pitch value (0-127), or -1 if not applicable
     * @param durationTicks the duration ticks (e.g., 480 for quarter), or -1 if not applicable
     * @param line          the 1-based line number where this token begins
     * @param column        the 1-based column number where this token begins
     */
    public Token(TokenType type, String lexeme, int pitchValue, int durationTicks, int line, int column) {
        this.type = Objects.requireNonNull(type, "TokenType cannot be null");
        this.lexeme = Objects.requireNonNull(lexeme, "Lexeme cannot be null");
        this.pitchValue = pitchValue;
        this.durationTicks = durationTicks;
        this.line = line;
        this.column = column;
    }

    /**
     * Convenience constructor for tokens without pitch or duration attributes.
     *
     * @param type   the token type
     * @param lexeme the textual representation from source
     * @param line   the 1-based line number where this token begins
     * @param column the 1-based column number where this token begins
     */
    public Token(TokenType type, String lexeme, int line, int column) {
        this(type, lexeme, -1, -1, line, column);
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getPitchValue() {
        return pitchValue;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /**
     * Checks if this token carries a valid MIDI pitch value (0-127).
     *
     * @return true if pitchValue is between 0 and 127 inclusive
     */
    public boolean hasPitch() {
        return pitchValue >= 0 && pitchValue <= 127;
    }

    /**
     * Checks if this token carries a valid duration in ticks (> 0).
     *
     * @return true if durationTicks > 0
     */
    public boolean hasDuration() {
        return durationTicks > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token token)) return false;
        return pitchValue == token.pitchValue &&
               durationTicks == token.durationTicks &&
               line == token.line &&
               column == token.column &&
               type == token.type &&
               lexeme.equals(token.lexeme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lexeme, pitchValue, durationTicks, line, column);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Token{");
        sb.append("type=").append(type);
        sb.append(", lexeme='").append(lexeme).append('\'');
        if (pitchValue >= 0) {
            sb.append(", pitch=").append(pitchValue);
        } else {
            sb.append(", pitch=-1");
        }
        if (durationTicks >= 0) {
            sb.append(", ticks=").append(durationTicks);
        } else {
            sb.append(", ticks=-1");
        }
        sb.append(", line=").append(line);
        sb.append(", col=").append(column);
        sb.append('}');
        return sb.toString();
    }
}
