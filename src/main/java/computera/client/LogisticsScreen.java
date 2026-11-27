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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogisticsScreen extends Screen {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 244;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int CELL = 20;
    private static final int PAGE = COLS * ROWS;
    private static final int TAB_W = 56;
    private static final int TAB_H = 14;
    private static final int MAX_TABS = 6;

    private record SlotView(String id, long count) {
    }

    private record DeviceView(String key, String alias, String group, String posText, int slotCount) {
    }

    private static String lastKey;
    private static int lastPage;
    private static int lastTabOffset;

    private final BlockPos pos;
    private static String lastGroup;
    private final List<DeviceView> allDevices = new ArrayList<>();
    private final List<DeviceView> devices = new ArrayList<>();
    private final List<SlotView> pageSlots = new ArrayList<>();
    private String serverSelectedKey = "";
    private String groupFilter;
    private final Set<String> distributeTargets = new java.util.LinkedHashSet<>();
    private ItemStack carried = ItemStack.EMPTY;

    private int left;
    private int top;
    private int selected;
    private int page;
    private int tabOffset;
    private boolean moveMode;
    private boolean distributeMode;
    private boolean giveMode;
    private int giveAmount = 16;
    private String giveRefId;
    private String sourceDeviceKey;
    private int sourceSlot = -1;
    private EditBox renameBox;
    private EditBox groupBox;

    private static final int FOOTER_Y = HEIGHT - 26;
    private static final int[] FOOTER_X = {10, 74, 138, 202, 258, 314};
    private static final int[] FOOTER_W = {60, 60, 60, 52, 52, 60};
    private static final int[] GIVE_STEPS = {1, 2, 4, 8, 16, 32, 64};

    public LogisticsScreen(BlockPos pos, List<String> rawDevices, String selectedKey,
                           int serverPage, List<String> rawSlots, String carriedText) {
        super(Component.translatable("computera.gui.logi.title"));
        this.pos = pos;
        this.serverSelectedKey = selectedKey == null ? "" : selectedKey;
        for (String line : rawDevices) {
            String[] parts = line.split("\\|", 5);
            if (parts.length != 5) {
                continue;
            }
            int slotCount = 0;
            try {
                slotCount = Math.max(0, Integer.parseInt(parts[4].trim()));
            } catch (NumberFormatException ignored) {
            }
            allDevices.add(new DeviceView(parts[0], parts[1], parts[2], parts[3], slotCount));
        }
        if (rawSlots != null) {
            for (String cell : rawSlots) {
                if (cell == null || cell.isEmpty() || cell.equals("-")) {
                    pageSlots.add(null);
                } else {
                    String[] sc = cell.split("=", 2);
                    try {
                        pageSlots.add(new SlotView(sc[0], Long.parseLong(sc[1])));
                    } catch (RuntimeException e) {
                        pageSlots.add(null);
                    }
                }
            }
        }
        groupFilter = lastGroup;
        if (!this.serverSelectedKey.isEmpty()) {
            lastKey = this.serverSelectedKey;
        }
        applyFilter();
        page = Math.max(0, Math.min(Math.max(0, serverPage), pageCount() - 1));
        lastPage = page;
        tabOffset = Math.max(0, lastTabOffset);

        if (carriedText != null && !carriedText.isEmpty()) {
            String[] cc = carriedText.split("=", 2);
            Identifier parsedId = Identifier.tryParse(cc[0]);
            Item item = parsedId == null ? null : BuiltInRegistries.ITEM.getValue(parsedId);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                int count = 1;
                try {
                    count = Integer.parseInt(cc[1]);
                } catch (RuntimeException ignored) {
                }
                this.carried = new ItemStack(item, count);
            }
        }
    }

    private int containerX() {
        return left + 10;
    }

    private int containerY() {
        return top + 74;
    }

    private int playerX() {
        return left + 236;
    }

    private int playerY() {
        return top + 74;
    }

    private int hotbarY() {
        return top + 140;
    }

    private int pageCount() {
        if (devices.isEmpty() || selected >= devices.size()) {
            return 1;
        }
        return Math.max(1, (devices.get(selected).slotCount() + PAGE - 1) / PAGE);
    }

    @Override
    protected void init() {
        left = (this.width - WIDTH) / 2;
        top = (this.height - HEIGHT) / 2;

        renameBox = new EditBox(this.font, left + 36, top + 44, 78, 14,
                Component.translatable("computera.gui.logi.rename"));
        renameBox.setMaxLength(32);
        renameBox.setValue(selectedAlias());
        this.addRenderableWidget(renameBox);

        groupBox = new EditBox(this.font, left + 188, top + 44, 62, 14,
                Component.translatable("computera.gui.logi.group"));
        groupBox.setMaxLength(16);
        groupBox.setValue(devices.isEmpty() ? "" : devices.get(Math.min(selected, devices.size() - 1)).group());
        this.addRenderableWidget(groupBox);

        page = Math.min(page, pageCount() - 1);
        tabOffset = Math.min(tabOffset, Math.max(0, devices.size() - MAX_TABS));
    }

    private void requestSelectedPage() {
        if (devices.isEmpty() || selected >= devices.size()) {
            return;
        }
        DeviceView device = devices.get(selected);
        lastKey = device.key();
        lastPage = page;
        lastTabOffset = tabOffset;
        send("logi_select", device.key(), String.valueOf(page));
    }

    private void applyFilter() {
        devices.clear();
        for (DeviceView device : allDevices) {
            if (groupFilter == null || groupFilter.isEmpty() || device.group().equals(groupFilter)) {
                devices.add(device);
            }
        }
        if (devices.isEmpty() && groupFilter != null) {
            groupFilter = null;
            for (DeviceView device : allDevices) {
                devices.add(device);
            }
        }
        int index = -1;
        if (lastKey != null) {
            for (int i = 0; i < devices.size(); i++) {
                if (devices.get(i).key().equals(lastKey)) {
                    index = i;
                    break;
                }
            }
        }
        if (index >= 0) {
            selected = index;
        }
        if (selected >= devices.size()) {
            selected = 0;
        }
        page = 0;
    }

    private void cycleGroup() {
        java.util.LinkedHashSet<String> groups = new java.util.LinkedHashSet<>();
        for (DeviceView device : allDevices) {
            if (!device.group().isEmpty()) {
                groups.add(device.group());
            }
        }
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add(null);
        options.addAll(groups);
        int current = options.indexOf(groupFilter);
        int next = (current + 1) % options.size();
        groupFilter = options.get(next);
        lastGroup = groupFilter;
        applyFilter();
        if (renameBox != null && groupBox != null) {
            renameBox.setValue(selectedAlias());
            groupBox.setValue(devices.isEmpty() ? "" : devices.get(Math.min(selected, devices.size() - 1)).group());
        }
        requestSelectedPage();
    }

    private String selectedAlias() {
        if (devices.isEmpty() || selected >= devices.size()) {
            return "";
        }
        return devices.get(selected).alias();
    }

    /** The item the give mode will dispense, and where it came from. */
    private String giveReferenceId() {
        if (giveRefId != null && !giveRefId.isEmpty()) {
            return giveRefId;
        }
        if (!carried.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (!held.isEmpty()) {
                return BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            }
        }
        return null;
    }

    private void cycleGiveAmount(int direction) {
        int index = 0;
        for (int i = 0; i < GIVE_STEPS.length; i++) {
            if (GIVE_STEPS[i] == giveAmount) {
                index = i;
                break;
            }
        }
        index = Math.max(0, Math.min(GIVE_STEPS.length - 1, index + direction));
        giveAmount = GIVE_STEPS[index];
    }

    private void sendGive() {
        String itemId = giveReferenceId();
        if (itemId == null || distributeTargets.isEmpty()) {
            return;
        }
        send("logi_give", String.join(",", distributeTargets), itemId + "|" + giveAmount);
    }

    private void syncEditBoxes() {
        boolean showRename = !giveMode;
        renameBox.setVisible(showRename);
        groupBox.setVisible(showRename);
    }

    private void send(String action, String name, String data) {
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, action, name, data));
    }

    @Override
    public void onClose() {
        send("close", "", "");
        super.onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        // footer buttons
        if (inside(mx, my, left + FOOTER_X[0], top + FOOTER_Y, FOOTER_W[0], 16)) {
            moveMode = !moveMode;
            distributeMode = false;
            giveMode = false;
            sourceDeviceKey = null;
            sourceSlot = -1;
            syncEditBoxes();
            return true;
        }
        if (inside(mx, my, left + FOOTER_X[1], top + FOOTER_Y, FOOTER_W[1], 16)) {
            distributeMode = !distributeMode;
            moveMode = false;
            giveMode = false;
            syncEditBoxes();
            return true;
        }
        if (inside(mx, my, left + FOOTER_X[2], top + FOOTER_Y, FOOTER_W[2], 16)) {
            giveMode = !giveMode;
            moveMode = false;
            distributeMode = false;
            sourceDeviceKey = null;
            sourceSlot = -1;
            syncEditBoxes();
            return true;
        }
        if (inside(mx, my, left + FOOTER_X[3], top + FOOTER_Y, FOOTER_W[3], 16)) {
            requestSelectedPage();
            return true;
        }
        if (inside(mx, my, left + FOOTER_X[4], top + FOOTER_Y, FOOTER_W[4], 16)) {
            send("request", "", "");
            return true;
        }
        if (inside(mx, my, left + FOOTER_X[5], top + FOOTER_Y, FOOTER_W[5], 16)) {
            if (!devices.isEmpty() && selected < devices.size()) {
                send("logi_open", devices.get(selected).key(), "");
            }
            return true;
        }
        if (giveMode) {
            if (inside(mx, my, left + 236, top + 44, 14, 14)) {
                cycleGiveAmount(-1);
                return true;
            }
            if (inside(mx, my, left + 296, top + 44, 14, 14)) {
                cycleGiveAmount(1);
                return true;
            }
            if (inside(mx, my, left + 316, top + 44, 46, 14)) {
                sendGive();
                return true;
            }
            if (inside(mx, my, left + 366, top + 44, 42, 14)) {
                giveRefId = null;
                return true;
            }
        } else {
            if (inside(mx, my, left + 116, top + 44, 38, 14)) {
                if (!devices.isEmpty() && selected < devices.size()) {
                    send("logi_rename", devices.get(selected).key(), renameBox.getValue());
                }
                return true;
            }
            if (inside(mx, my, left + 252, top + 44, 34, 14)) {
                if (!devices.isEmpty() && selected < devices.size()) {
                    String group = groupBox.getValue().isBlank() ? "" : groupBox.getValue();
                    send("logi_group", devices.get(selected).key(), group);
                    lastGroup = group.isEmpty() ? null : group;
                }
                return true;
            }
            if (inside(mx, my, left + 290, top + 44, 76, 14)) {
                cycleGroup();
                return true;
            }
        }

        // tab arrows
        if (inside(mx, my, left + 358, top + 26, 13, 14)) {
            tabOffset = Math.max(0, tabOffset - 1);
            lastTabOffset = tabOffset;
            return true;
        }
        if (inside(mx, my, left + 374, top + 26, 13, 14)) {
            tabOffset = Math.min(Math.max(0, devices.size() - MAX_TABS), tabOffset + 1);
            lastTabOffset = tabOffset;
            return true;
        }

        // tabs
        if (my >= top + 26 && my < top + 26 + TAB_H) {
            int index = (int) ((mx - (left + 10)) / (TAB_W + 2)) + tabOffset;
            if (mx >= left + 10 && index >= tabOffset && index < tabOffset + MAX_TABS
                    && (mx - (left + 10)) % (TAB_W + 2) <= TAB_W && index < devices.size()) {
                DeviceView device = devices.get(index);
                if (distributeMode || giveMode) {
                    if (!distributeTargets.remove(device.key())) {
                        distributeTargets.add(device.key());
                    }
                } else {
                    selected = index;
                    page = 0;
                    lastKey = device.key();
                    lastPage = 0;
                    lastTabOffset = tabOffset;
                    moveMode = false;
                    sourceDeviceKey = null;
                    sourceSlot = -1;
                    renameBox.setValue(device.alias());
                    groupBox.setValue(device.group());
                    requestSelectedPage();
                }
                return true;
            }
        }

        // page arrows
        if (inside(mx, my, left + 150, top + 60, 13, 14)) {
            if (page > 0) {
                page--;
                requestSelectedPage();
            }
            return true;
        }
        if (inside(mx, my, left + 190, top + 60, 13, 14)) {
            if (page + 1 < pageCount()) {
                page++;
                requestSelectedPage();
            }
            return true;
        }

        // container grid
        int col = (int) ((mx - containerX()) / CELL);
        int row = (int) ((my - containerY()) / CELL);
        if (col >= 0 && col < COLS && row >= 0 && row < ROWS && !devices.isEmpty()) {
            DeviceView device = devices.get(Math.min(selected, devices.size() - 1));
            int localSlot = row * COLS + col;
            int slot = page * PAGE + localSlot;
            if (slot < device.slotCount()) {
                containerClick(device, slot, localSlot, button);
                return true;
            }
        }

        // player inventory
        int pcol = (int) ((mx - playerX()) / CELL);
        int prow = (int) ((my - playerY()) / CELL);
        if (pcol >= 0 && pcol < COLS && prow >= 0 && prow < 3) {
            playerClick(9 + prow * COLS + pcol, button);
            return true;
        }
        int hcol = (int) ((mx - playerX()) / CELL);
        int hrow = (int) ((my - hotbarY()) / CELL);
        if (hcol >= 0 && hcol < COLS && hrow == 0) {
            playerClick(hcol, button);
            return true;
        }
        return false;
    }

    private void containerClick(DeviceView device, int slot, int localSlot, int button) {
        if (giveMode) {
            SlotView view = device.key().equals(serverSelectedKey) && localSlot >= 0 && localSlot < pageSlots.size()
                    ? pageSlots.get(localSlot) : null;
            giveRefId = view == null ? null : view.id();
            return;
        }
        if (!carried.isEmpty()) {
            send("logi_drop", device.key(), slot + (button == 1 ? ":one" : ""));
            return;
        }
        if (distributeMode) {
            if (!distributeTargets.isEmpty()) {
                send("logi_distribute", device.key() + "|" + String.join(",", distributeTargets), String.valueOf(slot));
            }
            return;
        }
        if (moveMode) {
            if (sourceDeviceKey == null) {
                sourceDeviceKey = device.key();
                sourceSlot = slot;
            } else if (!sourceDeviceKey.equals(device.key()) || sourceSlot != slot) {
                String spec = sourceDeviceKey + ":" + sourceSlot + ">" + device.key() + ":" + slot;
                send("logi_move", spec, "64");
                moveMode = false;
                sourceDeviceKey = null;
                sourceSlot = -1;
            }
            return;
        }
        send("logi_pickup", device.key(), slot + (button == 1 ? ":half" : ""));
    }

    private void playerClick(int slot, int button) {
        if (!carried.isEmpty()) {
            send("logi_drop", "player", slot + (button == 1 ? ":one" : ""));
        } else {
            send("logi_pickup", "player", slot + (button == 1 ? ":half" : ""));
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= containerX() && mouseX < containerX() + COLS * CELL
                && mouseY >= containerY() && mouseY < containerY() + ROWS * CELL) {
            if (pageCount() > 1) {
                int nextPage = Math.max(0, Math.min(pageCount() - 1, page + (scrollY < 0 ? 1 : -1)));
                if (nextPage != page) {
                    page = nextPage;
                    requestSelectedPage();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF2101812);
        graphics.fill(left, top, left + WIDTH, top + 2, 0xFF3E7A52);
        graphics.fill(left, top + HEIGHT - 2, left + WIDTH, top + HEIGHT, 0xFF3E7A52);

        graphics.text(this.font, Component.translatable("computera.gui.logi.title"),
                left + 10, top + 8, 0xFF8FE0A8, false);
        if (distributeMode) {
            graphics.text(this.font, Component.translatable("computera.gui.logi.targets", distributeTargets.size()),
                    left + 60, top + 8, 0xFFE0C86A, false);
        } else if (giveMode) {
            graphics.text(this.font, Component.translatable("computera.gui.logi.give.mode", giveAmount),
                    left + 60, top + 8, 0xFFE0C86A, false);
        } else if (moveMode) {
            graphics.text(this.font, Component.translatable("computera.gui.logi.moving"),
                    left + 60, top + 8, 0xFFE0C86A, false);
        }

        // tabs
        int tabs = Math.min(MAX_TABS, Math.max(0, devices.size() - tabOffset));
        for (int i = 0; i < tabs; i++) {
            DeviceView device = devices.get(tabOffset + i);
            int x = left + 10 + i * (TAB_W + 2);
            boolean sel = tabOffset + i == selected;
            boolean target = distributeTargets.contains(device.key());
            int border = target ? 0xFFE0C86A : (sel ? 0xFF3E7A52 : 0xFF2A4A34);
            graphics.fill(x, top + 26, x + TAB_W, top + 26 + TAB_H, 0xFF18261C);
            graphics.fill(x, top + 26, x + TAB_W, top + 27, border);
            graphics.fill(x, top + 26 + TAB_H - 1, x + TAB_W, top + 26 + TAB_H, border);
            graphics.text(this.font, trim(device.alias(), 8), x + 3, top + 30,
                    sel || target ? 0xFFD8F0E0 : 0xFF8FB09A, false);
        }
        drawButton(graphics, left + 358, top + 26, 13, 14, Component.literal("<"), mouseX, mouseY);
        drawButton(graphics, left + 374, top + 26, 13, 14, Component.literal(">"), mouseX, mouseY);

        // rename row / give row
        if (giveMode) {
            drawGiveRow(graphics, mouseX, mouseY);
        } else {
            graphics.text(this.font, Component.translatable("computera.gui.logi.rename"),
                    left + 10, top + 47, 0xFF5F8A6A, false);
            drawButton(graphics, left + 116, top + 44, 38, 14,
                    Component.translatable("computera.gui.logi.apply"), mouseX, mouseY);
            graphics.text(this.font, Component.translatable("computera.gui.logi.group"),
                    left + 158, top + 47, 0xFF5F8A6A, false);
            drawButton(graphics, left + 252, top + 44, 34, 14,
                    Component.translatable("computera.gui.logi.setgroup"), mouseX, mouseY);
            drawButton(graphics, left + 290, top + 44, 76, 14,
                    groupFilter == null
                            ? Component.translatable("computera.gui.logi.all")
                            : Component.literal(groupFilter), mouseX, mouseY);
        }

        if (devices.isEmpty()) {
            graphics.text(this.font, Component.translatable("computera.gui.logi.empty"),
                    left + 12, top + 80, 0xFF4F6A58, false);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        DeviceView device = devices.get(Math.min(selected, devices.size() - 1));
        graphics.text(this.font, Component.literal(device.alias() + "  " + device.posText()),
                left + 10, top + 62, 0xFF9FC0A8, false);
        graphics.text(this.font, Component.translatable("computera.gui.logi.container"),
                left + 10, top + 74, 0xFF5F8A6A, false);
        graphics.text(this.font, Component.translatable("computera.gui.logi.player"),
                playerX(), top + 74, 0xFF5F8A6A, false);
        drawButton(graphics, left + 150, top + 60, 13, 14, Component.literal("<"), mouseX, mouseY);
        graphics.text(this.font, Component.literal((page + 1) + "/" + pageCount()),
                left + 166, top + 63, 0xFF5F8A6A, false);
        drawButton(graphics, left + 190, top + 60, 13, 14, Component.literal(">"), mouseX, mouseY);

        drawContainer(graphics);
        drawPlayerInventory(graphics);

        graphics.text(this.font, Component.translatable(giveMode
                        ? "computera.gui.logi.give.hint" : "computera.gui.logi.hint"),
                left + 10, top + HEIGHT - 40, 0xFF5F8A6A, false);

        drawButton(graphics, left + FOOTER_X[0], top + FOOTER_Y, FOOTER_W[0], 16,
                Component.translatable("computera.gui.logi.move"), mouseX, mouseY);
        drawButton(graphics, left + FOOTER_X[1], top + FOOTER_Y, FOOTER_W[1], 16,
                Component.translatable("computera.gui.logi.distribute"), mouseX, mouseY);
        drawButton(graphics, left + FOOTER_X[2], top + FOOTER_Y, FOOTER_W[2], 16,
                Component.translatable("computera.gui.logi.give"), mouseX, mouseY);
        drawButton(graphics, left + FOOTER_X[3], top + FOOTER_Y, FOOTER_W[3], 16,
                Component.translatable("computera.gui.logi.refresh"), mouseX, mouseY);
        drawButton(graphics, left + FOOTER_X[4], top + FOOTER_Y, FOOTER_W[4], 16,
                Component.translatable("computera.gui.logi.back"), mouseX, mouseY);
        drawButton(graphics, left + FOOTER_X[5], top + FOOTER_Y, FOOTER_W[5], 16,
                Component.translatable("computera.gui.logi.native"), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (!carried.isEmpty()) {
            graphics.item(carried, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
        }
    }

    private void drawGiveRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, Component.translatable("computera.gui.logi.give.ref"),
                left + 10, top + 47, 0xFF5F8A6A, false);
        String refId = giveReferenceId();
        int iconX = left + 58;
        graphics.fill(iconX, top + 43, iconX + 16, top + 59, 0xFF2A4A34);
        graphics.fill(iconX + 1, top + 44, iconX + 15, top + 58, 0xFF18261C);
        if (refId != null) {
            Item item = null;
            try {
                item = BuiltInRegistries.ITEM.getValue(Identifier.parse(refId));
            } catch (RuntimeException ignored) {
            }
            if (item != null) {
                graphics.item(new ItemStack(item), iconX + 1, top + 44);
            }
            graphics.text(this.font, trim(refId, 22), iconX + 20, top + 47, 0xFFC8E8D0, false);
        } else {
            graphics.text(this.font, Component.translatable("computera.gui.logi.give.none"),
                    iconX + 20, top + 47, 0xFF7A6A4A, false);
        }
        drawButton(graphics, left + 236, top + 44, 14, 14, Component.literal("-"), mouseX, mouseY);
        graphics.text(this.font, Component.literal("x" + giveAmount),
                left + 254, top + 47, 0xFFE0C86A, false);
        drawButton(graphics, left + 296, top + 44, 14, 14, Component.literal("+"), mouseX, mouseY);
        drawButton(graphics, left + 316, top + 44, 46, 14,
                Component.translatable("computera.gui.logi.give.send"), mouseX, mouseY);
        drawButton(graphics, left + 366, top + 44, 42, 14,
                Component.translatable("computera.gui.logi.give.clear"), mouseX, mouseY);
    }

    private void drawContainer(GuiGraphicsExtractor graphics) {
        DeviceView device = devices.get(Math.min(selected, devices.size() - 1));
        int offset = page * PAGE;
        int gx = containerX();
        int gy = containerY();
        boolean matchesServer = device.key().equals(serverSelectedKey);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int localSlot = row * COLS + col;
                int slot = offset + localSlot;
                if (slot >= device.slotCount()) {
                    continue;
                }
                int x = gx + col * CELL;
                int y = gy + row * CELL;
                boolean isSource = device.key().equals(sourceDeviceKey) && sourceSlot == slot;
                drawSlot(graphics, x, y, isSource);
                SlotView view = matchesServer && localSlot < pageSlots.size() ? pageSlots.get(localSlot) : null;
                if (view != null) {
                    drawItem(graphics, view.id(), view.count(), x, y);
                }
            }
        }
    }

    private void drawPlayerInventory(GuiGraphicsExtractor graphics) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        var inventory = this.minecraft.player.getInventory();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = 9 + row * COLS + col;
                int x = playerX() + col * CELL;
                int y = playerY() + row * CELL;
                drawSlot(graphics, x, y, false);
                ItemStack stack = inventory.getItem(index);
                if (!stack.isEmpty()) {
                    graphics.item(stack, x + 1, y + 1);
                    graphics.itemDecorations(this.font, stack, x + 1, y + 1);
                }
            }
        }
        for (int col = 0; col < COLS; col++) {
            int x = playerX() + col * CELL;
            int y = hotbarY();
            drawSlot(graphics, x, y, false);
            ItemStack stack = inventory.getItem(col);
            if (!stack.isEmpty()) {
                graphics.item(stack, x + 1, y + 1);
                graphics.itemDecorations(this.font, stack, x + 1, y + 1);
            }
        }
    }

    private void drawSlot(GuiGraphicsExtractor graphics, int x, int y, boolean highlight) {
        graphics.fill(x, y, x + 18, y + 18, highlight ? 0xFFE0C86A : 0xFF2A4A34);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF18261C);
    }

    private void drawItem(GuiGraphicsExtractor graphics, String id, long count, int x, int y) {
        if (id == null || id.isEmpty() || count <= 0) {
            return;
        }
        Item item;
        try {
            item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        } catch (RuntimeException e) {
            computera.Computera.LOGGER.warn("logi invalid item id '{}'", id);
            return;
        }
        if (item == null) {
            return;
        }
        ItemStack stack = new ItemStack(item, (int) Math.min(count, 64));
        graphics.item(stack, x + 1, y + 1);
        graphics.itemDecorations(this.font, stack, x + 1, y + 1);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                            Component label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF2E5E40 : 0xFF213828);
        graphics.fill(x, y, x + w, y + 1, 0xFF3E7A52);
        int textX = x + (w - this.font.width(label)) / 2;
        graphics.text(this.font, label, textX, y + (h - 8) / 2, 0xFFC8E8D0, false);
    }

    private String trim(String text, int max) {
        return this.font.width(text) <= max * 6 ? text : this.font.plainSubstrByWidth(text, max * 6) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
