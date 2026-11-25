package computera.client;

import computera.net.TerminalActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TerminalScreen extends Screen {
    private static final int HEADER = 26;
    private static final int LINE_HEIGHT = 10;

    private final BlockPos pos;
    private final String kind;
    private final int threads;
    private final int used;
    private final List<String> output = new ArrayList<>();
    private final List<String> files = new ArrayList<>();

    private EditBox input;
    private int scroll;

    public TerminalScreen(BlockPos pos, String kind, int threads, int used, List<String> output, List<String> files) {
        super(Component.literal("Computera Terminal"));
        this.pos = pos;
        this.kind = kind;
        this.threads = threads;
        this.used = used;
        this.output.addAll(output);
        this.files.addAll(files);
        TerminalClientHandler.setActive(this);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    protected void init() {
        input = new EditBox(this.font, 12, this.height - 28, this.width - 24, 18, Component.literal("command"));
        input.setMaxLength(1024);
        input.setValue("");
        this.addRenderableWidget(input);
        this.setInitialFocus(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE60C0C0C);
        graphics.fill(0, 0, this.width, HEADER, 0xE6202020);
        graphics.text(this.font, kind + "  threads " + used + "/" + threads + "  files " + files.size(),
                10, 9, 0xFF9FE0B0, false);

        int lines = Math.max(1, (this.height - HEADER - 36) / LINE_HEIGHT);
        int first = Math.max(0, output.size() - lines - scroll);
        int y = HEADER + 4;
        for (int i = first; i < output.size() && i < first + lines; i++) {
            graphics.text(this.font, output.get(i), 12, y, 0xFFD8D8D8, false);
            y += LINE_HEIGHT;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            String text = input.getValue().trim();
            input.setValue("");
            if (!text.isEmpty()) {
                ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, "command", text, ""));
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Math.min(scroll + 5, Math.max(0, output.size() - 1));
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Math.max(scroll - 5, 0);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = (int) Math.max(0, Math.min(scroll + scrollY * 3, Math.max(0, output.size() - 1)));
        return true;
    }

    public void appendLines(List<String> lines) {
        output.addAll(lines);
        while (output.size() > 500) {
            output.removeFirst();
        }
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, "close", "", ""));
        TerminalClientHandler.clearActive(this);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
