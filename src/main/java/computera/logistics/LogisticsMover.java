package computera.logistics;

import computera.blockentity.LogisticsPortBlockEntity;
import computera.cluster.Cluster;
import computera.util.ItemFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LogisticsMover {
    public static final int ITEMS_PER_PIPE_PER_TICK = 4;

    private record Endpoint(BlockPos pos, ResourceHandler<ItemResource> handler) {
    }

    private record TagDestination(TagKey<Item> tag, Endpoint endpoint) {
    }

    public static int move(ServerLevel level, Cluster cluster) {
        int budget = cluster.pipes().size() * ITEMS_PER_PIPE_PER_TICK;
        if (budget <= 0) {
            return 0;
        }

        Map<String, List<Endpoint>> exactDestinations = new LinkedHashMap<>();
        List<TagDestination> tagDestinations = new ArrayList<>();
        List<Endpoint> sources = new ArrayList<>();
        Map<BlockPos, Boolean> seen = new HashMap<>();

        for (BlockPos portPos : cluster.ports()) {
            if (!level.hasChunkAt(portPos)) {
                continue;
            }
            String filter = level.getBlockEntity(portPos) instanceof LogisticsPortBlockEntity port
                    ? port.filter()
                    : null;
            for (Direction direction : Direction.values()) {
                BlockPos pos = portPos.relative(direction);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                BlockPos canonicalPos = level.getBlockEntity(pos) instanceof computera.blockentity.CrateBlockEntity crate
                        ? crate.groupRoot()
                        : pos;
                if (seen.putIfAbsent(canonicalPos, Boolean.TRUE) != null) {
                    continue;
                }
                ResourceHandler<ItemResource> handler =
                        level.getCapability(Capabilities.Item.BLOCK, pos, direction.getOpposite());
                if (handler == null) {
                    continue;
                }
                Endpoint endpoint = new Endpoint(canonicalPos, handler);
                if (filter != null && !filter.isBlank()) {
                    if (ItemFilter.isTagSpec(filter)) {
                        TagKey<Item> tag = ItemFilter.parseTag(filter);
                        if (tag != null) {
                            tagDestinations.add(new TagDestination(tag, endpoint));
                        }
                    } else {
                        String normalized = ItemFilter.normalize(filter);
                        exactDestinations.computeIfAbsent(normalized != null ? normalized : filter,
                                ignored -> new ArrayList<>()).add(endpoint);
                    }
                } else {
                    sources.add(endpoint);
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
                String id = BuiltInRegistries.ITEM.getKey(resource.getItem()).toString();
                List<Endpoint> exact = exactDestinations.get(id);
                List<Endpoint> targets = collectTargets(exact, tagDestinations, resource);
                if (targets.isEmpty()) {
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

    private static List<Endpoint> collectTargets(List<Endpoint> exact, List<TagDestination> tagDestinations,
                                                 ItemResource resource) {
        if (tagDestinations.isEmpty()) {
            return exact == null ? List.of() : exact;
        }
        Set<Endpoint> combined = new LinkedHashSet<>();
        if (exact != null) {
            combined.addAll(exact);
        }
        for (TagDestination dest : tagDestinations) {
            if (resource.typeHolder().is(dest.tag())) {
                combined.add(dest.endpoint());
            }
        }
        return combined.isEmpty() ? List.of() : List.copyOf(combined);
    }

    private LogisticsMover() {
    }
}
