package computera.client;

import computera.net.TerminalActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class CrtScreen extends Screen {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 244;
    private static final int ROW = 18;
    private static final int MAX_ROWS = 5;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final BlockPos pos;
    private final int threads;
    private final int used;
    private final boolean running;
    private final String runningFile;
    private final List<String> output = new ArrayList<>();
    private final List<String> files = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    private int left;
    private int top;
    private EditBox newName;

    public CrtScreen(BlockPos pos, int threads, int used, boolean running, String runningFile,
                     List<String> output, List<String> files) {
        super(Component.translatable("computera.gui.crt.title"));
        this.pos = pos;
        this.threads = threads;
        this.used = used;
        this.running = running;
        this.runningFile = runningFile;
        this.output.addAll(output);
        this.files.addAll(files);
    }

    @Override
    protected void init() {
        left = (this.width - WIDTH) / 2;
        top = (this.height - HEIGHT) / 2;
        hits.clear();

        int shown = Math.min(files.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            String name = files.get(i);
            int y = top + 48 + i * ROW;
            hits.add(new Hit(left + WIDTH - 132, y, 40, 14, () -> command("run " + name)));
            hits.add(new Hit(left + WIDTH - 88, y, 40, 14, () -> action("open", name, "")));
            hits.add(new Hit(left + WIDTH - 44, y, 36, 14, () -> command("rm " + name)));
        }

        int footer = top + HEIGHT - 26;
        hits.add(new Hit(left + 176, footer, 40, 16, () -> action("open", newName.getValue(), "")));
        hits.add(new Hit(left + 220, footer, 40, 16, () -> command("stop")));
        hits.add(new Hit(left + 264, footer, 48, 16, this::refresh));
        hits.add(new Hit(left + 316, footer, 44, 16, () -> action("logi", "", "")));
        hits.add(new Hit(left + 364, footer, 44, 16, this::openTextMode));
        hits.add(new Hit(left + 306, top + 4, 44, 14, () -> action("display_list", "", "")));
        hits.add(new Hit(left + 356, top + 4, 58, 14, () -> ClientPacketDistributor.sendToServer(
                new TerminalActionPayload(pos, "auto", "", ""))));

        this.newName = new EditBox(this.font, left + 52, footer, 120, 16,
                Component.translatable("computera.gui.crt.filename"));
        this.newName.setMaxLength(64);
        this.newName.setValue("program.py");
        this.addRenderableWidget(this.newName);
        this.setInitialFocus(this.newName);
    }

    private void command(String command) {
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, "command", command, ""));
        refresh();
    }

    private void action(String action, String name, String data) {
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, action, name, data));
    }

    private void refresh() {
        ClientPacketDistributor.sendToServer(new TerminalActionPayload(pos, "request", "", ""));
    }

    private void openTextMode() {
        this.minecraft.setScreen(new TerminalScreen(pos, "CRT", threads, used, output, files));
    }

    public BlockPos pos() {
        return pos;
    }

    public void appendLines(List<String> lines) {
        output.addAll(lines);
        while (output.size() > 200) {
            output.removeFirst();
        }
    }

    @Override
    public void onClose() {
        action("close", "", "");
        super.onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        for (Hit hit : hits) {
            if (hit.contains(event.x(), event.y())) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF2101812);
        graphics.fill(left, top, left + WIDTH, top + 2, 0xFF3E7A52);
        graphics.fill(left, top + HEIGHT - 2, left + WIDTH, top + HEIGHT, 0xFF3E7A52);
        graphics.fill(left, top, left + 2, top + HEIGHT, 0xFF2A4A34);
        graphics.fill(left + WIDTH - 2, top, left + WIDTH, top + HEIGHT, 0xFF2A4A34);

        graphics.fill(left + 2, top + 2, left + WIDTH - 2, top + 30, 0xFF16241A);
        graphics.text(this.font, Component.translatable("computera.gui.crt.title"),
                left + 10, top + 10, 0xFF8FE0A8, false);
        graphics.text(this.font, Component.translatable("computera.gui.crt.threads", used, threads),
                left + 100, top + 10, 0xFF7FB090, false);
        Component state = running
                ? Component.translatable("computera.gui.crt.running", runningFile)
                : Component.translatable("computera.gui.crt.idle");
        graphics.text(this.font, trim(state.getString(), 20), left + 172, top + 10,
                running ? 0xFFE0C86A : 0xFF7FB090, false);

        graphics.text(this.font, Component.translatable("computera.gui.crt.files"),
                left + 10, top + 36, 0xFF5F8A6A, false);
        if (files.isEmpty()) {
            graphics.text(this.font, Component.translatable("computera.gui.crt.empty"),
                    left + 12, top + 52, 0xFF4F6A58, false);
        }
        int shown = Math.min(files.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            int y = top + 48 + i * ROW;
            graphics.fill(left + 8, y, left + WIDTH - 136, y + 14, i % 2 == 0 ? 0xFF18261C : 0xFF141F18);
            graphics.text(this.font, trim(files.get(i), 34), left + 12, y + 3, 0xFFC8E8D0, false);
            drawButton(graphics, left + WIDTH - 132, y, 40, 14,
                    Component.translatable("computera.gui.crt.run"), mouseX, mouseY);
            drawButton(graphics, left + WIDTH - 88, y, 40, 14,
                    Component.translatable("computera.gui.crt.edit"), mouseX, mouseY);
            drawButton(graphics, left + WIDTH - 44, y, 36, 14,
                    Component.translatable("computera.gui.crt.delete"), mouseX, mouseY);
        }

        int outTop = top + 152;
        graphics.fill(left + 8, outTop - 4, left + WIDTH - 8, top + HEIGHT - 34, 0xFF0C140F);
        graphics.text(this.font, Component.translatable("computera.gui.crt.output"),
                left + 10, outTop, 0xFF5F8A6A, false);
        int start = Math.max(0, output.size() - 5);
        for (int i = start; i < output.size(); i++) {
            graphics.text(this.font, trim(output.get(i), 58),
                    left + 12, outTop + 12 + (i - start) * 10, 0xFFA8C8B0, false);
        }

        int footer = top + HEIGHT - 26;
        graphics.text(this.font, Component.translatable("computera.gui.crt.newname"),
                left + 10, footer + 4, 0xFF5F8A6A, false);
        drawButton(graphics, left + 176, footer, 40, 16,
                Component.translatable("computera.gui.crt.create"), mouseX, mouseY);
        drawButton(graphics, left + 220, footer, 40, 16,
                Component.translatable("computera.gui.crt.stop"), mouseX, mouseY);
        drawButton(graphics, left + 264, footer, 48, 16,
                Component.translatable("computera.gui.crt.refresh"), mouseX, mouseY);
        drawButton(graphics, left + 316, footer, 44, 16,
                Component.translatable("computera.gui.crt.logistics"), mouseX, mouseY);
        drawButton(graphics, left + 364, footer, 44, 16,
                Component.translatable("computera.gui.crt.text"), mouseX, mouseY);
        drawButton(graphics, left + 306, top + 4, 44, 14,
                Component.translatable("computera.gui.crt.displays"), mouseX, mouseY);
        drawButton(graphics, left + 356, top + 4, 58, 14,
                Component.translatable("computera.gui.auto.title"), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                            Component label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF2E5E40 : 0xFF213828);
        graphics.fill(x, y, x + w, y + 1, 0xFF3E7A52);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF16241A);
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
