package computera.script;

/**
 * A script runtime error. A line number is optional; the VM fills in the
 * current instruction line when a device or builtin does not provide one.
 */
public final class ScriptRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final boolean hasLine;

    public ScriptRuntimeException(String message) {
        super(message);
        this.line = 0;
        this.hasLine = false;
    }

    public ScriptRuntimeException(String message, int line) {
        super(message);
        this.line = line;
        this.hasLine = true;
    }

    public ScriptRuntimeException(String message, Throwable cause) {
        super(message, cause);
        this.line = 0;
        this.hasLine = false;
    }

    public int line() {
        return line;
    }

    public int getLine() {
        return line;
    }

    public boolean hasLine() {
        return hasLine;
    }
}
