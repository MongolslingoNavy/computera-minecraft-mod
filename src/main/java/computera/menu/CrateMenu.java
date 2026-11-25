package computera.menu;

import computera.blockentity.CrateBlockEntity;
import computera.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CrateMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final CrateContainer container;

    public CrateMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.CRATE.get(), containerId);
        this.pos = pos;
        boolean clientSide = playerInventory.player.level().isClientSide();
        CrateBlockEntity crate = playerInventory.player.level().getBlockEntity(pos) instanceof CrateBlockEntity c
                ? c : null;
        this.container = new CrateContainer(crate, clientSide);

        for (int index = 0; index < CrateContainer.PAGE_SLOTS; index++) {
            this.addSlot(new Slot(container, index, 8 + (index % 9) * 18, 18 + (index / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return CrateMenu.this.container.canPlace(getContainerSlot());
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
        this.addDataSlots(container.data());
    }

    public BlockPos pos() {
        return pos;
    }

    public int page() {
        return container.page();
    }

    public int pages() {
        return container.pages();
    }

    public boolean isOnScreen(int index) {
        return index >= 0 && index < CrateContainer.PAGE_SLOTS && index < container.getContainerSize();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            container.setPage(container.page() - 1);
        } else if (id == 1) {
            container.setPage(container.page() + 1);
        } else {
            return false;
        }
        this.broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < CrateContainer.PAGE_SLOTS) {
            if (!this.moveItemStackTo(stack, CrateContainer.PAGE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, CrateContainer.PAGE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof CrateBlockEntity && container.stillValid(player)
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
    }

    public List<Slot> crateSlots() {
        return this.slots.subList(0, CrateContainer.PAGE_SLOTS);
    }
}
