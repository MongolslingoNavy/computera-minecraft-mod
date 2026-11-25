package computera.blockentity;

/** What a display wall shows. */
public enum DisplayMode {
    OVERVIEW,
    ITEMS,
    DEVICES;

    public static DisplayMode byName(String name) {
        for (DisplayMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return OVERVIEW;
    }
}
