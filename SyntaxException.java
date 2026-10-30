/**
 * MelodyScript Parser
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

/**
 * Custom runtime exception thrown by the MelodyScript parser when an unexpected
 * token or grammar syntax violation is encountered.
 * Captures 1-based source coordinates, the expected token/production, and the actual found token.
 */
public class SyntaxException extends RuntimeException {

    private final int line;
    private final int column;
    private final String expected;
    private final String found;
    private final Token foundToken;

    /**
     * Constructs a SyntaxException with expected description and actual token found.
     *
     * @param expected   description of the expected token type or grammatical element
     * @param foundToken the actual token encountered at this position
     */
    public SyntaxException(String expected, Token foundToken) {
        super(formatMessage(expected, foundToken));
        this.line = foundToken != null ? foundToken.getLine() : -1;
        this.column = foundToken != null ? foundToken.getColumn() : -1;
        this.expected = expected;
        this.foundToken = foundToken;
        this.found = formatFound(foundToken);
    }

    /**
     * Constructs a SyntaxException with explicit fields.
     *
     * @param message  detailed message
     * @param line     1-based line number
     * @param column   1-based column number
     * @param expected expected construct
     * @param found    found construct description
     */
    public SyntaxException(String message, int line, int column, String expected, String found) {
        super(String.format("Syntax error at [%d:%d]: %s", line, column, message));
        this.line = line;
        this.column = column;
        this.expected = expected;
        this.found = found;
        this.foundToken = null;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getExpected() {
        return expected;
    }

    public String getFound() {
        return found;
    }

    public Token getFoundToken() {
        return foundToken;
    }

    private static String formatMessage(String expected, Token foundToken) {
        int l = foundToken != null ? foundToken.getLine() : -1;
        int c = foundToken != null ? foundToken.getColumn() : -1;
        return String.format("Syntax error at [%d:%d]: Expected %s, but found %s", l, c, expected, formatFound(foundToken));
    }

    private static String formatFound(Token token) {
        if (token == null) return "<null>";
        if (token.getType() == Token.TokenType.EOF) return "<EOF>";
        return String.format("%s \"%s\"", token.getType(), token.getLexeme());
    }
}
