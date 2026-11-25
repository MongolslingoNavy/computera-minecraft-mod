package computera.script;

final class Instruction {
    final byte op;
    int a;
    final int b;
    final int line;

    Instruction(byte op, int a, int b, int line) {
        this.op = op;
        this.a = a;
        this.b = b;
        this.line = line;
    }
}
