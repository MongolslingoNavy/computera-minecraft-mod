package computera.script;

final class FunctionInfo {
    final String name;
    final String[] params;
    final Instruction[] code;
    final int line;

    FunctionInfo(String name, String[] params, Instruction[] code, int line) {
        this.name = name;
        this.params = params;
        this.code = code;
        this.line = line;
    }
}
