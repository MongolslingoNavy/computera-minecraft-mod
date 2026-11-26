package computera.client;

import computera.net.AutomationStatePayload;
import computera.net.LogisticsStatePayload;
import computera.net.TerminalFilePayload;
import computera.net.TerminalLinesPayload;
import computera.net.TerminalStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class TerminalClientHandler {
    private static TerminalScreen active;

    public static void handleState(TerminalStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if ("CRT".equals(payload.kind())) {
                Minecraft.getInstance().setScreen(new CrtScreen(payload.pos(), payload.threads(),
                        payload.used(), payload.running(), payload.runningFile(), payload.output(), payload.files()));
            } else {
                Minecraft.getInstance().setScreen(new TerminalScreen(payload.pos(), payload.kind(),
                        payload.threads(), payload.used(), payload.output(), payload.files()));
            }
        });
    }

    public static void handleLines(TerminalLinesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof TerminalScreen terminal && terminal.pos().equals(payload.pos())) {
                terminal.appendLines(payload.lines());
            } else if (screen instanceof CrtScreen crt && crt.pos().equals(payload.pos())) {
                crt.appendLines(payload.lines());
            }
        });
    }

    public static void handleAutomation(AutomationStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new AutomationScreen(payload.pos(), payload.rules(), payload.items())));
    }

    public static void handleLogistics(LogisticsStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new LogisticsScreen(payload.pos(), payload.devices(), payload.selectedKey(),
                        payload.page(), payload.slots(), payload.carried())));
    }

    public static void handleDisplay(computera.net.DisplayStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new DisplayScreen(payload.pos(), payload.wallW(), payload.wallH(), payload.mode(),
                        payload.lines(), payload.items(), payload.devices())));
    }

    public static void handleDisplayList(computera.net.DisplayListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new DisplayListScreen(payload.pos(), payload.walls())));
    }

    public static void handleFile(TerminalFilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new TerminalEditorScreen(payload.pos(), payload.name(), payload.source())));
    }

    static void setActive(TerminalScreen screen) {
        active = screen;
    }

    static void clearActive(TerminalScreen screen) {
        if (active == screen) {
            active = null;
        }
    }

    private TerminalClientHandler() {
    }
}
