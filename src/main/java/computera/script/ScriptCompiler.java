package computera.script;

import java.util.List;
import java.util.Objects;

/**
 * Compiles Python-subset source text into a {@link ScriptProgram}.
 */
public final class ScriptCompiler {
    private ScriptCompiler() {
    }

    public static ScriptProgram compile(String source, String name) throws ScriptCompileException {
        Objects.requireNonNull(source, "source");
        String filename = name == null || name.isEmpty() ? "<script>" : name;
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        List<Token> tokens = new Lexer(normalized, filename).tokenize();
        Ast.Program program = new Parser(tokens, filename).parse();
        return new Compiler(filename).compile(program);
    }
}
