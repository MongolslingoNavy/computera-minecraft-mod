package computera.logistics;

import computera.blockentity.LogisticsPortBlockEntity;
import computera.cluster.Cluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LogisticsMover {
    public static final int ITEMS_PER_PIPE_PER_TICK = 4;

    private record Endpoint(BlockPos pos, ResourceHandler<ItemResource> handler) {
    }

    public static int move(ServerLevel level, Cluster cluster) {
        int budget = cluster.pipes().size() * ITEMS_PER_PIPE_PER_TICK;
        if (budget <= 0) {
            return 0;
        }

        Map<String, List<Endpoint>> destinations = new LinkedHashMap<>();
        List<Endpoint> sources = new ArrayList<>();
        Map<BlockPos, Boolean> seen = new HashMap<>();

        for (BlockPos portPos : cluster.ports()) {
            String filter = level.getBlockEntity(portPos) instanceof LogisticsPortBlockEntity port
                    ? port.filter()
                    : null;
            for (Direction direction : Direction.values()) {
                BlockPos pos = portPos.relative(direction);
                if (seen.putIfAbsent(pos, Boolean.TRUE) != null) {
                    continue;
                }
                ResourceHandler<ItemResource> handler =
                        level.getCapability(Capabilities.Item.BLOCK, pos, direction.getOpposite());
                if (handler == null) {
                    continue;
                }
                Endpoint endpoint = new Endpoint(pos, handler);
                sources.add(endpoint);
                if (filter != null) {
                    destinations.computeIfAbsent(filter, ignored -> new ArrayList<>()).add(endpoint);
                }
            }
        }

        int moved = 0;
        outer:
        for (Endpoint source : sources) {
            for (int slot = 0; slot < source.handler().size(); slot++) {
                ItemResource resource = source.handler().getResource(slot);
                if (resource.isEmpty()) {
                    continue;
                }
                String id = BuiltInRegistries.ITEM.getKey(resource.toStack(1).getItem()).toString();
                List<Endpoint> targets = destinations.get(id);
                if (targets == null || targets.isEmpty()) {
                    continue;
                }
                for (Endpoint target : targets) {
                    if (target.pos().equals(source.pos())) {
                        continue;
                    }
                    int remaining = budget - moved;
                    if (remaining <= 0) {
                        break outer;
                    }
                    int amount = (int) Math.min(Math.min(source.handler().getAmountAsLong(slot), remaining), 64);
                    int inserted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        int accepted;
                        try (Transaction probe = Transaction.open(transaction)) {
                            accepted = target.handler().insert(resource, amount, probe);
                        }
                        int extracted = source.handler().extract(slot, resource, accepted, transaction);
                        inserted = extracted > 0 ? target.handler().insert(resource, extracted, transaction) : 0;
                        if (inserted != extracted) {
                            inserted = 0;
                        }
                        if (inserted > 0) {
                            transaction.commit();
                        }
                    }
                    if (inserted > 0) {
                        moved += inserted;
                    }
                }
            }
        }
        return moved;
    }

    private LogisticsMover() {
    }
}
