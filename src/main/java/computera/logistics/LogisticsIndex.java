package computera.logistics;

import computera.blockentity.LogisticsPortBlockEntity;
import computera.cluster.Cluster;
import computera.device.DeviceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LogisticsIndex {
    public record Entry(String container, BlockPos pos, @Nullable Direction accessSide, int slot, long count) {
    }

    public static Map<String, List<Entry>> build(ServerLevel level, Cluster cluster) {
        Map<String, List<Entry>> index = new LinkedHashMap<>();
        for (var device : DeviceRegistry.entries(level, cluster).values()) {
            if (device.fluid()) {
                continue;
            }
            ResourceHandler<ItemResource> handler = DeviceRegistry.itemHandler(level, device);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                long amount = handler.getAmountAsLong(slot);
                if (!resource.isEmpty() && amount > 0) {
                    index.computeIfAbsent(itemId(resource), ignored -> new ArrayList<>())
                            .add(new Entry(device.key(), device.pos(), device.accessSide(), slot, amount));
                }
            }
        }
        return index;
    }

    public static long countAtPort(ServerLevel level, BlockPos portPos, String itemId) {
        long total = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = portPos.relative(direction);
            if (!level.hasChunkAt(neighbor)) {
                continue;
            }
            ResourceHandler<ItemResource> handler =
                    level.getCapability(Capabilities.Item.BLOCK, neighbor, direction.getOpposite());
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                if (!resource.isEmpty() && itemId.equals(itemId(resource))) {
                    total += handler.getAmountAsLong(slot);
                }
            }
        }
        return total;
    }

    public static long count(ServerLevel level, Cluster cluster, String itemId) {
        long total = 0;
        for (Entry entry : build(level, cluster).getOrDefault(itemId, List.of())) {
            total += entry.count();
        }
        return total;
    }

    public static int store(ServerLevel level, Cluster cluster, ServerPlayer player, String itemId, int amount) {
        Identifier parsed = Identifier.tryParse(itemId);
        if (parsed == null || amount <= 0) {
            return -1;
        }
        Item item = BuiltInRegistries.ITEM.getValue(parsed);
        if (item == null || item == Items.AIR) {
            return -1;
        }
        int remaining = amount;
        var inventory = player.getInventory();
        for (BlockPos portPos : cluster.ports()) {
            if (remaining <= 0) {
                break;
            }
            if (!level.hasChunkAt(portPos)
                    || !(level.getBlockEntity(portPos) instanceof LogisticsPortBlockEntity port)
                    || !itemId.equals(port.filter())) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                if (remaining <= 0) {
                    break;
                }
                BlockPos neighbor = portPos.relative(direction);
                if (!level.hasChunkAt(neighbor)) {
                    continue;
                }
                ResourceHandler<ItemResource> handler =
                        level.getCapability(Capabilities.Item.BLOCK, neighbor, direction.getOpposite());
                if (handler == null) {
                    continue;
                }
                for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.isEmpty() || !stack.is(item)) {
                        continue;
                    }
                    int want = Math.min(stack.getCount(), remaining);
                    int inserted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        inserted = handler.insert(ItemResource.of(stack), want, transaction);
                        transaction.commit();
                    }
                    if (inserted > 0) {
                        stack.shrink(inserted);
                        inventory.setChanged();
                        remaining -= inserted;
                    }
                }
            }
        }
        return amount - remaining;
    }

    public static int withdraw(ServerLevel level, Cluster cluster, ServerPlayer player, String itemId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int remaining = amount;
        for (Entry entry : build(level, cluster).getOrDefault(itemId, List.of())) {
            if (remaining <= 0) {
                break;
            }
            if (!level.hasChunkAt(entry.pos())) {
                continue;
            }
            ResourceHandler<ItemResource> handler =
                    level.getCapability(Capabilities.Item.BLOCK, entry.pos(), entry.accessSide());
            if (handler == null || entry.slot() < 0 || entry.slot() >= handler.size()) {
                continue;
            }
            ItemResource resource = handler.getResource(entry.slot());
            if (resource.isEmpty()) {
                continue;
            }
            int maxStack = Math.max(1, resource.toStack(1).getMaxStackSize());
            int want = (int) Math.min(Math.min(entry.count(), remaining), maxStack);
            int taken;
            try (Transaction transaction = Transaction.openRoot()) {
                taken = handler.extract(entry.slot(), resource, want, transaction);
                transaction.commit();
            }
            if (taken <= 0) {
                continue;
            }
            ItemStack stack = resource.toStack(taken);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.getInventory().setChanged();
            remaining -= taken;
        }
        return amount - remaining;
    }

    private static String itemId(ItemResource resource) {
        if (resource.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(resource.toStack(1).getItem()).toString();
    }

    private LogisticsIndex() {
    }
}
