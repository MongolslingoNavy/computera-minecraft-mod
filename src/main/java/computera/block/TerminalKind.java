package computera.block;

public enum TerminalKind {
    TELETYPE(false, true, false),
    CRT(true, false, true),
    LCD(true, false, true);

    private final boolean usesPower;
    private final boolean usesPaperTape;
    private final boolean showsStatus;

    TerminalKind(boolean usesPower, boolean usesPaperTape, boolean showsStatus) {
        this.usesPower = usesPower;
        this.usesPaperTape = usesPaperTape;
        this.showsStatus = showsStatus;
    }

    public boolean usesPower() {
        return usesPower;
    }

    public boolean usesPaperTape() {
        return usesPaperTape;
    }

    public boolean showsStatus() {
        return showsStatus;
    }
}
