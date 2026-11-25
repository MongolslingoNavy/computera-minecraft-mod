package computera.blockentity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;

/** The single logical inventory of a whole crate group. */
public final class CrateGroupHandler implements ResourceHandler<ItemResource> {
    private final List<CrateBlockEntity> members;

    public CrateGroupHandler(List<CrateBlockEntity> members) {
        this.members = members;
    }

    private CrateBlockEntity crateAt(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        CrateBlockEntity crate = members.get(index / CrateBlockEntity.SLOTS);
        return crate == null || crate.isRemoved() ? null : crate;
    }

    @Override
    public int size() {
        return members.size() * CrateBlockEntity.SLOTS;
    }

    @Override
    public ItemResource getResource(int index) {
        CrateBlockEntity crate = crateAt(index);
        return crate == null ? ItemResource.EMPTY : crate.handler().getResource(index % CrateBlockEntity.SLOTS);
    }

    @Override
    public long getAmountAsLong(int index) {
        CrateBlockEntity crate = crateAt(index);
        return crate == null ? 0 : crate.handler().getAmountAsLong(index % CrateBlockEntity.SLOTS);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        CrateBlockEntity crate = crateAt(index);
        return crate == null ? 0 : crate.handler().getCapacityAsLong(index % CrateBlockEntity.SLOTS, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        CrateBlockEntity crate = crateAt(index);
        return crate != null && crate.handler().isValid(index % CrateBlockEntity.SLOTS, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        CrateBlockEntity crate = crateAt(index);
        return crate == null ? 0
                : crate.handler().insert(index % CrateBlockEntity.SLOTS, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        CrateBlockEntity crate = crateAt(index);
        return crate == null ? 0
                : crate.handler().extract(index % CrateBlockEntity.SLOTS, resource, amount, transaction);
    }

    // ---- helpers for the menu -------------------------------------------------

    public ItemStack getStack(int index) {
        CrateBlockEntity crate = crateAt(index);
        if (crate == null) {
            return ItemStack.EMPTY;
        }
        return crate.getItem(index % CrateBlockEntity.SLOTS);
    }

    public void setStack(int index, ItemStack stack) {
        CrateBlockEntity crate = crateAt(index);
        if (crate == null) {
            return;
        }
        int local = index % CrateBlockEntity.SLOTS;
        if (stack.isEmpty()) {
            crate.handler().set(local, ItemResource.EMPTY, 0);
        } else {
            crate.handler().set(local, ItemResource.of(stack), stack.getCount());
        }
    }
}
