package computera.script;

/**
 * Services provided by the game to a running script.
 */
public interface ScriptHost {
    void print(String text);

    void error(String text);

    /**
     * Returns the device with the given name, or {@code null} when no such
     * device exists.
     */
    Device device(String name);

    long currentTimeMillis();
}
