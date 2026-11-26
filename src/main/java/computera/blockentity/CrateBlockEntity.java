package computera.blockentity;

import computera.menu.CrateMenu;
import computera.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A crate holds 27 slots. Face-to-face neighbours form one container whose
 * slots are simply the concatenation of every crate's slots, up to 64 crates.
 * Nothing about the group is stored: it is recomputed from the world, so
 * breaking a crate just drops its own contents and the rest re-group.
 */
public class CrateBlockEntity extends BlockEntity implements MenuProvider, net.minecraft.world.Container {
    public static final int SLOTS = 27;
    public static final int MAX_CRATES = 64;

    private final InventoryHandler handler = new InventoryHandler();

    private final class InventoryHandler extends ItemStacksResourceHandler {
        private InventoryHandler() {
            super(SLOTS);
        }

        private ItemStack stackAt(int slot) {
            return stacks.get(slot);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previous) {
            CrateBlockEntity.this.setChanged();
        }
    }

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRATE.get(), pos, state);
    }

    public ItemStacksResourceHandler handler() {
        return handler;
    }

    public ItemStack stack(int slot) {
        return getItem(slot);
    }

    public List<CrateBlockEntity> groupMembers() {
        List<CrateBlockEntity> members = new ArrayList<>();
        if (!(level instanceof ServerLevel serverLevel)) {
            members.add(this);
            return members;
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition.immutable());
        while (!queue.isEmpty() && members.size() < MAX_CRATES) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos) || !serverLevel.hasChunkAt(pos)) {
                continue;
            }
            if (!(serverLevel.getBlockEntity(pos) instanceof CrateBlockEntity crate) || crate.isRemoved()) {
                continue;
            }
            members.add(crate);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next)) {
                    queue.add(next);
                }
            }
        }
        members.sort(Comparator.comparingInt((CrateBlockEntity crate) -> crate.worldPosition.getY())
                .thenComparingInt(crate -> crate.worldPosition.getX())
                .thenComparingInt(crate -> crate.worldPosition.getZ()));
        return members;
    }

    public BlockPos groupRoot() {
        List<CrateBlockEntity> members = groupMembers();
        return members.isEmpty() ? worldPosition : members.get(0).worldPosition;
    }

    public CrateGroupHandler groupHandler() {
        return new CrateGroupHandler(groupMembers());
    }

    // Vanilla Container view of this single crate, used by /item replace and similar tools.
    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return ItemStack.EMPTY;
        }
        return handler.stackAt(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = getItem(slot);
        if (current.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = current.split(amount);
        setItem(slot, current);
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, Integer.MAX_VALUE);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        if (stack.isEmpty()) {
            handler.set(slot, ItemResource.EMPTY, 0);
        } else {
            handler.set(slot, ItemResource.of(stack), Math.min(stack.getCount(), stack.getMaxStackSize()));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SLOTS; slot++) {
            handler.set(slot, ItemResource.EMPTY, 0);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.computera.crate");
    }

    @Override
    public CrateMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CrateMenu(containerId, playerInventory, worldPosition);
    }

    public void dropContents(ServerLevel level, BlockPos pos) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, stack);
            }
            handler.set(slot, ItemResource.EMPTY, 0);
        }
        setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            dropContents(serverLevel, pos);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        handler.serialize(output.child("Inventory"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(handler::deserialize);
    }
}
