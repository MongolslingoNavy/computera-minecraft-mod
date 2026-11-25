package computera.script;

import java.util.List;

/**
 * An immutable compiled script: constant pool, function bodies and the main
 * function bytecode.
 */
public final class ScriptProgram {
    private final String name;
    private final Value[] constants;
    private final String[] names;
    private final FunctionInfo[] functions;
    private final FunctionInfo main;

    ScriptProgram(String name, List<Value> constants, List<String> names,
                  List<FunctionInfo> functions, FunctionInfo main) {
        this.name = name;
        this.constants = constants.toArray(new Value[0]);
        this.names = names.toArray(new String[0]);
        this.functions = functions.toArray(new FunctionInfo[0]);
        this.main = main;
    }

    public String name() {
        return name;
    }

    Value constant(int index) {
        return constants[index];
    }

    String name(int index) {
        return names[index];
    }

    FunctionInfo function(int index) {
        return functions[index];
    }

    FunctionInfo main() {
        return main;
    }
}
