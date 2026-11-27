package computera.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Compiler {
    private static final int MAX_DEPTH = 128;

    private record LoopContext(CodeBuilder.Label breakTarget, CodeBuilder.Label continueTarget) {
    }

    private final String filename;
    private final List<Value> constants = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final Map<String, Integer> nameIndices = new HashMap<>();
    private final List<FunctionInfo> functions = new ArrayList<>();

    private CodeBuilder code;
    private boolean inFunction;
    private int depth;
    private Deque<LoopContext> loops = new ArrayDeque<>();

    Compiler(String filename) {
        this.filename = filename;
    }

    ScriptProgram compile(Ast.Program program) throws ScriptCompileException {
        code = new CodeBuilder();
        inFunction = false;
        loops = new ArrayDeque<>();
        for (Ast.Stmt statement : program.body()) {
            compileStatement(statement);
        }
        emitConstant(Value.none(), program.line());
        code.emit(Opcode.RETURN, 0, 0, program.line());
        FunctionInfo main = new FunctionInfo("<main>", new String[0], code.toArray(), program.line());
        return new ScriptProgram(filename, constants, names, functions, main);
    }

    private void compileStatement(Ast.Stmt statement) throws ScriptCompileException {
        if (++depth > MAX_DEPTH) {
            depth--;
            throw error(statement.line(), statement.column(), "nesting depth limit exceeded (" + MAX_DEPTH + ")");
        }
        try {
            compileStatementBody(statement);
        } finally {
            depth--;
        }
    }

    private void compileStatementBody(Ast.Stmt statement) throws ScriptCompileException {
        if (statement instanceof Ast.ExprStmt s) {
            compileExpr(s.expr());
            code.emit(Opcode.POP, 0, 0, s.line());
            return;
        }
        if (statement instanceof Ast.Assign s) {
            compileAssignment(s);
            return;
        }
        if (statement instanceof Ast.AugAssign s) {
            compileAugmentedAssignment(s);
            return;
        }
        if (statement instanceof Ast.IfStmt s) {
            compileIf(s);
            return;
        }
        if (statement instanceof Ast.WhileStmt s) {
            compileWhile(s);
            return;
        }
        if (statement instanceof Ast.ForStmt s) {
            compileFor(s);
            return;
        }
        if (statement instanceof Ast.BreakStmt s) {
            if (loops.isEmpty()) {
                throw error(s.line(), s.column(), "break outside loop");
            }
            code.jump(Opcode.JUMP, loops.peek().breakTarget(), s.line());
            return;
        }
        if (statement instanceof Ast.ContinueStmt s) {
            if (loops.isEmpty()) {
                throw error(s.line(), s.column(), "continue outside loop");
            }
            code.jump(Opcode.JUMP, loops.peek().continueTarget(), s.line());
            return;
        }
        if (statement instanceof Ast.FunctionDef s) {
            compileFunctionDef(s);
            return;
        }
        if (statement instanceof Ast.ReturnStmt s) {
            compileReturn(s);
            return;
        }
        throw error(statement.line(), statement.column(), "unsupported statement");
    }

    private void compileAssignment(Ast.Assign statement) throws ScriptCompileException {
        if (statement.target() instanceof Ast.Name target) {
            compileExpr(statement.value());
            code.emit(Opcode.STORE_NAME, nameIndex(target.name()), 0, statement.line());
            return;
        }
        if (statement.target() instanceof Ast.Index target) {
            compileExpr(statement.value());
            compileExpr(target.base());
            compileExpr(target.index());
            code.emit(Opcode.STORE_INDEX, 0, 0, statement.line());
            return;
        }
        throw error(statement.line(), statement.column(), "cannot assign to this expression");
    }

    private void compileAugmentedAssignment(Ast.AugAssign statement) throws ScriptCompileException {
        if (!(statement.target() instanceof Ast.Name target)) {
            throw error(statement.line(), statement.column(), "augmented assignment target must be a variable");
        }
        code.emit(Opcode.LOAD_NAME, nameIndex(target.name()), 0, statement.line());
        compileExpr(statement.value());
        code.emit(Opcode.BINARY, statement.op().ordinal(), 0, statement.line());
        code.emit(Opcode.STORE_NAME, nameIndex(target.name()), 0, statement.line());
    }

    private void compileIf(Ast.IfStmt statement) throws ScriptCompileException {
        CodeBuilder.Label end = new CodeBuilder.Label();
        for (Ast.Branch branch : statement.branches()) {
            compileExpr(branch.condition());
            CodeBuilder.Label next = new CodeBuilder.Label();
            code.jump(Opcode.JUMP_IF_FALSE, next, branch.line());
            for (Ast.Stmt child : branch.body()) {
                compileStatement(child);
            }
            code.jump(Opcode.JUMP, end, branch.line());
            code.place(next);
        }
        if (statement.elseBody() != null) {
            for (Ast.Stmt child : statement.elseBody()) {
                compileStatement(child);
            }
        }
        code.place(end);
    }

    private void compileWhile(Ast.WhileStmt statement) throws ScriptCompileException {
        CodeBuilder.Label top = new CodeBuilder.Label();
        code.place(top);
        compileExpr(statement.condition());
        CodeBuilder.Label end = new CodeBuilder.Label();
        code.jump(Opcode.JUMP_IF_FALSE, end, statement.line());

        loops.push(new LoopContext(end, top));
        for (Ast.Stmt child : statement.body()) {
            compileStatement(child);
        }
        loops.pop();

        code.jump(Opcode.JUMP, top, statement.line());
        code.place(end);
    }

    private void compileFor(Ast.ForStmt statement) throws ScriptCompileException {
        compileExpr(statement.iterable());
        code.emit(Opcode.ITER_INIT, 0, 0, statement.line());

        CodeBuilder.Label top = new CodeBuilder.Label();
        code.place(top);
        CodeBuilder.Label cleanup = new CodeBuilder.Label();
        code.jump(Opcode.ITER_NEXT, cleanup, statement.line());
        code.emit(Opcode.STORE_NAME, nameIndex(statement.variable()), 0, statement.line());

        loops.push(new LoopContext(cleanup, top));
        for (Ast.Stmt child : statement.body()) {
            compileStatement(child);
        }
        loops.pop();

        code.jump(Opcode.JUMP, top, statement.line());
        code.place(cleanup);
        code.emit(Opcode.POP, 0, 0, statement.line());
    }

    private void compileFunctionDef(Ast.FunctionDef statement) throws ScriptCompileException {
        Set<String> seen = new HashSet<>();
        for (String param : statement.params()) {
            if (!seen.add(param)) {
                throw error(statement.line(), statement.column(),
                        "duplicate parameter name '" + param + "'");
            }
        }

        FunctionInfo function = compileFunctionBody(statement);
        int index = functions.size();
        functions.add(function);
        code.emit(Opcode.DEF_FUNC, index, 0, statement.line());
    }

    private FunctionInfo compileFunctionBody(Ast.FunctionDef statement) throws ScriptCompileException {
        CodeBuilder savedCode = code;
        boolean savedInFunction = inFunction;
        Deque<LoopContext> savedLoops = loops;

        code = new CodeBuilder();
        inFunction = true;
        loops = new ArrayDeque<>();
        try {
            for (Ast.Stmt child : statement.body()) {
                compileStatement(child);
            }
            emitConstant(Value.none(), statement.line());
            code.emit(Opcode.RETURN, 0, 0, statement.line());
            return new FunctionInfo(statement.name(), statement.params().toArray(new String[0]),
                    code.toArray(), statement.line());
        } finally {
            code = savedCode;
            inFunction = savedInFunction;
            loops = savedLoops;
        }
    }

    private void compileReturn(Ast.ReturnStmt statement) throws ScriptCompileException {
        if (!inFunction) {
            throw error(statement.line(), statement.column(), "return outside function");
        }
        if (statement.value() == null) {
            emitConstant(Value.none(), statement.line());
        } else {
            compileExpr(statement.value());
        }
        code.emit(Opcode.RETURN, 0, 0, statement.line());
    }

    private void compileExpr(Ast.Expr expr) throws ScriptCompileException {
        if (++depth > MAX_DEPTH) {
            depth--;
            throw error(expr.line(), expr.column(), "nesting depth limit exceeded (" + MAX_DEPTH + ")");
        }
        try {
            compileExprBody(expr);
        } finally {
            depth--;
        }
    }

    private void compileExprBody(Ast.Expr expr) throws ScriptCompileException {
        if (expr instanceof Ast.Literal literal) {
            emitConstant(literal.value(), literal.line());
            return;
        }
        if (expr instanceof Ast.Name name) {
            code.emit(Opcode.LOAD_NAME, nameIndex(name.name()), 0, name.line());
            return;
        }
        if (expr instanceof Ast.ListExpr list) {
            for (Ast.Expr element : list.elements()) {
                compileExpr(element);
            }
            code.emit(Opcode.BUILD_LIST, list.elements().size(), 0, list.line());
            return;
        }
        if (expr instanceof Ast.DictExpr dict) {
            for (int i = 0; i < dict.keys().size(); i++) {
                compileExpr(dict.keys().get(i));
                compileExpr(dict.values().get(i));
            }
            code.emit(Opcode.BUILD_DICT, dict.keys().size(), 0, dict.line());
            return;
        }
        if (expr instanceof Ast.Unary unary) {
            compileExpr(unary.operand());
            code.emit(Opcode.UNARY, unary.op().ordinal(), 0, unary.line());
            return;
        }
        if (expr instanceof Ast.Binary binary) {
            compileBinary(binary);
            return;
        }
        if (expr instanceof Ast.Call call) {
            compileCall(call);
            return;
        }
        if (expr instanceof Ast.Attr attr) {
            compileExpr(attr.base());
            code.emit(Opcode.GET_ATTR, nameIndex(attr.name()), 0, attr.line());
            return;
        }
        if (expr instanceof Ast.Index index) {
            compileExpr(index.base());
            compileExpr(index.index());
            code.emit(Opcode.LOAD_INDEX, 0, 0, index.line());
            return;
        }
        throw error(expr.line(), expr.column(), "unsupported expression");
    }

    private void compileBinary(Ast.Binary binary) throws ScriptCompileException {
        if (binary.op() == Ast.BinOp.AND) {
            compileExpr(binary.left());
            CodeBuilder.Label end = new CodeBuilder.Label();
            code.jump(Opcode.JUMP_IF_FALSE_KEEP, end, binary.line());
            code.emit(Opcode.POP, 0, 0, binary.line());
            compileExpr(binary.right());
            code.place(end);
            return;
        }
        if (binary.op() == Ast.BinOp.OR) {
            compileExpr(binary.left());
            CodeBuilder.Label end = new CodeBuilder.Label();
            code.jump(Opcode.JUMP_IF_TRUE_KEEP, end, binary.line());
            code.emit(Opcode.POP, 0, 0, binary.line());
            compileExpr(binary.right());
            code.place(end);
            return;
        }
        compileExpr(binary.left());
        compileExpr(binary.right());
        code.emit(Opcode.BINARY, binary.op().ordinal(), 0, binary.line());
    }

    private void compileCall(Ast.Call call) throws ScriptCompileException {
        if (call.callee() instanceof Ast.Name name) {
            for (Ast.Expr arg : call.args()) {
                compileExpr(arg);
            }
            code.emit(Opcode.CALL_NAME, nameIndex(name.name()), call.args().size(), call.line());
            return;
        }
        if (call.callee() instanceof Ast.Attr attr) {
            compileExpr(attr.base());
            for (Ast.Expr arg : call.args()) {
                compileExpr(arg);
            }
            code.emit(Opcode.CALL_METHOD, nameIndex(attr.name()), call.args().size(), call.line());
            return;
        }
        throw error(call.line(), call.column(),
                "only named functions and device methods can be called");
    }

    private void emitConstant(Value value, int line) {
        int index = constants.size();
        constants.add(value);
        code.emit(Opcode.CONST, index, 0, line);
    }

    private int nameIndex(String name) {
        Integer existing = nameIndices.get(name);
        if (existing != null) {
            return existing;
        }
        int index = names.size();
        names.add(name);
        nameIndices.put(name, index);
        return index;
    }

    private ScriptCompileException error(int line, int column, String message) {
        return ScriptErrors.compile(filename, line, column, message);
    }
}
