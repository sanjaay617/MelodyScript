/**
 * MelodyScript Lexical Analyzer
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Character-by-character lexical analyzer for the MelodyScript DSL.
 * Scans tokens, tracks 1-based line and column coordinates, ignores comments
 * and whitespace, computes exact MIDI pitch numbers (0-127), and throws
 * {@link LexicalException} on encountering illegal characters or malformed tokens.
 */
public class Lexer {

    private final String source;
    private int cursor = 0;
    private int line = 1;
    private int column = 1;

    /**
     * Constructs a Lexer for the given source input string.
     *
     * @param source the MelodyScript DSL source code
     */
    public Lexer(String source) {
        this.source = Objects.requireNonNull(source, "Source code cannot be null");
    }

    /**
     * Returns the full original source code.
     *
     * @return source code string
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the current line coordinate in the source.
     *
     * @return 1-based line number
     */
    public int getLine() {
        return line;
    }

    /**
     * Returns the current column coordinate in the source.
     *
     * @return 1-based column number
     */
    public int getColumn() {
        return column;
    }

    /**
     * Tokenizes the entire source string into a list of tokens up to and including EOF.
     *
     * @return list of scanned tokens ending with TokenType.EOF
     * @throws LexicalException if any unrecognized character or syntax violation is found
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = nextToken();
            tokens.add(token);
        } while (token.getType() != Token.TokenType.EOF);
        return tokens;
    }

    /**
     * Scans and returns the next lexical token from the source stream.
     * When the end of the input is reached, returns a token with type TokenType.EOF.
     *
     * @return the next Token
     * @throws LexicalException on lexical errors
     */
    public Token nextToken() {
        skipWhitespaceAndComments();

        if (isAtEnd()) {
            return new Token(Token.TokenType.EOF, "", -1, -1, line, column);
        }

        int startLine = line;
        int startCol = column;
        char c = peek();

        // 1. Delimiters
        if (c == ';') {
            advance();
            return new Token(Token.TokenType.SEMICOLON, ";", startLine, startCol);
        }
        if (c == ',') {
            advance();
            return new Token(Token.TokenType.COMMA, ",", startLine, startCol);
        }
        if (c == '{') {
            advance();
            return new Token(Token.TokenType.LBRACE, "{", startLine, startCol);
        }
        if (c == '}') {
            advance();
            return new Token(Token.TokenType.RBRACE, "}", startLine, startCol);
        }

        // 2. NOTE_PITCH literal check (e.g. C0 to G9, C#4, Eb5)
        if (isPitchAtCursor()) {
            return scanPitch(startLine, startCol);
        }

        // 3. Numbers (positive integers)
        if (Character.isDigit(c)) {
            return scanNumber(startLine, startCol);
        }

        // 4. Words: Keywords, Note Durations, Rest ("R"), or Identifiers
        if (isIdentifierStart(c)) {
            return scanWordOrIdentifier(startLine, startCol);
        }

        // 5. Unrecognized / illegal character
        char illegal = advance();
        throw new LexicalException(illegal, startLine, startCol);
    }

