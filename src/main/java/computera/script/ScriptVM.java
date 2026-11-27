package computera.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Executes a compiled script. Execution is driven in instruction batches by
 * the game loop through {@link #step(int)}; user code never runs on a Java
 * call stack of its own.
 */
public final class ScriptVM {
    private static final int MAX_RECURSION_DEPTH = 256;
    private static final long RANGE_LIMIT = 10_000L;
    private static final int MAX_STRING_LENGTH = 65_536;
    private static final int MAX_COLLECTION_SIZE = 10_000;
    private static final int MAX_EQUALITY_STEPS = 4_096;
    private static final Object DEVICES_MARKER = new Object();
    private static final Set<String> BUILTINS = Set.of(
            "print", "len", "range", "int", "float", "str", "bool", "sleep", "time", "log");

    private final ScriptProgram program;
    private final ScriptHost host;
    private final Map<String, Object> globals = new LinkedHashMap<>();
    private final List<Frame> frames = new ArrayList<>();
    private final Frame mainFrame;

    private boolean finished;
    private boolean sleeping;
    private long sleepUntilMillis;
    private int extraCost;

    public ScriptVM(ScriptProgram program, ScriptHost host) {
        this.program = Objects.requireNonNull(program, "program");
        this.host = Objects.requireNonNull(host, "host");
        this.mainFrame = new Frame(program.main(), globals);
        frames.add(mainFrame);
    }

    public boolean step(int maxInstructions) {
        if (finished) {
            return false;
        }
        if (maxInstructions <= 0) {
            return true;
        }

        int executed = 0;
        while (executed < maxInstructions && !finished) {
            if (sleeping) {
                if (host.currentTimeMillis() < sleepUntilMillis) {
                    return true;
                }
                sleeping = false;
            }

            Frame frame = currentFrame();
            if (frame.ip < 0 || frame.ip >= frame.function.code.length) {
                fail(new ScriptRuntimeException("internal error: instruction pointer out of range",
                        frame.currentLine));
                break;
            }
            Instruction instruction = frame.function.code[frame.ip++];
            frame.currentLine = instruction.line;
            extraCost = 0;
            executed++;
            try {
                execute(frame, instruction);
                if (extraCost > 0) {
                    executed = (int) Math.min((long) maxInstructions, (long) executed + extraCost);
                }
            } catch (ScriptRuntimeException e) {
                fail(e);
            } catch (RuntimeException e) {
                fail(new ScriptRuntimeException("internal error: " + exceptionMessage(e),
                        frame.currentLine));
            }
        }
        return !finished;
    }

    public boolean isFinished() {
        return finished;
    }

    public void halt(String reason) {
        if (finished) {
            return;
        }
        finished = true;
        sleeping = false;
        frames.clear();
        String message = program.name() + ": script halted";
        if (reason != null && !reason.isEmpty()) {
            message += ": " + reason;
        }
        safeError(message);
    }

    public boolean isSleeping() {
        return sleeping;
    }

    public long sleepUntilMillis() {
        return sleeping ? sleepUntilMillis : 0L;
    }

    private void execute(Frame frame, Instruction instruction) {
        switch (instruction.op) {
            case Opcode.CONST -> frame.push(program.constant(instruction.a));
            case Opcode.POP -> frame.pop();
            case Opcode.LOAD_NAME -> frame.push(loadName(frame, program.name(instruction.a), instruction.line));
            case Opcode.STORE_NAME -> frame.locals.put(program.name(instruction.a), frame.pop());
            case Opcode.DEF_FUNC -> {
                FunctionInfo function = program.function(instruction.a);
                frame.locals.put(function.name, function);
            }
            case Opcode.GET_ATTR -> {
                Object base = frame.pop();
                frame.push(getAttr(base, program.name(instruction.a), instruction.line));
            }
            case Opcode.CALL_NAME -> callName(frame, program.name(instruction.a), instruction.b, instruction.line);
            case Opcode.CALL_METHOD -> callMethod(frame, program.name(instruction.a), instruction.b, instruction.line);
            case Opcode.LOAD_INDEX -> {
                Value index = popValue(frame);
                Object base = frame.pop();
                frame.push(indexGet(base, index, instruction.line));
            }
            case Opcode.STORE_INDEX -> {
                Value index = popValue(frame);
                Object base = frame.pop();
                Value value = popValue(frame);
                storeIndex(base, index, value, instruction.line);
            }
            case Opcode.BUILD_LIST -> buildList(frame, instruction.a, instruction.line);
            case Opcode.BUILD_DICT -> buildDict(frame, instruction.a, instruction.line);
            case Opcode.ITER_INIT -> {
                Value iterable = popValue(frame);
                frame.push(iteratorFor(iterable, instruction.line));
            }
            case Opcode.ITER_NEXT -> {
                Object top = frame.peek();
                if (!(top instanceof IteratorState iterator)) {
                    throw new ScriptRuntimeException("internal error: expected iterator", instruction.line);
                }
                if (iterator.hasNext()) {
                    frame.push(iterator.next());
                } else {
                    frame.ip = instruction.a;
                }
            }
            case Opcode.UNARY -> {
                Value operand = popValue(frame);
                frame.push(applyUnary(instruction.a, operand, instruction.line));
            }
            case Opcode.BINARY -> {
                Value right = popValue(frame);
                Value left = popValue(frame);
                frame.push(applyBinary(instruction.a, left, right, instruction.line));
            }
            case Opcode.JUMP -> frame.ip = instruction.a;
            case Opcode.JUMP_IF_FALSE -> {
                Value condition = popValue(frame);
                if (!truthy(condition)) {
                    frame.ip = instruction.a;
                }
            }
            case Opcode.JUMP_IF_TRUE -> {
                Value condition = popValue(frame);
                if (truthy(condition)) {
                    frame.ip = instruction.a;
                }
            }
            case Opcode.JUMP_IF_FALSE_KEEP -> {
                Value condition = peekValue(frame);
                if (!truthy(condition)) {
                    frame.ip = instruction.a;
                }
            }
            case Opcode.JUMP_IF_TRUE_KEEP -> {
                Value condition = peekValue(frame);
                if (truthy(condition)) {
                    frame.ip = instruction.a;
                }
            }
            case Opcode.RETURN -> doReturn(frame);
            default -> throw new ScriptRuntimeException("internal error: unknown opcode " + instruction.op,
                    instruction.line);
        }
    }

    private void buildList(Frame frame, int count, int line) {
        if (count > MAX_COLLECTION_SIZE) {
            throw error("MemoryError", "list size limit exceeded (" + MAX_COLLECTION_SIZE + ")", line);
        }
        Value[] items = new Value[count];
        for (int i = count - 1; i >= 0; i--) {
            items[i] = popValue(frame);
        }
        extraCost += count;
        frame.push(Value.list(Arrays.asList(items)));
    }

    private void buildDict(Frame frame, int count, int line) {
        if (count > MAX_COLLECTION_SIZE) {
            throw error("MemoryError", "dictionary size limit exceeded (" + MAX_COLLECTION_SIZE + ")", line);
        }
        String[] keys = new String[count];
        Value[] values = new Value[count];
        for (int i = count - 1; i >= 0; i--) {
            values[i] = popValue(frame);
            Value key = popValue(frame);
            if (!key.isString()) {
                throw error("TypeError",
                        "dictionary keys must be strings, not " + key.typeName(), line);
            }
            keys[i] = key.asString();
        }
        Map<String, Value> map = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            map.put(keys[i], values[i]);
        }
        extraCost += count;
        frame.push(Value.map(map));
    }

    private Object loadName(Frame frame, String name, int line) {
        Object value = lookup(frame, name);
        if (value != null) {
            return value;
        }
        if ("devices".equals(name)) {
            return DEVICES_MARKER;
        }
        if (BUILTINS.contains(name)) {
            throw error("TypeError", "built-in function '" + name + "' can only be called", line);
        }
        throw error("NameError", "name '" + name + "' is not defined", line);
    }

    private Object lookup(Frame frame, String name) {
        if (frame.locals.containsKey(name)) {
            return frame.locals.get(name);
        }
        if (frame != mainFrame && globals.containsKey(name)) {
            return globals.get(name);
        }
        return null;
    }

    private void callName(Frame frame, String name, int argc, int line) {
        Value[] args = popArgs(frame, argc);
        Object target = lookup(frame, name);
        if (target instanceof FunctionInfo function) {
            callFunction(function, args, line);
            return;
        }
        if (target != null) {
            if (target instanceof Value value) {
                throw error("TypeError", "'" + value.typeName() + "' object is not callable", line);
            }
            throw error("TypeError", "'" + name + "' is not callable", line);
        }
        if (BUILTINS.contains(name)) {
            callBuiltin(frame, name, args, line);
            return;
        }
        throw error("NameError", "name '" + name + "' is not defined", line);
    }

    private void callFunction(FunctionInfo function, Value[] args, int line) {
        if (frames.size() - 1 >= MAX_RECURSION_DEPTH) {
            throw error("RecursionError", "maximum recursion depth exceeded (" + MAX_RECURSION_DEPTH + ")", line);
        }
        if (function.params.length != args.length) {
            throw error("TypeError", function.name + "() takes " + function.params.length
                    + " positional argument(s) but " + args.length + " were given", line);
        }
        Map<String, Object> locals = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            locals.put(function.params[i], args[i]);
        }
        if (!locals.containsKey(function.name)) {
            locals.put(function.name, function);
        }
        Frame called = new Frame(function, locals);
        called.currentLine = function.line;
        frames.add(called);
    }

    private void callMethod(Frame frame, String method, int argc, int line) {
        Value[] args = popArgs(frame, argc);
        Object receiver = frame.pop();
        if (!(receiver instanceof DeviceRef ref)) {
            if (receiver instanceof Value value) {
                throw error("TypeError", "'" + value.typeName() + "' value has no method '"
                        + method + "'", line);
            }
            throw error("TypeError", "method '" + method + "' can only be called on a device", line);
        }
        try {
            Value result = ref.device.call(method, new ArrayList<>(Arrays.asList(args)));
            frame.push(result == null ? Value.none() : result);
        } catch (ScriptRuntimeException e) {
            throw ensureLine(e, line);
        } catch (RuntimeException e) {
            throw error("DeviceError", "device '" + ref.name + "' method '" + method
                    + "' failed: " + exceptionMessage(e), line);
        }
    }

    private Object getAttr(Object base, String attr, int line) {
        if (base == DEVICES_MARKER) {
            Device device = host.device(attr);
            if (device == null) {
                throw error("NameError", "name 'devices." + attr + "' is not defined", line);
            }
            return new DeviceRef(attr, device);
        }
        if (base instanceof DeviceRef ref) {
            try {
                Value value = ref.device.get(attr);
                return value == null ? Value.none() : value;
            } catch (ScriptRuntimeException e) {
                throw ensureLine(e, line);
            } catch (RuntimeException e) {
                throw error("DeviceError", "device '" + ref.name + "' property '" + attr
                        + "' failed: " + exceptionMessage(e), line);
            }
        }
        if (base instanceof Value value) {
            throw error("TypeError", "'" + value.typeName() + "' value has no attribute '"
                    + attr + "'", line);
        }
        throw error("TypeError", "value has no attribute '" + attr + "'", line);
    }

    private Value indexGet(Object base, Value index, int line) {
        if (!(base instanceof Value container)) {
            throw error("TypeError", "value is not subscriptable", line);
        }
        if (container.isList()) {
            long position = requireIndexInt(index, "list", line);
            List<Value> list = container.asList();
            if (position < 0) {
                position += list.size();
            }
            if (position < 0 || position >= list.size()) {
                throw error("IndexError", "list index out of range", line);
            }
            return list.get((int) position);
        }
        if (container.isMap()) {
            if (!index.isString()) {
                throw error("TypeError", "dictionary keys must be strings, not "
                        + index.typeName(), line);
            }
            Map<String, Value> map = container.asMap();
            String key = index.asString();
            if (!map.containsKey(key)) {
                throw error("KeyError", "key not found: " + key, line);
            }
            return map.get(key);
        }
        if (container.isString()) {
            long position = requireIndexInt(index, "string", line);
            String text = container.asString();
            int length = text.codePointCount(0, text.length());
            if (position < 0) {
                position += length;
            }
            if (position < 0 || position >= length) {
                throw error("IndexError", "string index out of range", line);
            }
            int offset = text.offsetByCodePoints(0, (int) position);
            int codePoint = text.codePointAt(offset);
            return Value.of(new String(Character.toChars(codePoint)));
        }
        throw error("TypeError", "'" + container.typeName() + "' value is not subscriptable", line);
    }

    private long requireIndexInt(Value index, String containerKind, int line) {
        if (!index.isInt()) {
            throw error("TypeError", containerKind + " indices must be integers, not "
                    + index.typeName(), line);
        }
        return index.asLong();
    }

    private void storeIndex(Object base, Value index, Value value, int line) {
        if (!(base instanceof Value container)) {
            throw error("TypeError", "value does not support item assignment", line);
        }
        if (container.isList()) {
            long position = requireIndexInt(index, "list", line);
            List<Value> list = container.asList();
            if (position < 0) {
                position += list.size();
            }
            if (position < 0 || position >= list.size()) {
                throw error("IndexError", "list assignment index out of range", line);
            }
            list.set((int) position, value);
            return;
        }
        if (container.isMap()) {
            if (!index.isString()) {
                throw error("TypeError", "dictionary keys must be strings, not "
                        + index.typeName(), line);
            }
            Map<String, Value> map = container.asMap();
            String key = index.asString();
            if (!map.containsKey(key) && map.size() >= MAX_COLLECTION_SIZE) {
                throw error("MemoryError", "dictionary size limit exceeded (" + MAX_COLLECTION_SIZE + ")", line);
            }
            map.put(key, value);
            return;
        }
        throw error("TypeError", "'" + container.typeName()
                + "' value does not support item assignment", line);
    }

    private IteratorState iteratorFor(Value value, int line) {
        if (value.isList()) {
            return IteratorState.forList(value);
        }
        if (value.isString()) {
            return IteratorState.forText(value.asString());
        }
        if (value.isMap()) {
            return IteratorState.forMap(value);
        }
        throw error("TypeError", "'" + value.typeName() + "' value is not iterable", line);
    }

    private void doReturn(Frame frame) {
        Value value = popValue(frame);
        frames.remove(frames.size() - 1);
        if (frames.isEmpty()) {
            finished = true;
        } else {
            frames.get(frames.size() - 1).push(value);
        }
    }

    private Value[] popArgs(Frame frame, int count) {
        Value[] args = new Value[count];
        for (int i = count - 1; i >= 0; i--) {
            args[i] = popValue(frame);
        }
        return args;
    }

    private Value applyUnary(int opOrdinal, Value value, int line) {
        Ast.UnaryOp op = Ast.UnaryOp.values()[opOrdinal];
        return switch (op) {
            case NEG -> {
                if (value.isInt()) {
                    try {
                        yield Value.of(Math.negateExact(value.asLong()));
                    } catch (ArithmeticException e) {
                        throw error("OverflowError", "integer overflow", line);
                    }
                }
                if (value.isFloat()) {
                    yield Value.of(-value.asDouble());
                }
                throw error("TypeError", "bad operand type for unary -: '" + value.typeName() + "'", line);
            }
            case POS -> {
                if (value.isInt() || value.isFloat()) {
                    yield value;
                }
                throw error("TypeError", "bad operand type for unary +: '" + value.typeName() + "'", line);
            }
            case NOT -> Value.of(!truthy(value));
        };
    }

    private Value applyBinary(int opOrdinal, Value left, Value right, int line) {
        Ast.BinOp op = Ast.BinOp.values()[opOrdinal];
        return switch (op) {
            case AND -> truthy(left) ? right : left;
            case OR -> truthy(left) ? left : right;
            case ADD -> add(left, right, line);
            case SUB -> subtract(left, right, line);
            case MUL -> multiply(left, right, line);
            case DIV -> divide(left, right, line);
            case FLOORDIV -> floorDivide(left, right, line);
            case MOD -> modulo(left, right, line);
            case POW -> power(left, right, line);
            case EQ -> Value.of(valuesEqual(left, right, line));
            case NE -> Value.of(!valuesEqual(left, right, line));
            case LT -> compare(left, right, line, Comparison.LT);
            case LE -> compare(left, right, line, Comparison.LE);
            case GT -> compare(left, right, line, Comparison.GT);
            case GE -> compare(left, right, line, Comparison.GE);
            case IN -> Value.of(contains(right, left, line));
        };
    }

    private enum Comparison {
        LT, LE, GT, GE
    }

    private Value compare(Value left, Value right, int line, Comparison comparison) {
        if (left.isString() && right.isString()) {
            int result = left.asString().compareTo(right.asString());
            return Value.of(switch (comparison) {
                case LT -> result < 0;
                case LE -> result <= 0;
                case GT -> result > 0;
                case GE -> result >= 0;
            });
        }
        if (isNumber(left) && isNumber(right)) {
            double a = numberAsDouble(left);
            double b = numberAsDouble(right);
            return Value.of(switch (comparison) {
                case LT -> a < b;
                case LE -> a <= b;
                case GT -> a > b;
                case GE -> a >= b;
            });
        }
        throw error("TypeError", "comparison not supported between '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private Value add(Value left, Value right, int line) {
        if (left.isInt() && right.isInt()) {
            try {
                return Value.of(Math.addExact(left.asLong(), right.asLong()));
            } catch (ArithmeticException e) {
                throw error("OverflowError", "integer overflow", line);
            }
        }
        if (isNumber(left) && isNumber(right)) {
            return Value.of(numberAsDouble(left) + numberAsDouble(right));
        }
        if (left.isString() && right.isString()) {
            String a = left.asString();
            String b = right.asString();
            long combined = (long) a.length() + (long) b.length();
            if (combined > MAX_STRING_LENGTH) {
                throw error("MemoryError", "string length limit exceeded (" + MAX_STRING_LENGTH + ")", line);
            }
            extraCost += (int) (combined / 64L);
            return Value.of(a + b);
        }
        throw error("TypeError", "unsupported operand types for +: '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private Value subtract(Value left, Value right, int line) {
        if (left.isInt() && right.isInt()) {
            try {
                return Value.of(Math.subtractExact(left.asLong(), right.asLong()));
            } catch (ArithmeticException e) {
                throw error("OverflowError", "integer overflow", line);
            }
        }
        if (isNumber(left) && isNumber(right)) {
            return Value.of(numberAsDouble(left) - numberAsDouble(right));
        }
        throw error("TypeError", "unsupported operand types for -: '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private Value multiply(Value left, Value right, int line) {
        if (left.isInt() && right.isInt()) {
            try {
                return Value.of(Math.multiplyExact(left.asLong(), right.asLong()));
            } catch (ArithmeticException e) {
                throw error("OverflowError", "integer overflow", line);
            }
        }
        if (isNumber(left) && isNumber(right)) {
            return Value.of(numberAsDouble(left) * numberAsDouble(right));
        }
        throw error("TypeError", "unsupported operand types for *: '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private Value divide(Value left, Value right, int line) {
        if (isNumber(left) && isNumber(right)) {
            double divisor = numberAsDouble(right);
            if (divisor == 0.0) {
                throw error("ZeroDivisionError", "division by zero", line);
            }
            return Value.of(numberAsDouble(left) / divisor);
        }
        throw error("TypeError", "unsupported operand types for /: '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private Value floorDivide(Value left, Value right, int line) {
        if (left.isInt() && right.isInt()) {
            long divisor = right.asLong();
            if (divisor == 0) {
                throw error("ZeroDivisionError", "integer division or modulo by zero", line);
            }
            if (left.asLong() == Long.MIN_VALUE && divisor == -1) {
                throw error("OverflowError", "integer division overflow", line);
            }
            return Value.of(Math.floorDiv(left.asLong(), divisor));
        }
        if (isNumber(left) && isNumber(right)) {
            double divisor = numberAsDouble(right);
            if (divisor == 0.0) {
                throw error("ZeroDivisionError", "float floor division by zero", line);
            }
            return Value.of(Math.floor(numberAsDouble(left) / divisor));
        }
        throw error("TypeError", "unsupported operand types for //: '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private Value modulo(Value left, Value right, int line) {
        if (left.isInt() && right.isInt()) {
            long divisor = right.asLong();
            if (divisor == 0) {
                throw error("ZeroDivisionError", "integer division or modulo by zero", line);
            }
            return Value.of(Math.floorMod(left.asLong(), divisor));
        }
        if (isNumber(left) && isNumber(right)) {
            double divisor = numberAsDouble(right);
            if (divisor == 0.0) {
                throw error("ZeroDivisionError", "float modulo by zero", line);
            }
            double a = numberAsDouble(left);
            double b = divisor;
            return Value.of(a - Math.floor(a / b) * b);
        }
        throw error("TypeError", "unsupported operand types for %: '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private Value power(Value left, Value right, int line) {
        if (left.isInt() && right.isInt()) {
            long base = left.asLong();
            long exponent = right.asLong();
            if (exponent < 0) {
                if (base == 0L) {
                    throw error("ZeroDivisionError", "0.0 cannot be raised to a negative power", line);
                }
                return Value.of(Math.pow(base, exponent));
            }
            try {
                long result = 1L;
                long factor = base;
                long remaining = exponent;
                while (remaining > 0) {
                    if ((remaining & 1L) != 0L) {
                        result = Math.multiplyExact(result, factor);
                    }
                    remaining >>= 1;
                    if (remaining > 0) {
                        factor = Math.multiplyExact(factor, factor);
                    }
                }
                return Value.of(result);
            } catch (ArithmeticException e) {
                throw error("OverflowError", "integer overflow", line);
            }
        }
        if (isNumber(left) && isNumber(right)) {
            double base = numberAsDouble(left);
            double exponent = numberAsDouble(right);
            if (base == 0.0 && exponent < 0.0) {
                throw error("ZeroDivisionError", "0.0 cannot be raised to a negative power", line);
            }
            return Value.of(Math.pow(base, exponent));
        }
        throw error("TypeError", "unsupported operand types for **: '" + left.typeName()
                + "' and '" + right.typeName() + "'", line);
    }

    private boolean contains(Value container, Value needle, int line) {
        if (container.isList()) {
            List<Value> list = container.asList();
            extraCost += list.size();
            for (Value element : list) {
                if (valuesEqual(element, needle, line)) {
                    return true;
                }
            }
            return false;
        }
        if (container.isMap()) {
            if (!needle.isString()) {
                throw error("TypeError", "dictionary keys must be strings, not "
                        + needle.typeName(), line);
            }
            return container.asMap().containsKey(needle.asString());
        }
        if (container.isString()) {
            if (!needle.isString()) {
                throw error("TypeError", "'in <string>' requires a string operand, not "
                        + needle.typeName(), line);
            }
            extraCost += container.asString().length() / 64;
            return container.asString().contains(needle.asString());
        }
        throw error("TypeError", "'in' requires a list, dict or string on the right, not '"
                + container.typeName() + "'", line);
    }

    private static final class EqualityState {
        final IdentityHashMap<Object, IdentityHashMap<Object, Boolean>> seen = new IdentityHashMap<>();
        int remaining = MAX_EQUALITY_STEPS;
    }

    private boolean valuesEqual(Value left, Value right, int line) {
        EqualityState state = new EqualityState();
        boolean result = valuesEqual(left, right, 0, state, line);
        extraCost += (MAX_EQUALITY_STEPS - state.remaining);
        return result;
    }

    private boolean valuesEqual(Value left, Value right, int depth, EqualityState state, int line) {
        if (left == right) {
            return true;
        }
        if (state.remaining-- <= 0) {
            throw error("RuntimeError", "equality comparison exceeded complexity limit", line);
        }
        if (depth > 64) {
            return false;
        }
        if (left.isNone() || right.isNone()) {
            return left.isNone() && right.isNone();
        }
        if (left.isBool() || right.isBool()) {
            return left.isBool() && right.isBool() && left.asBool() == right.asBool();
        }
        if (left.isInt() && right.isInt()) {
            return left.asLong() == right.asLong();
        }
        if (left.isFloat() && right.isFloat()) {
            return left.asDouble() == right.asDouble();
        }
        if (isNumber(left) && isNumber(right)) {
            return numberAsDouble(left) == numberAsDouble(right);
        }
        if (left.isString() && right.isString()) {
            return left.asString().equals(right.asString());
        }
        if (left.isList() && right.isList()) {
            List<Value> a = left.asList();
            List<Value> b = right.asList();
            if (a == b) {
                return true;
            }
            if (a.size() != b.size()) {
                return false;
            }
            IdentityHashMap<Object, Boolean> inner = state.seen.computeIfAbsent(a, ignored -> new IdentityHashMap<>());
            Boolean cached = inner.get(b);
            if (cached != null) {
                return cached;
            }
            inner.put(b, Boolean.TRUE);
            for (int i = 0; i < a.size(); i++) {
                if (!valuesEqual(a.get(i), b.get(i), depth + 1, state, line)) {
                    inner.put(b, Boolean.FALSE);
                    return false;
                }
            }
            return true;
        }
        if (left.isMap() && right.isMap()) {
            Map<String, Value> a = left.asMap();
            Map<String, Value> b = right.asMap();
            if (a == b) {
                return true;
            }
            if (!a.keySet().equals(b.keySet())) {
                return false;
            }
            IdentityHashMap<Object, Boolean> inner = state.seen.computeIfAbsent(a, ignored -> new IdentityHashMap<>());
            Boolean cached = inner.get(b);
            if (cached != null) {
                return cached;
            }
            inner.put(b, Boolean.TRUE);
            for (Map.Entry<String, Value> entry : a.entrySet()) {
                if (!valuesEqual(entry.getValue(), b.get(entry.getKey()), depth + 1, state, line)) {
                    inner.put(b, Boolean.FALSE);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean truthy(Value value) {
        if (value.isNone()) {
            return false;
        }
        if (value.isBool()) {
            return value.asBool();
        }
        if (value.isInt()) {
            return value.asLong() != 0L;
        }
        if (value.isFloat()) {
            return value.asDouble() != 0.0;
        }
        if (value.isString()) {
            return !value.asString().isEmpty();
        }
        if (value.isList()) {
            return !value.asList().isEmpty();
        }
        if (value.isMap()) {
            return !value.asMap().isEmpty();
        }
        return true;
    }

    private void callBuiltin(Frame frame, String name, Value[] args, int line) {
        switch (name) {
            case "print" -> {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        builder.append(' ');
                    }
                    builder.append(args[i].display());
                }
                String text = builder.toString();
                extraCost += text.length() / 64;
                host.print(text);
                frame.push(Value.none());
            }
            case "len" -> {
                requireArity("len", args, 1, 1, line);
                Value value = args[0];
                if (value.isString()) {
                    String text = value.asString();
                    frame.push(Value.of(text.codePointCount(0, text.length())));
                } else if (value.isList()) {
                    frame.push(Value.of(value.asList().size()));
                } else if (value.isMap()) {
                    frame.push(Value.of(value.asMap().size()));
                } else {
                    throw error("TypeError", "object of type '" + value.typeName() + "' has no len()", line);
                }
            }
            case "range" -> {
                requireArity("range", args, 1, 3, line);
                long start = 0L;
                long stop;
                long step = 1L;
                if (args.length == 1) {
                    stop = requireInt(args[0], "range() stop", line);
                } else {
                    start = requireInt(args[0], "range() start", line);
                    stop = requireInt(args[1], "range() stop", line);
                    if (args.length == 3) {
                        step = requireInt(args[2], "range() step", line);
                    }
                }
                if (step == 0L) {
                    throw error("ValueError", "range() step must not be zero", line);
                }
                long count;
                try {
                    long distance = Math.subtractExact(stop, start);
                    if (step > 0L) {
                        count = distance <= 0L ? 0L : (distance - 1L) / step + 1L;
                    } else if (distance >= 0L) {
                        count = 0L;
                    } else {
                        long positiveStep = step == Long.MIN_VALUE ? Long.MAX_VALUE : -step;
                        long absoluteDistance = distance == Long.MIN_VALUE ? Long.MAX_VALUE : -distance;
                        count = (absoluteDistance - 1L) / positiveStep + 1L;
                    }
                } catch (ArithmeticException e) {
                    throw error("ValueError", "range() arguments are too large", line);
                }
                if (count > RANGE_LIMIT) {
                    throw error("ValueError", "range() result is too large (limit "
                            + RANGE_LIMIT + ")", line);
                }
                extraCost += (int) count;
                List<Value> values = new ArrayList<>((int) count);
                long current = start;
                for (long i = 0L; i < count; i++) {
                    values.add(Value.of(current));
                    current += step;
                }
                frame.push(Value.list(values));
            }
            case "int" -> {
                requireArity("int", args, 1, 1, line);
                frame.push(Value.of(toInt(args[0], line)));
            }
            case "float" -> {
                requireArity("float", args, 1, 1, line);
                frame.push(Value.of(toDouble(args[0], line)));
            }
            case "str" -> {
                requireArity("str", args, 1, 1, line);
                String text = args[0].display();
                extraCost += text.length() / 64;
                frame.push(Value.of(text));
            }
            case "bool" -> {
                requireArity("bool", args, 1, 1, line);
                frame.push(Value.of(truthy(args[0])));
            }
            case "sleep" -> {
                requireArity("sleep", args, 1, 1, line);
                long millis = toSleepMillis(args[0], line);
                long now = host.currentTimeMillis();
                long until = millis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + millis;
                sleeping = true;
                sleepUntilMillis = until;
                frame.push(Value.none());
            }
            case "time" -> {
                requireArity("time", args, 0, 0, line);
                frame.push(Value.of(host.currentTimeMillis()));
            }
            case "log" -> {
                requireArity("log", args, 1, 1, line);
                host.print("[log] " + args[0].display());
                frame.push(Value.none());
            }
            default -> throw new ScriptRuntimeException("internal error: unknown builtin " + name, line);
        }
    }

    private void requireArity(String name, Value[] args, int min, int max, int line) {
        if (args.length >= min && args.length <= max) {
            return;
        }
        String expected;
        if (min == max) {
            expected = "exactly " + min + " argument" + (min == 1 ? "" : "s");
        } else {
            expected = min + " to " + max + " arguments";
        }
        throw error("TypeError", name + "() takes " + expected + " (" + args.length + " given)", line);
    }

    private long requireInt(Value value, String description, int line) {
        if (!value.isInt()) {
            throw error("TypeError", description + " must be an int, not " + value.typeName(), line);
        }
        return value.asLong();
    }

    private long toInt(Value value, int line) {
        if (value.isInt()) {
            return value.asLong();
        }
        if (value.isBool()) {
            return value.asBool() ? 1L : 0L;
        }
        if (value.isFloat()) {
            double number = value.asDouble();
            if (Double.isNaN(number) || number > Long.MAX_VALUE || number < Long.MIN_VALUE) {
                throw error("ValueError", "cannot convert this float to int", line);
            }
            return (long) number;
        }
        if (value.isString()) {
            String text = value.asString().trim();
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw error("ValueError", "invalid literal for int(): '" + text + "'", line);
            }
        }
        throw error("TypeError", "int() argument must be a number or string, not "
                + value.typeName(), line);
    }

    private double toDouble(Value value, int line) {
        if (value.isFloat()) {
            return value.asDouble();
        }
        if (value.isInt()) {
            return (double) value.asLong();
        }
        if (value.isBool()) {
            return value.asBool() ? 1.0 : 0.0;
        }
        if (value.isString()) {
            String text = value.asString().trim();
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw error("ValueError", "invalid literal for float(): '" + text + "'", line);
            }
        }
        throw error("TypeError", "float() argument must be a number or string, not "
                + value.typeName(), line);
    }

    private long toSleepMillis(Value value, int line) {
        double millis;
        if (value.isInt()) {
            millis = value.asLong();
        } else if (value.isFloat()) {
            millis = value.asDouble();
        } else {
            throw error("TypeError", "sleep() argument must be a number, not "
                    + value.typeName(), line);
        }
        if (Double.isNaN(millis)) {
            throw error("ValueError", "sleep() argument must be a number", line);
        }
        if (millis <= 0.0) {
            return 0L;
        }
        return millis >= Long.MAX_VALUE - 1.0 ? Long.MAX_VALUE - 1L : (long) millis;
    }

    private void fail(ScriptRuntimeException failure) {
        if (finished) {
            return;
        }
        int line = failure.hasLine()
                ? failure.line()
                : (frames.isEmpty() ? 1 : currentFrame().currentLine);
        StringBuilder message = new StringBuilder();
        message.append(program.name()).append(':').append(line).append(": ")
                .append(failure.getMessage() == null ? "script error" : failure.getMessage());
        int frameCount = frames.size();
        if (frameCount <= 20) {
            for (int i = frameCount - 1; i >= 0; i--) {
                appendFrame(message, frames.get(i));
            }
        } else {
            for (int i = frameCount - 1; i >= frameCount - 15; i--) {
                appendFrame(message, frames.get(i));
            }
            message.append('\n').append("  ... ").append(frameCount - 20).append(" more frames ...");
            for (int i = 4; i >= 0; i--) {
                appendFrame(message, frames.get(i));
            }
        }
        finished = true;
        sleeping = false;
        frames.clear();
        safeError(message.toString());
    }

    private static void appendFrame(StringBuilder message, Frame frame) {
        message.append('\n').append("  at ").append(frame.function.name)
                .append(" (line ").append(frame.currentLine).append(')');
    }

    private void safeError(String text) {
        try {
            host.error(text);
        } catch (RuntimeException ignored) {
            // A broken host must not make step() throw.
        }
    }

    private Frame currentFrame() {
        return frames.get(frames.size() - 1);
    }

    private Value popValue(Frame frame) {
        Object value = frame.pop();
        if (value instanceof Value typed) {
            return typed;
        }
        throw error("TypeError", "expected a value, found " + internalTypeName(value),
                frame.currentLine);
    }

    private Value peekValue(Frame frame) {
        Object value = frame.peek();
        if (value instanceof Value typed) {
            return typed;
        }
        throw error("TypeError", "expected a value, found " + internalTypeName(value),
                frame.currentLine);
    }

    private static String internalTypeName(Object value) {
        if (value instanceof FunctionInfo) {
            return "function";
        }
        if (value instanceof DeviceRef) {
            return "device";
        }
        if (value instanceof IteratorState) {
            return "iterator";
        }
        if (value == DEVICES_MARKER) {
            return "devices";
        }
        return String.valueOf(value);
    }

    private static boolean isNumber(Value value) {
        return value.isInt() || value.isFloat();
    }

    private static double numberAsDouble(Value value) {
        return value.isInt() ? (double) value.asLong() : value.asDouble();
    }

    private static ScriptRuntimeException ensureLine(ScriptRuntimeException failure, int line) {
        return failure.hasLine() ? failure : new ScriptRuntimeException(failure.getMessage(), line);
    }

    private static String exceptionMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private static ScriptRuntimeException error(String kind, String message, int line) {
        return new ScriptRuntimeException(kind + ": " + message, line);
    }

    private static final class Frame {
        final FunctionInfo function;
        final Map<String, Object> locals;
        Object[] stack = new Object[16];
        int sp;
        int ip;
        int currentLine;

        Frame(FunctionInfo function, Map<String, Object> locals) {
            this.function = function;
            this.locals = locals;
            this.currentLine = function.line;
        }

        void push(Object value) {
            if (sp == stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            stack[sp++] = value;
        }

        Object pop() {
            if (sp == 0) {
                throw new ScriptRuntimeException("internal error: value stack underflow", currentLine);
            }
            Object value = stack[--sp];
            stack[sp] = null;
            return value;
        }

        Object peek() {
            if (sp == 0) {
                throw new ScriptRuntimeException("internal error: value stack underflow", currentLine);
            }
            return stack[sp - 1];
        }
    }
}
