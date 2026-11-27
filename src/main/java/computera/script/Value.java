package computera.script;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A dynamically typed script value. Supported runtime types are int (long),
 * float (double), bool, string, list, dict (string keys, insertion ordered)
 * and None.
 */
public final class Value {
    private enum Kind {
        INT, FLOAT, BOOL, STRING, LIST, MAP, NONE
    }

    private static final Value TRUE = new Value(Kind.BOOL, 0L, 0.0, true, null);
    private static final Value FALSE = new Value(Kind.BOOL, 0L, 0.0, false, null);
    private static final Value NONE = new Value(Kind.NONE, 0L, 0.0, false, null);

    private final Kind kind;
    private final long intValue;
    private final double floatValue;
    private final boolean boolValue;
    private final Object objectValue;

    private Value(Kind kind, long intValue, double floatValue, boolean boolValue, Object objectValue) {
        this.kind = kind;
        this.intValue = intValue;
        this.floatValue = floatValue;
        this.boolValue = boolValue;
        this.objectValue = objectValue;
    }

    private Value(Kind kind, Object objectValue) {
        this(kind, 0L, 0.0, false, objectValue);
    }

    public static Value of(long value) {
        return new Value(Kind.INT, value, 0.0, false, null);
    }

    public static Value of(double value) {
        return new Value(Kind.FLOAT, 0L, value, false, null);
    }

    public static Value of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static Value of(String value) {
        Objects.requireNonNull(value, "value");
        return new Value(Kind.STRING, value);
    }

    public static Value list(List<Value> values) {
        Objects.requireNonNull(values, "values");
        List<Value> copy = new ArrayList<>(values.size());
        for (Value value : values) {
            copy.add(Objects.requireNonNull(value, "list element"));
        }
        return new Value(Kind.LIST, copy);
    }

    public static Value map(Map<String, Value> values) {
        Objects.requireNonNull(values, "values");
        Map<String, Value> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Value> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "map key"),
                    Objects.requireNonNull(entry.getValue(), "map value"));
        }
        return new Value(Kind.MAP, copy);
    }

    public static Value none() {
        return NONE;
    }

    public boolean isInt() {
        return kind == Kind.INT;
    }

    public boolean isFloat() {
        return kind == Kind.FLOAT;
    }

    public boolean isBool() {
        return kind == Kind.BOOL;
    }

    public boolean isString() {
        return kind == Kind.STRING;
    }

    public boolean isList() {
        return kind == Kind.LIST;
    }

    public boolean isMap() {
        return kind == Kind.MAP;
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }

    public long asLong() {
        if (kind != Kind.INT) {
            throw new IllegalStateException("value is not an int: " + typeName());
        }
        return intValue;
    }

    public double asDouble() {
        if (kind != Kind.FLOAT) {
            throw new IllegalStateException("value is not a float: " + typeName());
        }
        return floatValue;
    }

    public boolean asBool() {
        if (kind != Kind.BOOL) {
            throw new IllegalStateException("value is not a bool: " + typeName());
        }
        return boolValue;
    }

    public String asString() {
        if (kind != Kind.STRING) {
            throw new IllegalStateException("value is not a string: " + typeName());
        }
        return (String) objectValue;
    }

    @SuppressWarnings("unchecked")
    public List<Value> asList() {
        if (kind != Kind.LIST) {
            throw new IllegalStateException("value is not a list: " + typeName());
        }
        return (List<Value>) objectValue;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Value> asMap() {
        if (kind != Kind.MAP) {
            throw new IllegalStateException("value is not a dict: " + typeName());
        }
        return (Map<String, Value>) objectValue;
    }

    public String typeName() {
        return switch (kind) {
            case INT -> "int";
            case FLOAT -> "float";
            case BOOL -> "bool";
            case STRING -> "string";
            case LIST -> "list";
            case MAP -> "dict";
            case NONE -> "none";
        };
    }

    public static final int MAX_FORMAT_CHARS = 16_384;
    private static final int MAX_FORMAT_NODES = 4_096;

    private static final class FormatState {
        final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();
        int remainingNodes = MAX_FORMAT_NODES;
        boolean truncated;
    }

    public String display() {
        StringBuilder builder = new StringBuilder();
        FormatState state = new FormatState();
        format(false, 0, state, builder);
        return builder.toString();
    }

    @Override
    public String toString() {
        return display();
    }

    private void format(boolean nested, int depth, FormatState state, StringBuilder builder) {
        if (state.truncated || builder.length() >= MAX_FORMAT_CHARS) {
            state.truncated = true;
            return;
        }
        if (depth > 64 || state.remainingNodes-- <= 0) {
            builder.append("...");
            if (state.remainingNodes < 0) {
                state.truncated = true;
            }
            return;
        }
        switch (kind) {
            case INT -> builder.append(intValue);
            case FLOAT -> builder.append(formatDouble(floatValue));
            case BOOL -> builder.append(boolValue ? "True" : "False");
            case STRING -> {
                String text = (String) objectValue;
                String rendered = nested ? quote(text) : text;
                int room = MAX_FORMAT_CHARS - builder.length();
                if (rendered.length() > room) {
                    if (room > 3) {
                        builder.append(rendered, 0, room - 3).append("...");
                    } else {
                        builder.append("...");
                    }
                    state.truncated = true;
                } else {
                    builder.append(rendered);
                }
            }
            case NONE -> builder.append("None");
            case LIST -> {
                List<Value> values = asList();
                if (state.active.put(values, Boolean.TRUE) != null) {
                    builder.append("[...]");
                    return;
                }
                try {
                    builder.append('[');
                    for (int i = 0; i < values.size(); i++) {
                        if (state.truncated || builder.length() >= MAX_FORMAT_CHARS) {
                            builder.append(", ...");
                            state.truncated = true;
                            break;
                        }
                        if (i > 0) {
                            builder.append(", ");
                        }
                        values.get(i).format(true, depth + 1, state, builder);
                    }
                    builder.append(']');
                } finally {
                    state.active.remove(values);
                }
            }
            case MAP -> {
                Map<String, Value> values = asMap();
                if (state.active.put(values, Boolean.TRUE) != null) {
                    builder.append("{...}");
                    return;
                }
                try {
                    builder.append('{');
                    boolean first = true;
                    for (Map.Entry<String, Value> entry : values.entrySet()) {
                        if (state.truncated || builder.length() >= MAX_FORMAT_CHARS) {
                            builder.append(", ...");
                            state.truncated = true;
                            break;
                        }
                        if (!first) {
                            builder.append(", ");
                        }
                        first = false;
                        builder.append(quote(entry.getKey())).append(": ");
                        entry.getValue().format(true, depth + 1, state, builder);
                    }
                    builder.append('}');
                } finally {
                    state.active.remove(values);
                }
            }
        }
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-inf";
        }
        return Double.toString(value);
    }

    private static String quote(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 2);
        builder.append('\'');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\'' -> builder.append("\\'");
                case '\n' -> builder.append("\\n");
                case '\t' -> builder.append("\\t");
                case '\r' -> builder.append("\\r");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\x%02x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.append('\'').toString();
    }
}
