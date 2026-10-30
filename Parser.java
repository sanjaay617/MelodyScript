/**
 * MelodyScript Predictive LL(1) Recursive-Descent Parser
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Predictive LL(1) recursive-descent parser for MelodyScript.
 * Consumes tokens from {@link Lexer} using single-token lookahead and constructs
 * an Abstract Syntax Tree (AST) rooted at {@link ProgramNode}.
 *
 * <p>EBNF Grammar:
 * <pre>
 * Program        ::= ( Statement )*
 * Statement      ::= TempoStmt | InstrumentStmt | PhraseDefStmt | PlayStmt
 * TempoStmt      ::= "tempo" NUMBER ";"
 * InstrumentStmt ::= "instrument" IDENTIFIER ";"
 * PhraseDefStmt  ::= "define" IDENTIFIER "{" NoteSequence "}"
 * PlayStmt       ::= "play" ( IDENTIFIER | "repeat" NUMBER "{" NoteSequence "}" ) ";"
 * NoteSequence   ::= Note ( "," Note )*
 * Note           ::= ( NOTE_PITCH | REST ) NOTE_DURATION
 * </pre>
 */
public class Parser {

    private final Lexer lexer;
    private Token currentToken;

    /**
     * Constructs a Parser backed by the provided Lexer instance.
     * Primes the single-token lookahead buffer.
     *
     * @param lexer the lexical analyzer
     */
    public Parser(Lexer lexer) {
        this.lexer = Objects.requireNonNull(lexer, "lexer cannot be null");
        this.currentToken = this.lexer.nextToken();
    }

    /**
     * Convenience constructor that creates a Lexer from a source String.
     *
     * @param source source DSL code
     */
    public Parser(String source) {
        this(new Lexer(source));
    }

    /**
     * Parses the entire MelodyScript input into a ProgramNode AST.
     *
     * @return ProgramNode root of the parsed AST
     * @throws SyntaxException if a syntax violation or unexpected token occurs
     */
    public ProgramNode parse() {
        List<ASTNode> statements = new ArrayList<>();
        int rootLine = currentToken.getLine();
        int rootCol = currentToken.getColumn();

        while (!check(Token.TokenType.EOF)) {
            statements.add(parseStatement());
        }

        consume(Token.TokenType.EOF, "<EOF>");
        return new ProgramNode(statements, rootLine, rootCol);
    }

    /**
     * Statement ::= TempoStmt | InstrumentStmt | PhraseDefStmt | PlayStmt
     */
    private ASTNode parseStatement() {
        return switch (currentToken.getType()) {
            case KEYWORD_TEMPO -> parseTempoStmt();
            case KEYWORD_INSTRUMENT -> parseInstrumentStmt();
            case KEYWORD_DEFINE -> parsePhraseDefStmt();
            case KEYWORD_PLAY -> parsePlayStmt();
            default -> throw new SyntaxException("statement starting with 'tempo', 'instrument', 'define', or 'play'", currentToken);
        };
    }

    /**
     * TempoStmt ::= "tempo" NUMBER ";"
     */
    private TempoNode parseTempoStmt() {
        Token tempoToken = consume(Token.TokenType.KEYWORD_TEMPO, "'tempo'");
        Token numToken = consume(Token.TokenType.NUMBER, "tempo BPM value (positive integer)");
        consume(Token.TokenType.SEMICOLON, "';' after tempo declaration");

        int bpm = Integer.parseInt(numToken.getLexeme());
        return new TempoNode(bpm, tempoToken.getLine(), tempoToken.getColumn());
    }

    /**
     * InstrumentStmt ::= "instrument" IDENTIFIER ";"
     */
    private InstrumentNode parseInstrumentStmt() {
        Token instToken = consume(Token.TokenType.KEYWORD_INSTRUMENT, "'instrument'");
        Token idToken = consume(Token.TokenType.IDENTIFIER, "instrument name identifier");
        consume(Token.TokenType.SEMICOLON, "';' after instrument declaration");

        return new InstrumentNode(idToken.getLexeme(), instToken.getLine(), instToken.getColumn());
    }