    /**
     * Skips whitespace (spaces, tabs, newlines) and single-line comments starting with "//".
     * Accurately updates 1-based line and column counters across \n, \r\n, and \r.
     */
    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t') {
                advance();
            } else if (c == '\r') {
                advance();
                if (!isAtEnd() && peek() == '\n') {
                    advance();
                }
                line++;
                column = 1;
            } else if (c == '\n') {
                advance();
                line++;
                column = 1;
            } else if (c == '/' && peekNext() == '/') {
                // Consume both '/' characters
                advance();
                advance();
                // Skip everything until newline or EOF
                while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
                    advance();
                }
                // The newline itself will be handled in the next loop cycle
            } else {
                break;
            }
        }
    }

    /**
     * Checks if the character sequence at the cursor matches a NOTE_PITCH literal.
     * Pattern: Note letter [A-Ga-g], optional accidental [#b], octave digit [0-9],
     * not followed immediately by an identifier character (word boundary check).
     */
    private boolean isPitchAtCursor() {
        char c = peek();
        if (!isNoteLetter(c)) {
            return false;
        }

        // Case A: Note + accidental + octave (e.g., C#4, Eb5)
        char next = peekNext();
        if (isAccidental(next)) {
            char oct = peekOffset(2);
            if (Character.isDigit(oct)) {
                char after = peekOffset(3);
                return !isIdentifierPart(after);
            }
            return false;
        }

        // Case B: Note + octave without accidental (e.g., C4, A4, G9, C0)
        if (Character.isDigit(next)) {
            char after = peekOffset(2);
            return !isIdentifierPart(after);
        }

        return false;
    }

    /**
     * Scans a NOTE_PITCH token and computes its exact MIDI pitch value (0-127).
     */
    private Token scanPitch(int startLine, int startCol) {
        int startPos = cursor;
        advance(); // Consume base note letter (e.g. 'C')
        if (isAccidental(peek())) {
            advance(); // Consume accidental ('#' or 'b')
        }
        advance(); // Consume octave digit ('0'-'9')

        String lexeme = source.substring(startPos, cursor);
        int pitchValue = parsePitchMidi(lexeme, startLine, startCol);
        return new Token(Token.TokenType.NOTE_PITCH, lexeme, pitchValue, -1, startLine, startCol);
    }

    /**
     * Parses a pitch lexeme into its exact MIDI note number (0 to 127).
     * Middle C (C4) resolves to 60, A4 resolves to 69, C#4 to 61, Eb5 to 75.
     *
     * @param lexeme   the note pitch string
     * @param line     source line coordinate for error reporting
     * @param column   source column coordinate for error reporting
     * @return MIDI note number between 0 and 127
     * @throws LexicalException if pitch is malformed or out of the 0-127 range
     */
    public static int parsePitchMidi(String lexeme, int line, int column) {
        if (lexeme == null || lexeme.length() < 2 || lexeme.length() > 3) {
            throw new LexicalException("Malformed pitch string: '" + lexeme + "'", line, column);
        }

        char noteChar = Character.toUpperCase(lexeme.charAt(0));
        int baseOffset = switch (noteChar) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> throw new LexicalException("Invalid note pitch letter: '" + noteChar + "'", line, column);
        };

        int accidentalOffset = 0;
        int octaveCharIndex;

        if (lexeme.length() == 3) {
            char acc = lexeme.charAt(1);
            if (acc == '#' || acc == '\u266F') {
                accidentalOffset = 1;
            } else if (acc == 'b' || acc == '\u266D') {
                accidentalOffset = -1;
            } else {
                throw new LexicalException("Unrecognized pitch accidental: '" + acc + "'", line, column);
            }
            octaveCharIndex = 2;
        } else {
            octaveCharIndex = 1;
        }

        char octChar = lexeme.charAt(octaveCharIndex);
        if (!Character.isDigit(octChar)) {
            throw new LexicalException("Pitch octave must be a digit (0-9), found: '" + octChar + "'", line, column);
        }
        int octave = octChar - '0';

        // Formula: MIDI Note = (octave + 1) * 12 + baseNoteOffset + accidentalOffset
        int midiPitch = (octave + 1) * 12 + baseOffset + accidentalOffset;

        if (midiPitch < 0 || midiPitch > 127) {
            throw new LexicalException(
                String.format("Pitch '%s' computes to MIDI note %d, exceeding valid MIDI range (0-127)", lexeme, midiPitch),
                line, column
            );
        }

        return midiPitch;
    }

    /**
     * Scans a positive integer literal token.
     */
    private Token scanNumber(int startLine, int startCol) {
        int startPos = cursor;
        while (!isAtEnd() && Character.isDigit(peek())) {
            advance();
        }

        // Identifier characters immediately following digits are invalid (e.g. 120abc)
        if (!isAtEnd() && isIdentifierStart(peek())) {
            throw new LexicalException(
                "Invalid number literal: identifier characters cannot immediately follow digits",
                startLine, startCol
            );
        }

        String lexeme = source.substring(startPos, cursor);
        try {
            int value = Integer.parseInt(lexeme);
            if (value < 0) {
                throw new LexicalException("Negative integer literals are not supported: " + lexeme, startLine, startCol);
            }
            return new Token(Token.TokenType.NUMBER, lexeme, -1, -1, startLine, startCol);
        } catch (NumberFormatException e) {
            throw new LexicalException("Integer literal exceeds 32-bit integer range: " + lexeme, startLine, startCol);
        }
    }

    /**
     * Scans a word and classifies it as a keyword, note duration, rest ("R"), or identifier.
     */
    private Token scanWordOrIdentifier(int startLine, int startCol) {
        int startPos = cursor;
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }

        String word = source.substring(startPos, cursor);

        // Keywords
        switch (word) {
            case "tempo" -> {
                return new Token(Token.TokenType.KEYWORD_TEMPO, word, startLine, startCol);
            }
            case "instrument" -> {
                return new Token(Token.TokenType.KEYWORD_INSTRUMENT, word, startLine, startCol);
            }
            case "define" -> {
                return new Token(Token.TokenType.KEYWORD_DEFINE, word, startLine, startCol);
            }
            case "play" -> {
                return new Token(Token.TokenType.KEYWORD_PLAY, word, startLine, startCol);
            }
            case "repeat" -> {
                return new Token(Token.TokenType.KEYWORD_REPEAT, word, startLine, startCol);
            }
        }

        // Note Durations (with ticks per MIDI PPQ 480 standard)
        switch (word) {
            case "whole" -> {
                return new Token(Token.TokenType.NOTE_DURATION, word, -1, 1920, startLine, startCol);
            }
            case "half" -> {
                return new Token(Token.TokenType.NOTE_DURATION, word, -1, 960, startLine, startCol);
            }
            case "quarter" -> {
                return new Token(Token.TokenType.NOTE_DURATION, word, -1, 480, startLine, startCol);
            }
            case "eighth" -> {
                return new Token(Token.TokenType.NOTE_DURATION, word, -1, 240, startLine, startCol);
            }
            case "sixteenth" -> {
                return new Token(Token.TokenType.NOTE_DURATION, word, -1, 120, startLine, startCol);
            }
            case "thirtysecond" -> {
                return new Token(Token.TokenType.NOTE_DURATION, word, -1, 60, startLine, startCol);
            }
        }

        // Rest
        if ("R".equals(word)) {
            return new Token(Token.TokenType.REST, word, startLine, startCol);
        }

        // General alphanumeric/underscore identifier
        return new Token(Token.TokenType.IDENTIFIER, word, startLine, startCol);
    }

    /**
     * Checks if a character is a standard musical note letter (A-G, case-insensitive).
     */
    private static boolean isNoteLetter(char c) {
        return (c >= 'A' && c <= 'G') || (c >= 'a' && c <= 'g');
    }

    /**
     * Checks if a character is a note accidental (sharp '#' or flat 'b', or Unicode ♯/♭).
     */
    private static boolean isAccidental(char c) {
        return c == '#' || c == 'b' || c == '\u266F' || c == '\u266D';
    }

    /**
     * Checks if a character can legally start an identifier name.
     */
    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    /**
     * Checks if a character can legally continue an identifier name.
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Checks if the cursor has reached the end of the input source.
     */
    private boolean isAtEnd() {
        return cursor >= source.length();
    }

    /**
     * Returns the current character at the cursor without consuming it,
     * or '\0' if at the end of input.
     */
    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(cursor);
    }

    /**
     * Returns the character at cursor + 1 without consuming it,
     * or '\0' if out of bounds.
     */
    private char peekNext() {
        return peekOffset(1);
    }

    /**
     * Returns the character at cursor + offset without consuming it,
     * or '\0' if out of bounds.
     */
    private char peekOffset(int offset) {
        int idx = cursor + offset;
        return (idx >= source.length() || idx < 0) ? '\0' : source.charAt(idx);
    }

    /**
     * Consumes and returns the current character, advancing the cursor and column.
     */
    private char advance() {
        char c = source.charAt(cursor++);
        column++;
        return c;
    }
}
