package computera.menu;

import computera.blockentity.CabinetBlockEntity;
import computera.item.ComputeCardItem;
import computera.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class CabinetMenu extends AbstractContainerMenu {
    public static final int CARD_SLOTS = 8;
    public static final int DRIVE_SLOTS = 2;
    public static final int TOTAL_SLOTS = CARD_SLOTS + DRIVE_SLOTS;

    private final BlockPos pos;
    private final CabinetContainer container;
    private final CabinetBlockEntity cabinet;
    private int syncedEnergyLow;
    private int syncedEnergyHigh;
    private int syncedThreads;

    private final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            if (cabinet == null || cabinet.isRemoved()) {
                return 0;
            }
            int energy = cabinet.energy().getAmountAsInt();
            return switch (index) {
                case 0 -> energy & 0xFFFF;
                case 1 -> (energy >>> 16) & 0xFFFF;
                case 2 -> cabinet.installedThreads();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> syncedEnergyLow = value & 0xFFFF;
                case 1 -> syncedEnergyHigh = value & 0xFFFF;
                case 2 -> syncedThreads = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CabinetMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.CABINET.get(), containerId);
        this.pos = pos;
        this.cabinet = playerInventory.player.level().getBlockEntity(pos) instanceof CabinetBlockEntity c
                ? c
                : null;
        this.container = new CabinetContainer(this.cabinet);

        for (int i = 0; i < CARD_SLOTS; i++) {
            final int slotIndex = i;
            this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ComputeCardItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    container.setChanged();
                }
            });
        }

        for (int i = 0; i < DRIVE_SLOTS; i++) {
            this.addSlot(new Slot(container, CARD_SLOTS + i, 8 + i * 22, 46) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(computera.registry.ModItems.HARD_DRIVE.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    container.setChanged();
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        this.addDataSlots(this.data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int syncedEnergy() {
        return (syncedEnergyHigh << 16) | (syncedEnergyLow & 0xFFFF);
    }

    public int syncedThreads() {
        return syncedThreads;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < TOTAL_SLOTS) {
            if (!this.moveItemStackTo(stack, TOTAL_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof ComputeCardItem) {
            if (!this.moveItemStackTo(stack, 0, CARD_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(computera.registry.ModItems.HARD_DRIVE.get())) {
            if (!this.moveItemStackTo(stack, CARD_SLOTS, TOTAL_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
        return cabinet != null && !cabinet.isRemoved()
                && player.level().getBlockEntity(pos) == cabinet
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }
}
