package computera.client;

import computera.menu.CrateMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CrateScreen extends AbstractContainerScreen<CrateMenu> {
    public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 222);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() - leftPos;
        double my = event.y() - topPos;
        if (my >= 4 && my < 16) {
            if (mx >= 100 && mx < 112) {
                sendButton(0);
                return true;
            }
            if (mx >= 148 && mx < 160) {
                sendButton(1);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x, y, x + imageWidth, y + 2, 0xFF8B8B8B);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF8B8B8B);
        graphics.fill(x, y, x + 2, y + imageHeight, 0xFF8B8B8B);
        graphics.fill(x + imageWidth - 2, y, x + imageWidth, y + imageHeight, 0xFF8B8B8B);

        for (var slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            graphics.fill(sx, sy, sx + 1, sy + 16, 0xFFFFFFFF);
            graphics.fill(sx, sy, sx + 16, sy + 1, 0xFFFFFFFF);
        }

        graphics.text(this.font, Component.literal((menu.page() + 1) + "/" + menu.pages()),
                x + 116, y + 6, 0xFF404040, false);
        drawArrow(graphics, x + 100, y + 4, "<", mouseX, mouseY);
        drawArrow(graphics, x + 148, y + 4, ">", mouseX, mouseY);
    }

    private void drawArrow(GuiGraphicsExtractor graphics, int x, int y, String label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
        graphics.fill(x, y, x + 12, y + 12, hover ? 0xFFA0A0A0 : 0xFF8B8B8B);
        graphics.text(this.font, Component.literal(label), x + 4, y + 2, 0xFF303030, false);
    }
}
