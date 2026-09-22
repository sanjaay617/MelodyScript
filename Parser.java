import java.util.*;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public ASTNodes.ProgramNode parseProgram() {
        ASTNodes.ProgramNode program = new ASTNodes.ProgramNode();
        while (!isAtEnd()) {
            program.statements.add(parseStatement());
        }
        return program;
    }

    private ASTNodes.ASTNode parseStatement() {
        Token t = peek();
        switch (t.type) {
            case TEMPO: return parseTempo();
            case INSTRUMENT: return parseInstrument();
            case DEFINE: return parsePhraseDef();
            case PLAY: return parsePlay();
            default:
                throw error(t, "Expected statement starting with 'tempo', 'instrument', 'define', or 'play'.");
        }
    }

    private ASTNodes.TempoNode parseTempo() {
        consume(Token.TokenType.TEMPO, "Expected 'tempo'");
        Token num = consume(Token.TokenType.NUMBER, "Expected numeric BPM value after 'tempo'");
        consume(Token.TokenType.SEMICOLON, "Expected ';' after tempo statement");
        return new ASTNodes.TempoNode(Integer.parseInt(num.lexeme));
    }

    private ASTNodes.InstrumentNode parseInstrument() {
        consume(Token.TokenType.INSTRUMENT, "Expected 'instrument'");
        Token id = consume(Token.TokenType.IDENTIFIER, "Expected instrument identifier");
        consume(Token.TokenType.SEMICOLON, "Expected ';' after instrument statement");
        return new ASTNodes.InstrumentNode(id.lexeme);
    }

    private ASTNodes.PhraseDefNode parsePhraseDef() {
        consume(Token.TokenType.DEFINE, "Expected 'define'");
        Token id = consume(Token.TokenType.IDENTIFIER, "Expected phrase identifier");
        consume(Token.TokenType.LBRACE, "Expected '{' before note sequence");
        ASTNodes.NoteSequenceNode sequence = parseNoteSequence();
        consume(Token.TokenType.RBRACE, "Expected '}' after note sequence");
        return new ASTNodes.PhraseDefNode(id.lexeme, sequence);
    }

    private ASTNodes.PlayNode parsePlay() {
        consume(Token.TokenType.PLAY, "Expected 'play'");
        if (match(Token.TokenType.REPEAT)) {
            Token count = consume(Token.TokenType.NUMBER, "Expected repeat count after 'repeat'");
            consume(Token.TokenType.LBRACE, "Expected '{' before repeat block");
            ASTNodes.NoteSequenceNode sequence = parseNoteSequence();
            consume(Token.TokenType.RBRACE, "Expected '}' after repeat block");
            consume(Token.TokenType.SEMICOLON, "Expected ';' after play repeat statement");
            return new ASTNodes.PlayNode(new ASTNodes.RepeatNode(Integer.parseInt(count.lexeme), sequence));
        } else {
            Token id = consume(Token.TokenType.IDENTIFIER, "Expected phrase identifier after 'play'");
            consume(Token.TokenType.SEMICOLON, "Expected ';' after play statement");
            return new ASTNodes.PlayNode(id.lexeme);
        }
    }

    private ASTNodes.NoteSequenceNode parseNoteSequence() {
        ASTNodes.NoteSequenceNode seq = new ASTNodes.NoteSequenceNode();
        seq.notes.add(parseNote());
        while (match(Token.TokenType.COMMA)) {
            seq.notes.add(parseNote());
        }
        return seq;
    }

    private ASTNodes.NoteNode parseNote() {
        Token pitch = consume(Token.TokenType.PITCH, "Expected pitch literal (e.g., C4, F#5, R)");
        Token duration = consume(Token.TokenType.DURATION, "Expected duration literal (e.g., quarter, eighth)");
        return new ASTNodes.NoteNode(pitch.lexeme, duration.lexeme);
    }

    // Helper Utility Methods
    private Token consume(Token.TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean match(Token.TokenType... types) {
        for (Token.TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(Token.TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() { return peek().type == Token.TokenType.EOF; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }

    private RuntimeException error(Token token, String message) {
        return new RuntimeException(String.format("Syntax Error at [%d:%d]: %s (Found '%s')", 
                token.line, token.column, message, token.lexeme));
    }
}