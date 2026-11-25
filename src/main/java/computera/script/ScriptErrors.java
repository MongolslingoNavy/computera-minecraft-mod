package computera.script;

final class ScriptErrors {
    private ScriptErrors() {
    }

    static ScriptCompileException compile(String filename, int line, int column, String message) {
        int safeLine = Math.max(line, 1);
        int safeColumn = Math.max(column, 1);
        return new ScriptCompileException(
                filename + ":" + safeLine + ":" + safeColumn + ": " + message,
                safeLine, safeColumn);
    }
}
