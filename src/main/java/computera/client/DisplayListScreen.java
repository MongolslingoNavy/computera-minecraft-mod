package computera.client;

import computera.net.TerminalActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** CRT page: pick what each display wall shows. */
public class DisplayListScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 220;
    private static final int ROW = 22;
    private static final int MAX_ROWS = 6;

    private record WallView(String posText, int w, int h, String mode) {
    }

    private final BlockPos terminalPos;
    private final List<WallView> walls = new ArrayList<>();
    private int left;
    private int top;
    private int scroll;

    public DisplayListScreen(BlockPos terminalPos, List<String> raw) {
        super(Component.translatable("computera.gui.displays.title"));
        this.terminalPos = terminalPos;
        for (String line : raw) {
            String[] parts = line.split("\\|", 4);
            if (parts.length != 4) {
                continue;
            }
            try {
                walls.add(new WallView(parts[0], Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), parts[3]));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    protected void init() {
        left = (this.width - WIDTH) / 2;
        top = (this.height - HEIGHT) / 2;
        scroll = Math.min(scroll, Math.max(0, walls.size() - MAX_ROWS));
    }

    private void send(String action, String name, String data) {
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(terminalPos, action, name, data));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        if (inside(mx, my, left + 10, top + HEIGHT - 26, 56, 16)) {
            send("display_list", "", "");
            return true;
        }
        if (inside(mx, my, left + 70, top + HEIGHT - 26, 56, 16)) {
            send("request", "", "");
            return true;
        }
        int row = (int) ((my - (top + 46)) / ROW);
        if (row >= 0 && row < MAX_ROWS && mx >= left + 10 && mx < left + WIDTH - 10) {
            int index = scroll + row;
            if (index >= walls.size()) {
                return false;
            }
            WallView wall = walls.get(index);
            String[] modes = {"OVERVIEW", "ITEMS", "DEVICES"};
            for (int i = 0; i < 3; i++) {
                int x = left + 220 + i * 48;
                if (mx >= x && mx < x + 44) {
                    send("display_mode", wall.posText(), modes[i]);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, walls.size() - MAX_ROWS),
                scroll + (scrollY < 0 ? 1 : -1)));
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF2101812);
        graphics.fill(left, top, left + WIDTH, top + 2, 0xFF3E7A52);
        graphics.fill(left, top + HEIGHT - 2, left + WIDTH, top + HEIGHT, 0xFF3E7A52);

        graphics.text(this.font, Component.translatable("computera.gui.displays.title"),
                left + 10, top + 9, 0xFF8FE0A8, false);
        graphics.text(this.font, Component.translatable("computera.gui.displays.hint"),
                left + 130, top + 9, 0xFF5F8A6A, false);

        if (walls.isEmpty()) {
            graphics.text(this.font, Component.translatable("computera.gui.displays.empty"),
                    left + 12, top + 40, 0xFF4F6A58, false);
        }
        for (int row = 0; row < MAX_ROWS; row++) {
            int index = scroll + row;
            if (index >= walls.size()) {
                break;
            }
            WallView wall = walls.get(index);
            int y = top + 46 + row * ROW;
            graphics.fill(left + 8, y, left + WIDTH - 8, y + 18, row % 2 == 0 ? 0xFF18261C : 0xFF141F18);
            graphics.text(this.font, Component.literal(wall.posText()),
                    left + 12, y + 5, 0xFFC8E8D0, false);
            graphics.text(this.font, Component.literal(wall.w() + "x" + wall.h()),
                    left + 150, y + 5, 0xFF7FB090, false);
            String[] modes = {"OVERVIEW", "ITEMS", "DEVICES"};
            for (int i = 0; i < 3; i++) {
                int x = left + 220 + i * 48;
                boolean active = wall.mode().equalsIgnoreCase(modes[i]);
                drawModeButton(graphics, x, y + 2, 44, 14, modeLabel(modes[i]), active, mouseX, mouseY);
            }
        }
        if (walls.size() > MAX_ROWS) {
            graphics.text(this.font, Component.literal((scroll + 1) + "/" + (walls.size() - MAX_ROWS + 1)),
                    left + WIDTH - 46, top + HEIGHT - 21, 0xFF5F8A6A, false);
        }

        drawButton(graphics, left + 10, top + HEIGHT - 26, 56, 16,
                Component.translatable("computera.gui.displays.refresh"), mouseX, mouseY);
        drawButton(graphics, left + 70, top + HEIGHT - 26, 56, 16,
                Component.translatable("computera.gui.displays.back"), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawModeButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                Component label, boolean active, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, active ? 0xFF2E5E40 : (hover ? 0xFF243C2C : 0xFF1B2A20));
        graphics.fill(x, y, x + w, y + 1, active ? 0xFF8FE0A8 : 0xFF3E7A52);
        int tx = x + (w - this.font.width(label)) / 2;
        graphics.text(this.font, label, tx, y + (h - 8) / 2, active ? 0xFFE0F8E8 : 0xFF9FC0A8, false);
    }

    private Component modeLabel(String mode) {
        if ("ITEMS".equalsIgnoreCase(mode)) {
            return Component.translatable("computera.gui.display.mode.items");
        }
        if ("DEVICES".equalsIgnoreCase(mode)) {
            return Component.translatable("computera.gui.display.mode.devices");
        }
        return Component.translatable("computera.gui.display.mode.overview");
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
    public void onClose() {
        send("close", "", "");
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