    /**
     * PhraseDefStmt ::= "define" IDENTIFIER "{" NoteSequence "}"
     */
    private PhraseDefNode parsePhraseDefStmt() {
        Token defToken = consume(Token.TokenType.KEYWORD_DEFINE, "'define'");
        Token idToken = consume(Token.TokenType.IDENTIFIER, "phrase name identifier");
        consume(Token.TokenType.LBRACE, "'{' to open phrase definition block");
        NoteSequenceNode sequence = parseNoteSequence();
        consume(Token.TokenType.RBRACE, "'}' to close phrase definition block");

        return new PhraseDefNode(idToken.getLexeme(), sequence, defToken.getLine(), defToken.getColumn());
    }

    /**
     * PlayStmt ::= "play" ( IDENTIFIER | "repeat" NUMBER "{" NoteSequence "}" ) ";"
     */
    private PlayNode parsePlayStmt() {
        Token playToken = consume(Token.TokenType.KEYWORD_PLAY, "'play'");

        if (check(Token.TokenType.KEYWORD_REPEAT)) {
            // Repeat block: "repeat" NUMBER "{" NoteSequence "}" ";"
            advance(); // consume "repeat"
            Token numToken = consume(Token.TokenType.NUMBER, "repeat count (positive integer)");
            int count = Integer.parseInt(numToken.getLexeme());
            consume(Token.TokenType.LBRACE, "'{' to open repeat block");
            NoteSequenceNode sequence = parseNoteSequence();
            consume(Token.TokenType.RBRACE, "'}' to close repeat block");
            consume(Token.TokenType.SEMICOLON, "';' after repeat play statement");

            return new PlayNode(null, count, sequence, true, playToken.getLine(), playToken.getColumn());
        } else if (check(Token.TokenType.IDENTIFIER)) {
            // Phrase invocation: IDENTIFIER ";"
            Token idToken = advance(); // consume identifier
            consume(Token.TokenType.SEMICOLON, "';' after phrase play statement");

            return new PlayNode(idToken.getLexeme(), 1, null, false, playToken.getLine(), playToken.getColumn());
        } else {
            throw new SyntaxException("phrase identifier or 'repeat' after 'play'", currentToken);
        }
    }

    /**
     * NoteSequence ::= Note ( "," Note )*
     */
    private NoteSequenceNode parseNoteSequence() {
        List<ASTNode> notes = new ArrayList<>();
        int seqLine = currentToken.getLine();
        int seqCol = currentToken.getColumn();

        notes.add(parseNote());
        while (check(Token.TokenType.COMMA)) {
            advance(); // consume ','
            notes.add(parseNote());
        }
        return new NoteSequenceNode(notes, seqLine, seqCol);
    }

    /**
     * Note ::= ( NOTE_PITCH | REST ) NOTE_DURATION
     */
    private ASTNode parseNote() {
        if (check(Token.TokenType.NOTE_PITCH)) {
            Token pitchToken = advance();
            Token durToken = consume(Token.TokenType.NOTE_DURATION, "note duration ('quarter', 'eighth', 'half', 'whole')");
            return new NoteNode(
                pitchToken.getLexeme(),
                pitchToken.getPitchValue(),
                durToken.getLexeme(),
                durToken.getDurationTicks(),
                pitchToken.getLine(),
                pitchToken.getColumn()
            );
        } else if (check(Token.TokenType.REST)) {
            Token restToken = advance();
            Token durToken = consume(Token.TokenType.NOTE_DURATION, "rest duration ('quarter', 'eighth', 'half', 'whole')");
            return new RestNode(
                durToken.getLexeme(),
                durToken.getDurationTicks(),
                restToken.getLine(),
                restToken.getColumn()
            );
        } else {
            throw new SyntaxException("note pitch (e.g., C4, Eb5) or rest ('R')", currentToken);
        }
    }

    /**
     * Checks if current lookahead token matches the expected token type without consuming it.
     */
    private boolean check(Token.TokenType type) {
        return currentToken.getType() == type;
    }

    /**
     * Consumes the current lookahead token and advances to the next token.
     */
    private Token advance() {
        Token previous = currentToken;
        if (currentToken.getType() != Token.TokenType.EOF) {
            currentToken = lexer.nextToken();
        }
        return previous;
    }

    /**
     * Consumes the current token if it matches expectedType, or throws SyntaxException.
     */
    private Token consume(Token.TokenType expectedType, String description) {
        if (check(expectedType)) {
            return advance();
        }
        throw new SyntaxException(description != null ? description : expectedType.name(), currentToken);
    }
}
