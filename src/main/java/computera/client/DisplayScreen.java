package computera.client;

import computera.net.DisplayActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class DisplayScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 214;
    private static final int MAX_VISIBLE_ROWS = 12;

    private final BlockPos pos;
    private final int wallW;
    private final int wallH;
    private final computera.blockentity.DisplayMode mode;
    private final List<String> lines = new ArrayList<>();
    private final List<String> itemEntries = new ArrayList<>();
    private final List<String> deviceEntries = new ArrayList<>();

    private int left;
    private int top;
    private int scroll;

    public DisplayScreen(BlockPos pos, int wallW, int wallH, String modeText,
                         List<String> lines, List<String> items, List<String> devices) {
        super(Component.translatable("computera.gui.display.title"));
        this.pos = pos;
        this.wallW = wallW;
        this.wallH = wallH;
        this.mode = computera.blockentity.DisplayMode.byName(modeText);
        this.lines.addAll(lines);
        this.itemEntries.addAll(items);
        this.deviceEntries.addAll(devices);
    }

    @Override
    protected void init() {
        left = (this.width - WIDTH) / 2;
        top = (this.height - HEIGHT) / 2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        if (mx >= left + 10 && mx < left + 66 && my >= top + HEIGHT - 26 && my < top + HEIGHT - 10) {
            ClientPacketDistributor.sendToServer(new DisplayActionPayload(pos));
            return true;
        }
        if (mx >= left + 70 && mx < left + 126 && my >= top + HEIGHT - 26 && my < top + HEIGHT - 10) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF2101812);
        graphics.fill(left, top, left + WIDTH, top + 2, 0xFF3E7A52);
        graphics.fill(left, top + HEIGHT - 2, left + WIDTH, top + HEIGHT, 0xFF3E7A52);

        graphics.text(this.font, Component.translatable("computera.gui.display.title"),
                left + 10, top + 9, 0xFF8FE0A8, false);
        graphics.text(this.font, Component.literal(wallW + " x " + wallH + "  " + modeLabel()),
                left + 130, top + 9, 0xFF7FB090, false);
        graphics.text(this.font, Component.literal(pos.toShortString()),
                left + 210, top + 9, 0xFF4F6A58, false);

        graphics.fill(left + 8, top + 28, left + WIDTH - 8, top + HEIGHT - 34, 0xFF0C140F);
        List<String> content = DisplayText.content(mode, lines, itemEntries, deviceEntries, false);
        int maxScroll = Math.max(0, content.size() - MAX_VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int y = top + 34;
        int end = Math.min(content.size(), scroll + MAX_VISIBLE_ROWS);
        for (int i = scroll; i < end; i++) {
            int color = i == 0 ? 0xFF7FF0C8 : 0xFFB8E8C8;
            String line = content.get(i);
            if (this.font.width(line) > WIDTH - 28) {
                line = this.font.plainSubstrByWidth(line, WIDTH - 34) + "…";
            }
            graphics.text(this.font, Component.literal(line), left + 14, y, color, false);
            y += 12;
        }
        if (content.isEmpty()) {
            graphics.text(this.font, Component.translatable("computera.gui.display.empty"),
                    left + 14, y, 0xFF4F6A58, false);
        } else if (maxScroll > 0) {
            graphics.text(this.font, Component.literal((scroll + 1) + "/" + (maxScroll + 1)),
                    left + WIDTH - 42, top + HEIGHT - 45, 0xFF5F8A6A, false);
        }

        graphics.text(this.font, Component.translatable("computera.gui.display.hint"),
                left + 132, top + HEIGHT - 21, 0xFF5F8A6A, false);

        drawButton(graphics, left + 10, top + HEIGHT - 26, 56, 16,
                Component.translatable("computera.gui.display.refresh"), mouseX, mouseY);
        drawButton(graphics, left + 70, top + HEIGHT - 26, 56, 16,
                Component.translatable("computera.gui.display.close"), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<String> content = DisplayText.content(mode, lines, itemEntries, deviceEntries, false);
        int maxScroll = Math.max(0, content.size() - MAX_VISIBLE_ROWS);
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String modeLabel() {
        return switch (mode) {
            case ITEMS -> Component.translatable("computera.gui.display.mode.items").getString();
            case DEVICES -> Component.translatable("computera.gui.display.mode.devices").getString();
            default -> Component.translatable("computera.gui.display.mode.overview").getString();
        };
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                            Component label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF2E5E40 : 0xFF213828);
        graphics.fill(x, y, x + w, y + 1, 0xFF3E7A52);
        int tx = x + (w - this.font.width(label)) / 2;
        graphics.text(this.font, label, tx, y + (h - 8) / 2, 0xFFC8E8D0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
