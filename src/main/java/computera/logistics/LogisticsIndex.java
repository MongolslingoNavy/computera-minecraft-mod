package computera.logistics;

import computera.blockentity.LogisticsPortBlockEntity;
import computera.cluster.Cluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LogisticsIndex {
    public record Entry(String container, BlockPos pos, int slot, long count) {
    }

    public static Map<String, List<Entry>> build(ServerLevel level, Cluster cluster) {
        Map<String, List<Entry>> index = new LinkedHashMap<>();
        for (var device : computera.device.DeviceRegistry.positions(level, cluster).entrySet()) {
            BlockPos pos = device.getValue();
            ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                long amount = handler.getAmountAsLong(slot);
                if (!resource.isEmpty() && amount > 0) {
                    index.computeIfAbsent(itemId(resource), ignored -> new ArrayList<>())
                            .add(new Entry(device.getKey(), pos, slot, amount));
                }
            }
        }
        return index;
    }

    public static long countAtPort(ServerLevel level, BlockPos portPos, String itemId) {
        long total = 0;
        for (Direction direction : Direction.values()) {
            ResourceHandler<ItemResource> handler =
                    level.getCapability(Capabilities.Item.BLOCK, portPos.relative(direction), direction.getOpposite());
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
        Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
        if (item == null) {
            return -1;
        }
        int remaining = amount;
        var inventory = player.getInventory();
        for (BlockPos portPos : cluster.ports()) {
            if (remaining <= 0) {
                break;
            }
            if (!(level.getBlockEntity(portPos) instanceof LogisticsPortBlockEntity port)
                    || !itemId.equals(port.filter())) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                if (remaining <= 0) {
                    break;
                }
                ResourceHandler<ItemResource> handler =
                        level.getCapability(Capabilities.Item.BLOCK, portPos.relative(direction), direction.getOpposite());
                if (handler == null) {
                    continue;
                }
                for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
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
        int remaining = amount;
        for (Entry entry : build(level, cluster).getOrDefault(itemId, List.of())) {
            if (remaining <= 0) {
                break;
            }
            ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, entry.pos(), null);
            if (handler == null) {
                continue;
            }
            ItemResource resource = handler.getResource(entry.slot());
            if (resource.isEmpty()) {
                continue;
            }
            int want = (int) Math.min(Math.min(entry.count(), remaining), Integer.MAX_VALUE);
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
