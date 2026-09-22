import java.util.*;

public class Lexer {
    private final String source;
    private int pos = 0;
    private int line = 1;
    private int column = 1;

    private static final Set<String> DURATIONS = Set.of("whole", "half", "quarter", "eighth", "sixteenth");

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < source.length()) {
            char ch = source.charAt(pos);

            if (Character.isWhitespace(ch)) {
                if (ch == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
                pos++;
                continue;
            }

            int startCol = column;

            switch (ch) {
                case ';': tokens.add(new Token(Token.TokenType.SEMICOLON, ";", line, startCol)); advance(); break;
                case ',': tokens.add(new Token(Token.TokenType.COMMA, ",", line, startCol)); advance(); break;
                case '{': tokens.add(new Token(Token.TokenType.LBRACE, "{", line, startCol)); advance(); break;
                case '}': tokens.add(new Token(Token.TokenType.RBRACE, "}", line, startCol)); advance(); break;
                default:
                    if (Character.isDigit(ch)) {
                        tokens.add(scanNumber(startCol));
                    } else if (Character.isLetter(ch) || ch == '#') {
                        tokens.add(scanIdentifierOrLiteral(startCol));
                    } else {
                        throw new RuntimeException("Lexical Error: Unexpected character '" + ch + "' at " + line + ":" + column);
                    }
                    break;
            }
        }
        tokens.add(new Token(Token.TokenType.EOF, "", line, column));
        return tokens;
    }

    private Token scanNumber(int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            sb.append(source.charAt(pos));
            advance();
        }
        return new Token(Token.TokenType.NUMBER, sb.toString(), line, startCol);
    }

    private Token scanIdentifierOrLiteral(int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '#')) {
            sb.append(source.charAt(pos));
            advance();
        }
        String text = sb.toString();

        // Check Keywords
        switch (text) {
            case "tempo": return new Token(Token.TokenType.TEMPO, text, line, startCol);
            case "instrument": return new Token(Token.TokenType.INSTRUMENT, text, line, startCol);
            case "define": return new Token(Token.TokenType.DEFINE, text, line, startCol);
            case "play": return new Token(Token.TokenType.PLAY, text, line, startCol);
            case "repeat": return new Token(Token.TokenType.REPEAT, text, line, startCol);
        }

        // Check Durations
        if (DURATIONS.contains(text.toLowerCase())) {
            return new Token(Token.TokenType.DURATION, text, line, startCol);
        }

        // Check Pitch (e.g., C4, F#5, R)
        if (text.matches("([A-G][#b]?[0-8])|R")) {
            return new Token(Token.TokenType.PITCH, text, line, startCol);
        }

        return new Token(Token.TokenType.IDENTIFIER, text, line, startCol);
    }

    private void advance() {
        pos++;
        column++;
    }
}