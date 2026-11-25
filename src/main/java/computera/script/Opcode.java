package computera.script;

final class Opcode {
    static final byte CONST = 0;
    static final byte POP = 1;
    static final byte LOAD_NAME = 2;
    static final byte STORE_NAME = 3;
    static final byte DEF_FUNC = 4;
    static final byte GET_ATTR = 5;
    static final byte CALL_NAME = 6;
    static final byte CALL_METHOD = 7;
    static final byte LOAD_INDEX = 8;
    static final byte STORE_INDEX = 9;
    static final byte BUILD_LIST = 10;
    static final byte BUILD_DICT = 11;
    static final byte ITER_INIT = 12;
    static final byte ITER_NEXT = 13;
    static final byte UNARY = 14;
    static final byte BINARY = 15;
    static final byte JUMP = 16;
    static final byte JUMP_IF_FALSE = 17;
    static final byte JUMP_IF_TRUE = 18;
    static final byte JUMP_IF_FALSE_KEEP = 19;
    static final byte JUMP_IF_TRUE_KEEP = 20;
    static final byte RETURN = 21;

    private Opcode() {
    }
}
