public class Token {
    public enum TokenType {
        // Keywords
        TEMPO, INSTRUMENT, DEFINE, PLAY, REPEAT,
        
        // Literals & Identifiers
        IDENTIFIER,      // e.g. piano, riff
        NUMBER,          // e.g. 120, 2
        PITCH,           // e.g. C4, F#5, R (Rest)
        DURATION,        // e.g. quarter, eighth, half, whole
        
        // Delimiters
        SEMICOLON,       // ;
        COMMA,           // ,
        LBRACE,          // {
        RBRACE,          // }
        
        EOF
    }

    public final TokenType type;
    public final String lexeme;
    public final int line;
    public final int column;

    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return String.format("%s('%s') [%d:%d]", type, lexeme, line, column);
    }
}