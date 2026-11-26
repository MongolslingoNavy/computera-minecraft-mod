package computera.menu;

import computera.blockentity.CrateBlockEntity;
import computera.blockentity.CrateGroupHandler;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * A 54-slot window over a crate group. The server reads the live group; the
 * client only keeps the synced stacks. Pages are switched with menu buttons.
 */
public class CrateContainer implements Container {
    public static final int PAGE_SLOTS = 54;

    private final CrateBlockEntity crate;
    private final boolean clientSide;
    private final NonNullList<ItemStack> clientStacks = NonNullList.withSize(PAGE_SLOTS, ItemStack.EMPTY);
    private CrateGroupHandler group;
    private long cachedTick = Long.MIN_VALUE;
    private int page;
    private int syncedPages = 1;
    private int syncedTotalSlots = CrateBlockEntity.SLOTS;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> page;
                case 1 -> pages();
                case 2 -> totalSlots();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                page = Math.max(0, value);
            } else if (index == 1) {
                syncedPages = Math.max(1, value);
                page = Math.min(page, syncedPages - 1);
            } else if (index == 2) {
                syncedTotalSlots = Math.max(0, value);
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CrateContainer(CrateBlockEntity crate, boolean clientSide) {
        this.crate = crate;
        this.clientSide = clientSide;
        if (!clientSide && crate != null) {
            refreshGroup();
        }
    }

    private void refreshGroup() {
        if (clientSide) {
            return;
        }
        if (crate == null || crate.isRemoved()) {
            group = null;
            cachedTick = Long.MIN_VALUE;
            return;
        }
        long now = crate.getLevel() == null ? Long.MIN_VALUE : crate.getLevel().getGameTime();
        if (group == null || now != cachedTick) {
            group = crate.groupHandler();
            cachedTick = now;
        }
        int total = group == null ? 0 : group.size();
        int maxPage = Math.max(0, (total - 1) / PAGE_SLOTS);
        if (page > maxPage) {
            page = maxPage;
        }
    }

    public ContainerData data() {
        return data;
    }

    public int page() {
        return page;
    }

    public int totalSlots() {
        if (clientSide) {
            return syncedTotalSlots;
        }
        refreshGroup();
        return group == null ? 0 : group.size();
    }

    public int pages() {
        if (clientSide) {
            return syncedPages;
        }
        int total = totalSlots();
        return Math.max(1, (total + PAGE_SLOTS - 1) / PAGE_SLOTS);
    }

    public void setPage(int next) {
        cachedTick = Long.MIN_VALUE;
        page = Math.max(0, Math.min(pages() - 1, next));
    }

    private int globalIndex(int slot) {
        if (!clientSide) {
            refreshGroup();
        }
        return page * PAGE_SLOTS + slot;
    }

    public boolean canPlace(int slot) {
        if (slot < 0 || slot >= PAGE_SLOTS) {
            return false;
        }
        int index = globalIndex(slot);
        if (clientSide) {
            return index >= 0 && index < syncedTotalSlots;
        }
        return group != null && index >= 0 && index < group.size();
    }

    @Override
    public int getContainerSize() {
        return PAGE_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < PAGE_SLOTS; slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= PAGE_SLOTS) {
            return ItemStack.EMPTY;
        }
        return clientSide ? clientStacks.get(slot) : (group == null ? ItemStack.EMPTY : group.getStack(globalIndex(slot)));
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack current = getItem(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = current.split(amount);
        setItem(slot, current.isEmpty() ? ItemStack.EMPTY : current);
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= PAGE_SLOTS) {
            return;
        }
        if (clientSide) {
            clientStacks.set(slot, stack);
            return;
        }
        if (group != null) {
            group.setStack(globalIndex(slot), stack);
        }
    }

    @Override
    public void setChanged() {
        cachedTick = Long.MIN_VALUE;
        if (!clientSide && crate != null && !crate.isRemoved()) {
            for (CrateBlockEntity member : crate.groupMembers()) {
                member.setChanged();
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (clientSide) {
            return true;
        }
        return crate != null && !crate.isRemoved()
                && player.level().getBlockEntity(crate.getBlockPos()) == crate
                && player.distanceToSqr(crate.getBlockPos().getX() + 0.5,
                        crate.getBlockPos().getY() + 0.5, crate.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
    }
}
