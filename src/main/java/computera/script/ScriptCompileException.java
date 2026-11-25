package computera.script;

/**
 * Raised while lexing, parsing or compiling a script.
 */
public final class ScriptCompileException extends Exception {
    private static final long serialVersionUID = 1L;

    public final int line;
    public final int column;

    public ScriptCompileException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
