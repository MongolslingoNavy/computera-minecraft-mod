package computera.script;

import java.util.ArrayList;
import java.util.List;

final class IteratorState {
    private final Value list;
    private final String text;
    private final List<String> keys;
    private int index;

    private IteratorState(Value list, String text, List<String> keys) {
        this.list = list;
        this.text = text;
        this.keys = keys;
    }

    static IteratorState forList(Value list) {
        return new IteratorState(list, null, null);
    }

    static IteratorState forText(String text) {
        return new IteratorState(null, text, null);
    }

    static IteratorState forMap(Value map) {
        return new IteratorState(null, null, new ArrayList<>(map.asMap().keySet()));
    }

    boolean hasNext() {
        if (list != null) {
            return index < list.asList().size();
        }
        if (text != null) {
            return index < text.length();
        }
        return index < keys.size();
    }

    Value next() {
        if (list != null) {
            return list.asList().get(index++);
        }
        if (text != null) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            return Value.of(new String(Character.toChars(codePoint)));
        }
        return Value.of(keys.get(index++));
    }
}
