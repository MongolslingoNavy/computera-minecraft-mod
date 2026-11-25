package computera.client;

import computera.net.TerminalActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AutomationScreen extends Screen {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 230;
    private static final int ROW = 18;
    private static final int MAX_ROWS = 6;

    private static final int PICK_COLS = 9;
    private static final int PICK_ROWS = 5;
    private static final int PICK_CELL = 20;
    private static final int PICK_W = PICK_COLS * PICK_CELL + 8;
    private static final int PICK_H = PICK_ROWS * PICK_CELL + 52;

    private record ItemEntry(String id, long count) {
    }

    private final BlockPos pos;
    private final List<String> rules = new ArrayList<>();
    private final List<ItemEntry> networkItems = new ArrayList<>();
    private final List<ItemEntry> playerItems = new ArrayList<>();

    private int left;
    private int top;

    private boolean pickerOpen;
    private int pickerTab;
    private int pickerScroll;
    private String hoveredId;

    private EditBox fromBox;
    private EditBox toBox;
    private EditBox itemBox;
    private EditBox countBox;
    private EditBox intervalBox;

    public AutomationScreen(BlockPos pos, List<String> rules, List<String> items) {
        super(Component.translatable("computera.gui.auto.title"));
        this.pos = pos;
        this.rules.addAll(rules);
        for (String line : items) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                try {
                    networkItems.add(new ItemEntry(parts[0], Long.parseLong(parts[1])));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    @Override
    protected void init() {
        left = (this.width - WIDTH) / 2;
        top = (this.height - HEIGHT) / 2;

        int y1 = top + 154;
        int y2 = top + 176;
        fromBox = box(left + 10, y1, 90, "chest_1");
        toBox = box(left + 110, y1, 90, "chest_2");
        itemBox = box(left + 210, y1, 100, networkItems.isEmpty() ? "minecraft:iron_ingot" : networkItems.get(0).id());
        countBox = box(left + 10, y2, 40, "16");
        intervalBox = box(left + 60, y2, 50, "40");
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox box = new EditBox(this.font, x, y, w, 14, Component.literal(value));
        box.setMaxLength(64);
        box.setValue(value);
        this.addRenderableWidget(box);
        return box;
    }

    private void send(String action, String name, String data) {
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, action, name, data));
    }

    // ------------------------------------------------------------------ picker

    private void refreshPlayerItems() {
        playerItems.clear();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Map<String, Long> totals = new LinkedHashMap<>();
        var inventory = this.minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    (long) stack.getCount(), Long::sum);
        }
        totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> playerItems.add(new ItemEntry(entry.getKey(), entry.getValue())));
    }

    private List<ItemEntry> pickerEntries() {
        if (pickerTab == 1) {
            return playerItems;
        }
        if (pickerTab == 2) {
            if (this.minecraft == null || this.minecraft.player == null) {
                return List.of();
            }
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (held.isEmpty()) {
                return List.of();
            }
            return List.of(new ItemEntry(BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), held.getCount()));
        }
        return networkItems;
    }

    private int pickerLeft() {
        return left + (WIDTH - PICK_W) / 2;
    }

    private int pickerTop() {
        return top + (HEIGHT - PICK_H) / 2;
    }

    private int pickerGridY() {
        return pickerTop() + 34;
    }

    private void openPicker() {
        refreshPlayerItems();
        pickerOpen = true;
        pickerScroll = 0;
    }

    private boolean clickPicker(double mx, double my) {
        int px = pickerLeft();
        int py = pickerTop();
        if (mx < px || mx >= px + PICK_W || my < py || my >= py + PICK_H) {
            pickerOpen = false;
            return true;
        }
        for (int tab = 0; tab < 3; tab++) {
            int tx = px + 4 + tab * 56;
            if (mx >= tx && mx < tx + 54 && my >= py + 4 && my < py + 16) {
                pickerTab = tab;
                pickerScroll = 0;
                return true;
            }
        }
        if (mx >= px + PICK_W - 16 && my >= py + 3 && my < py + 16) {
            pickerOpen = false;
            return true;
        }
        int gx = px + 4;
        int gy = pickerGridY();
        int col = (int) ((mx - gx) / PICK_CELL);
        int row = (int) ((my - gy) / PICK_CELL);
        if (col >= 0 && col < PICK_COLS && row >= 0 && row < PICK_ROWS) {
            int index = pickerScroll * PICK_COLS * PICK_ROWS + row * PICK_COLS + col;
            List<ItemEntry> entries = pickerEntries();
            if (index < entries.size()) {
                itemBox.setValue(entries.get(index).id());
                pickerOpen = false;
            }
            return true;
        }
        return true;
    }

    private int pickerPageCount() {
        int size = pickerEntries().size();
        return Math.max(1, (size + PICK_COLS * PICK_ROWS - 1) / (PICK_COLS * PICK_ROWS));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (pickerOpen) {
            return clickPicker(mx, my);
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int y1 = top + 154;
        int y2 = top + 176;

        if (inside(mx, my, left + 312, y1 - 1, 18, 18)) {
            openPicker();
            return true;
        }
        if (inside(mx, my, left + 190, y2, 50, 14)) {
            String data = fromBox.getValue() + "|" + toBox.getValue() + "|" + itemBox.getValue()
                    + "|" + countBox.getValue() + "|" + intervalBox.getValue();
            send("auto_add", "move", data);
            return true;
        }
        if (inside(mx, my, left + 250, y2, 50, 14)) {
            send("request", "", "");
            return true;
        }

        int rows = Math.min(rules.size(), MAX_ROWS);
        for (int i = 0; i < rows; i++) {
            int y = top + 30 + i * ROW;
            if (inside(mx, my, left + WIDTH - 78, y, 34, 14)) {
                send("auto_toggle", String.valueOf(i), "");
                return true;
            }
            if (inside(mx, my, left + WIDTH - 40, y, 34, 14)) {
                send("auto_del", String.valueOf(i), "");
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pickerOpen) {
            pickerScroll = Math.max(0, Math.min(pickerPageCount() - 1,
                    pickerScroll + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ----------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF2101812);
        graphics.fill(left, top, left + WIDTH, top + 2, 0xFF3E7A52);

        graphics.text(this.font, Component.translatable("computera.gui.auto.title"),
                left + 10, top + 8, 0xFF8FE0A8, false);

        int rows = Math.min(rules.size(), MAX_ROWS);
        if (rules.isEmpty()) {
            graphics.text(this.font, Component.translatable("computera.gui.auto.empty"),
                    left + 12, top + 34, 0xFF4F6A58, false);
        }
        for (int i = 0; i < rows; i++) {
            String[] p = rules.get(i).split("\\|", 7);
            int y = top + 30 + i * ROW;
            graphics.fill(left + 8, y, left + WIDTH - 84, y + 14, i % 2 == 0 ? 0xFF18261C : 0xFF141F18);
            String summary = p.length >= 6
                    ? "搬运 " + p[1] + " -> " + p[2]
                      + "  " + p[3] + " x" + p[4] + "  /" + p[5] + "t"
                    : rules.get(i);
            graphics.text(this.font, trim(summary, 40), left + 12, y + 3,
                    p.length >= 7 && Boolean.parseBoolean(p[6]) ? 0xFFC8E8D0 : 0xFF6A7A6E, false);
            drawButton(graphics, left + WIDTH - 78, y, 34, 14,
                    Component.translatable("computera.gui.auto.toggle"), mouseX, mouseY);
            drawButton(graphics, left + WIDTH - 40, y, 34, 14,
                    Component.translatable("computera.gui.auto.delete"), mouseX, mouseY);
        }

        int y1 = top + 154;
        int y2 = top + 176;
        graphics.text(this.font, Component.translatable("computera.gui.auto.from"), left + 10, y1 - 10, 0xFF5F8A6A, false);
        graphics.text(this.font, Component.translatable("computera.gui.auto.to"), left + 110, y1 - 10, 0xFF5F8A6A, false);
        graphics.text(this.font, Component.translatable("computera.gui.auto.item"), left + 210, y1 - 10, 0xFF5F8A6A, false);
        graphics.text(this.font, Component.translatable("computera.gui.auto.count"), left + 10, y2 - 10, 0xFF5F8A6A, false);
        graphics.text(this.font, Component.translatable("computera.gui.auto.interval"), left + 60, y2 - 10, 0xFF5F8A6A, false);

        drawItemButton(graphics, mouseX, mouseY);

        drawButton(graphics, left + 190, y2, 50, 14,
                Component.translatable("computera.gui.auto.add"), mouseX, mouseY);
        drawButton(graphics, left + 250, y2, 50, 14,
                Component.translatable("computera.gui.auto.back"), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (pickerOpen) {
            drawPicker(graphics, mouseX, mouseY);
        }
    }

    private void drawItemButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + 312;
        int y = top + 153;
        boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
        graphics.fill(x, y, x + 18, y + 18, hover ? 0xFF2E5E40 : 0xFF213828);
        graphics.fill(x, y, x + 18, y + 1, 0xFF3E7A52);
        Item item = itemOf(itemBox.getValue());
        if (item != null && item != Items.AIR) {
            graphics.item(new ItemStack(item), x + 1, y + 1);
        } else {
            graphics.text(this.font, "?", x + 6, y + 5, 0xFF6A7A6E, false);
        }
        if (hover) {
            graphics.text(this.font, Component.translatable("computera.gui.auto.pick"),
                    x - 6, y - 12, 0xFFE0C86A, false);
        }
    }

    private void drawPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int px = pickerLeft();
        int py = pickerTop();
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        graphics.fill(px - 2, py - 2, px + PICK_W + 2, py + PICK_H + 2, 0xFF3E7A52);
        graphics.fill(px, py, px + PICK_W, py + PICK_H, 0xF0141F18);

        Component[] tabs = {
                Component.translatable("computera.gui.auto.tab.network"),
                Component.translatable("computera.gui.auto.tab.inventory"),
                Component.translatable("computera.gui.auto.tab.hand")};
        for (int tab = 0; tab < 3; tab++) {
            int tx = px + 4 + tab * 56;
            boolean sel = tab == pickerTab;
            graphics.fill(tx, py + 4, tx + 54, py + 16, sel ? 0xFF2E5E40 : 0xFF1B2A20);
            graphics.text(this.font, trim(tabs[tab].getString(), 9), tx + 3, py + 6,
                    sel ? 0xFFD8F0E0 : 0xFF8FB09A, false);
        }
        drawButton(graphics, px + PICK_W - 16, py + 3, 13, 13, Component.literal("x"), mouseX, mouseY);

        List<ItemEntry> entries = pickerEntries();
        int start = pickerScroll * PICK_COLS * PICK_ROWS;
        int gx = px + 4;
        int gy = pickerGridY();
        hoveredId = null;
        if (entries.isEmpty()) {
            graphics.text(this.font, Component.translatable("computera.gui.auto.pick_empty"),
                    gx + 2, gy + 30, 0xFF5F8A6A, false);
        }
        for (int row = 0; row < PICK_ROWS; row++) {
            for (int col = 0; col < PICK_COLS; col++) {
                int index = start + row * PICK_COLS + col;
                if (index >= entries.size()) {
                    continue;
                }
                ItemEntry entry = entries.get(index);
                int x = gx + col * PICK_CELL;
                int y = gy + row * PICK_CELL;
                boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                graphics.fill(x, y, x + 18, y + 18, hover ? 0xFFE0C86A : 0xFF2A4A34);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF18261C);
                Item item = itemOf(entry.id());
                if (item != null && item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, (int) Math.min(entry.count(), 64));
                    graphics.item(stack, x + 1, y + 1);
                    graphics.itemDecorations(this.font, stack, x + 1, y + 1);
                }
                if (hover) {
                    hoveredId = entry.id() + " x" + entry.count();
                }
            }
        }

        int footer = py + PICK_H - 12;
        if (hoveredId != null) {
            graphics.text(this.font, trim(hoveredId, 40), gx + 1, footer, 0xFFD8F0E0, false);
        } else {
            graphics.text(this.font, Component.translatable("computera.gui.auto.pick_hint"),
                    gx + 1, footer, 0xFF5F8A6A, false);
        }
        graphics.text(this.font, Component.literal((pickerScroll + 1) + "/" + pickerPageCount()),
                px + PICK_W - 26, footer, 0xFF5F8A6A, false);
    }

    private static Item itemOf(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(id.trim()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                            Component label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF2E5E40 : 0xFF213828);
        graphics.fill(x, y, x + w, y + 1, 0xFF3E7A52);
        int tx = x + (w - this.font.width(label)) / 2;
        graphics.text(this.font, label, tx, y + (h - 8) / 2, 0xFFC8E8D0, false);
    }

    private String trim(String text, int max) {
        return this.font.width(text) <= max * 6 ? text : this.font.plainSubstrByWidth(text, max * 6) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
