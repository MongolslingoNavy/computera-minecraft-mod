package computera.client;

import computera.menu.CabinetMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.Minecraft;

public class CabinetScreen extends AbstractContainerScreen<CabinetMenu> {
    public CabinetScreen(CabinetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
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

        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(menu.pos())
                        instanceof computera.blockentity.CabinetBlockEntity cabinet) {
            graphics.text(this.font, "FE " + cabinet.energy().getAmountAsLong()
                            + "  threads " + cabinet.installedThreads(),
                    x + 8, y + 64, 0xFF404040, false);
        }

        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            graphics.fill(sx, sy, sx + 1, sy + 16, 0xFFFFFFFF);
            graphics.fill(sx, sy, sx + 16, sy + 1, 0xFFFFFFFF);
        }
    }
}
