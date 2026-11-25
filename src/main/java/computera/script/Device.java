package computera.script;

import java.util.List;
import java.util.Set;

/**
 * A device exposed to scripts through {@code devices.<name>}.
 */
public interface Device {
    /**
     * Invokes a device method. Implementations should throw
     * {@link ScriptRuntimeException} for unknown methods.
     */
    Value call(String method, List<Value> args);

    Value get(String property);

    default Set<String> methodNames() {
        return Set.of();
    }
}
