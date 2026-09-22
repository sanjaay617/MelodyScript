import java.util.List;

public class Main {
    public static void main(String[] args) {
        String sourceCode = """
            tempo 120;
            instrument piano;

            define riff {
                C4 quarter, E4 quarter, G4 quarter, R quarter
            }

            play riff;
            play repeat 2 { A4 eighth, B4 eighth };
            """;

        System.out.println("=== MELODYSCRIPT SOURCE PROGRAM ===");
        System.out.println(sourceCode);

        try {
            // Stage 1: Lexical Analysis
            Lexer lexer = new Lexer(sourceCode);
            List<Token> tokens = lexer.tokenize();

            // Stage 2: Syntax Analysis (Parser -> AST)
            Parser parser = new Parser(tokens);
            ASTNodes.ProgramNode ast = parser.parseProgram();

            // Stage 3: Output AST Structure
            System.out.println("=== COMPILER STAGE 1 & 2 OUTPUT (AST) ===");
            System.out.print(ast);

        } catch (Exception e) {
            System.err.println("Compilation Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}