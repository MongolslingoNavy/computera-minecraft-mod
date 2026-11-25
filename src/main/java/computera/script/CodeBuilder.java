package computera.script;

import java.util.ArrayList;
import java.util.List;

final class CodeBuilder {
    static final class Label {
        int position = -1;
        final List<Integer> fixups = new ArrayList<>();
    }

    private final List<Instruction> instructions = new ArrayList<>();

    int emit(byte op, int a, int b, int line) {
        instructions.add(new Instruction(op, a, b, line));
        return instructions.size() - 1;
    }

    int here() {
        return instructions.size();
    }

    void place(Label label) {
        label.position = instructions.size();
        for (int fixup : label.fixups) {
            instructions.get(fixup).a = label.position;
        }
        label.fixups.clear();
    }

    void jump(byte op, Label target, int line) {
        int position = emit(op, 0, 0, line);
        if (target.position >= 0) {
            instructions.get(position).a = target.position;
        } else {
            target.fixups.add(position);
        }
    }

    Instruction[] toArray() {
        return instructions.toArray(new Instruction[0]);
    }
}
