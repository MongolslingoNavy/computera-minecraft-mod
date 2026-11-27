package computera.menu;

import computera.blockentity.CabinetBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CabinetContainer implements Container {
    private final CabinetBlockEntity cabinet;

    public CabinetContainer(CabinetBlockEntity cabinet) {
        this.cabinet = cabinet;
    }

    @Override
    public int getContainerSize() {
        return CabinetBlockEntity.CARD_SLOTS + CabinetBlockEntity.DRIVE_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        if (cabinet == null) {
            return true;
        }
        for (int i = 0; i < CabinetBlockEntity.CARD_SLOTS; i++) {
            if (!cabinet.card(i).isEmpty()) {
                return false;
            }
        }
        for (int i = 0; i < CabinetBlockEntity.DRIVE_SLOTS; i++) {
            if (!cabinet.drive(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (cabinet == null) {
            return ItemStack.EMPTY;
        }
        return slot < CabinetBlockEntity.CARD_SLOTS
                ? cabinet.card(slot)
                : cabinet.drive(slot - CabinetBlockEntity.CARD_SLOTS);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (cabinet == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = stack.split(count);
        setItem(slot, stack);
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (cabinet == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (cabinet != null) {
            if (slot < CabinetBlockEntity.CARD_SLOTS) {
                cabinet.setCard(slot, stack);
            } else {
                cabinet.setDrive(slot - CabinetBlockEntity.CARD_SLOTS, stack);
            }
        }
    }

    @Override
    public void setChanged() {
        if (cabinet != null) {
            cabinet.setChanged();
        }
    }

    @Override
    public void clearContent() {
        if (cabinet != null) {
            for (int slot = 0; slot < CabinetBlockEntity.CARD_SLOTS; slot++) {
                cabinet.setCard(slot, ItemStack.EMPTY);
            }
            for (int slot = 0; slot < CabinetBlockEntity.DRIVE_SLOTS; slot++) {
                cabinet.setDrive(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return cabinet != null && !cabinet.isRemoved()
                && player.level().getBlockEntity(cabinet.getBlockPos()) == cabinet
                && player.distanceToSqr(
                        cabinet.getBlockPos().getX() + 0.5,
                        cabinet.getBlockPos().getY() + 0.5,
                        cabinet.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
