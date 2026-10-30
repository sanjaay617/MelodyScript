/**
 * MelodyScript Semantic Analyzer
 * Pure Java (JDK 17+) implementation with zero third-party dependencies.
 */

/**
 * Custom runtime exception thrown when static semantic analysis discovers
 * a domain rule violation in the MelodyScript AST.
 * Captures 1-based source coordinates, the specific rule description,
 * and offender details.
 */
public class SemanticException extends RuntimeException {

    private final int line;
    private final int column;
    private final String ruleDescription;
    private final String offenderDetails;

    /**
     * Constructs a SemanticException with explicit coordinates and rule diagnostics.
     *
     * @param ruleDescription description of the violated semantic rule (e.g. Rule 1: Declaration-Before-Use)
     * @param offenderDetails details regarding the offending identifier, value, or node
     * @param line            1-based line number in source
     * @param column          1-based column number in source
     */
    public SemanticException(String ruleDescription, String offenderDetails, int line, int column) {
        super(String.format("Semantic error at [%d:%d] [%s]: %s", line, column, ruleDescription, offenderDetails));
        this.ruleDescription = ruleDescription;
        this.offenderDetails = offenderDetails;
        this.line = line;
        this.column = column;
    }

    /**
     * Convenience constructor extracting source coordinates from an ASTNode.
     *
     * @param ruleDescription description of the violated semantic rule
     * @param offenderDetails details regarding the offending construct
     * @param node            AST node associated with the semantic error
     */
    public SemanticException(String ruleDescription, String offenderDetails, ASTNode node) {
        this(ruleDescription, offenderDetails, node != null ? node.getLine() : -1, node != null ? node.getColumn() : -1);
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }

    public String getOffenderDetails() {
        return offenderDetails;
    }
}
