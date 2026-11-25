package computera.script;

import java.util.List;

final class Ast {
    enum UnaryOp {
        NEG, POS, NOT
    }

    enum BinOp {
        ADD, SUB, MUL, DIV, FLOORDIV, MOD, POW,
        EQ, NE, LT, LE, GT, GE, IN,
        AND, OR
    }

    interface Expr {
        int line();

        int column();
    }

    interface Stmt {
        int line();

        int column();
    }

    record Program(List<Stmt> body, int line) {
    }

    record Literal(Value value, int line, int column) implements Expr {
    }

    record Name(String name, int line, int column) implements Expr {
    }

    record ListExpr(List<Expr> elements, int line, int column) implements Expr {
    }

    record DictExpr(List<Expr> keys, List<Expr> values, int line, int column) implements Expr {
    }

    record Unary(UnaryOp op, Expr operand, int line, int column) implements Expr {
    }

    record Binary(BinOp op, Expr left, Expr right, int line, int column) implements Expr {
    }

    record Call(Expr callee, List<Expr> args, int line, int column) implements Expr {
    }

    record Attr(Expr base, String name, int line, int column) implements Expr {
    }

    record Index(Expr base, Expr index, int line, int column) implements Expr {
    }

    record ExprStmt(Expr expr, int line, int column) implements Stmt {
    }

    record Assign(Expr target, Expr value, int line, int column) implements Stmt {
    }

    record AugAssign(Expr target, BinOp op, Expr value, int line, int column) implements Stmt {
    }

    record Branch(Expr condition, List<Stmt> body, int line, int column) {
    }

    record IfStmt(List<Branch> branches, List<Stmt> elseBody, int line, int column) implements Stmt {
    }

    record WhileStmt(Expr condition, List<Stmt> body, int line, int column) implements Stmt {
    }

    record ForStmt(String variable, Expr iterable, List<Stmt> body, int line, int column) implements Stmt {
    }

    record BreakStmt(int line, int column) implements Stmt {
    }

    record ContinueStmt(int line, int column) implements Stmt {
    }

    record FunctionDef(String name, List<String> params, List<Stmt> body,
                       int line, int column) implements Stmt {
    }

    record ReturnStmt(Expr value, int line, int column) implements Stmt {
    }

    private Ast() {
    }
}
