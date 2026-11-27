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

public class TerminalEditorScreen extends Screen {
    private static final int HEADER = 26;
    private static final int LINE_HEIGHT = 12;

    private final BlockPos pos;
    private final String fileName;
    private final List<String> lines = new ArrayList<>();

    private EditBox currentLine;
    private int current;
    private int scroll;

    public TerminalEditorScreen(BlockPos pos, String name, String source) {
        super(Component.literal("Edit " + name));
        this.pos = pos;
        this.fileName = name;
        if (source == null || source.isEmpty()) {
            lines.add("");
        } else {
            for (String line : source.split("\n", -1)) {
                lines.add(line);
            }
        }
    }

    private int visibleLines() {
        return Math.max(1, (this.height - HEADER - 20) / LINE_HEIGHT);
    }

    @Override
    protected void init() {
        currentLine = new EditBox(this.font, 40, 0, this.width - 52, 10, Component.literal("line"));
        currentLine.setMaxLength(1024);
        currentLine.setValue(lines.get(current));
        currentLine.setResponder(value -> {
            if (current < lines.size()) {
                lines.set(current, value);
            }
        });
        this.addRenderableWidget(currentLine);
        this.setInitialFocus(currentLine);
        layoutCurrentLine();
    }

    private void layoutCurrentLine() {
        int maxLines = visibleLines();
        if (current < scroll) {
            scroll = current;
        } else if (current >= scroll + maxLines) {
            scroll = current - maxLines + 1;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - maxLines)));
        int y = HEADER + 4 + (current - scroll) * LINE_HEIGHT;
        currentLine.setY(y);
        currentLine.setVisible(current >= scroll && current < scroll + maxLines);
        currentLine.setValue(lines.get(current));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE60C0C0C);
        graphics.fill(0, 0, this.width, HEADER, 0xE6202020);
        graphics.text(this.font, "edit " + fileName + "   (Esc saves, Enter new line, Up/Down switch)  L"
                        + (current + 1) + "/" + lines.size(),
                10, 9, 0xFF9FE0B0, false);

        int maxLines = visibleLines();
        int end = Math.min(lines.size(), scroll + maxLines);
        int textWidth = Math.max(40, this.width - 54);
        for (int i = scroll; i < end; i++) {
            int y = HEADER + 4 + (i - scroll) * LINE_HEIGHT;
            if (i == current) {
                graphics.fill(30, y - 1, this.width - 10, y + LINE_HEIGHT - 3, 0x40204030);
            }
            String number = String.valueOf(i + 1);
            graphics.text(this.font, number, 32 - this.font.width(number), y, 0xFF6A6A6A, false);
            if (i != current) {
                String text = lines.get(i);
                if (this.font.width(text) > textWidth) {
                    text = this.font.plainSubstrByWidth(text, textWidth) + "…";
                }
                graphics.text(this.font, text, 42, y + 1, 0xFFD0DCD4, false);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, lines.size() - visibleLines());
        scroll = Math.max(0, Math.min(maxScroll, scroll + (scrollY < 0 ? 1 : -1)));
        int maxLines = visibleLines();
        currentLine.setY(HEADER + 4 + (current - scroll) * LINE_HEIGHT);
        currentLine.setVisible(current >= scroll && current < scroll + maxLines);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            saveAndBack();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP && current > 0) {
            current--;
            layoutCurrentLine();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN && current < lines.size() - 1) {
            current++;
            layoutCurrentLine();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (current == lines.size() - 1) {
                lines.add("");
            } else {
                lines.add(current + 1, "");
            }
            current++;
            layoutCurrentLine();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && current > 0 && currentLine.getValue().isEmpty()) {
            lines.remove(current);
            current--;
            layoutCurrentLine();
            return true;
        }
        return super.keyPressed(event);
    }

    private void saveAndBack() {
        String source = String.join("\n", lines);
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, "save", fileName, source));
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, "request", "", ""));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
