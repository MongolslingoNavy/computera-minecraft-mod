package computera.cluster;

import computera.block.LogisticsPortBlock;
import computera.block.TerminalBlock;
import computera.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ClusterManager {
    public static final int MAX_CABINETS = 32;
    public static final int MAX_CABLE_DISTANCE = 64;

    private static final Map<ServerLevel, Cache> CACHES = new IdentityHashMap<>();
    private static final Map<ServerLevel, Long> GENERATIONS = new IdentityHashMap<>();

    private static final class Cache {
        final Map<BlockPos, Cluster> byBlock = new HashMap<>();
        final Set<Cluster> clusters = new LinkedHashSet<>();
    }

    public static long generation(ServerLevel level) {
        return GENERATIONS.getOrDefault(level, 0L);
    }

    public static Cluster clusterFor(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        Cache cache = CACHES.computeIfAbsent(level, ignored -> new Cache());
        Cluster known = cache.byBlock.get(pos);
        if (known != null) {
            return known;
        }
        Cluster cluster = build(level, pos, cache.byBlock);
        if (cluster != null) {
            cache.clusters.add(cluster);
            for (BlockPos blockPos : cluster.blocks()) {
                cache.byBlock.put(blockPos, cluster);
            }
        }
        return cluster;
    }

    public static void ensureCluster(ServerLevel level, BlockPos pos) {
        clusterFor(level, pos);
    }

    public static void invalidate(ServerLevel level, BlockPos pos) {
        CACHES.remove(level);
        GENERATIONS.merge(level, 1L, Long::sum);
    }

    public static void onLevelUnload(ServerLevel level) {
        CACHES.remove(level);
        GENERATIONS.remove(level);
    }

    public static void clearAll() {
        CACHES.clear();
        GENERATIONS.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Cache cache = CACHES.get(level);
            if (cache == null) {
                continue;
            }
            for (Cluster cluster : new java.util.ArrayList<>(cache.clusters)) {
                if (CACHES.get(level) != cache) {
                    break;
                }
                cluster.tick();
            }
        }
    }

    private static Cluster build(ServerLevel level, BlockPos origin, Map<BlockPos, Cluster> claimed) {
        if (!level.hasChunkAt(origin) || claimed.containsKey(origin)) {
            return null;
        }
        BlockState originState = level.getBlockState(origin);
        boolean originIsTerminal = originState.getBlock() instanceof TerminalBlock;
        boolean originIsPort = originState.getBlock() instanceof LogisticsPortBlock;
        if (!originIsTerminal && !originIsPort && !isClusterBlock(originState)) {
            return null;
        }

        Cluster cluster = new Cluster(level);
        Deque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        if (originIsTerminal || originIsPort) {
            if (originIsTerminal) {
                cluster.addTerminal(origin);
            } else {
                cluster.addPort(origin);
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = origin.relative(direction);
                if (!level.hasChunkAt(neighbor) || claimed.containsKey(neighbor)) {
                    continue;
                }
                if (isClusterBlock(level.getBlockState(neighbor))) {
                    queue.add(neighbor);
                    distances.put(neighbor, 1);
                }
            }
        } else {
            queue.add(origin);
            distances.put(origin, 0);
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos) || claimed.containsKey(pos)) {
                continue;
            }
            int distance = distances.getOrDefault(pos, 0);
            if (distance > MAX_CABLE_DISTANCE) {
                continue;
            }
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() == ModBlocks.CABINET.get()) {
                if (cluster.cabinetCount() >= MAX_CABINETS) {
                    continue;
                }
                cluster.addCabinet(pos);
            } else if (state.getBlock() == ModBlocks.CABLE.get()) {
                cluster.addCable(pos);
            } else if (state.getBlock() == ModBlocks.LOGISTICS_PIPE.get()) {
                cluster.addPipe(pos);
            } else {
                continue;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (visited.contains(neighbor) || claimed.containsKey(neighbor) || !level.hasChunkAt(neighbor)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.getBlock() instanceof TerminalBlock) {
                    cluster.addTerminal(neighbor);
                } else if (neighborState.getBlock() instanceof LogisticsPortBlock) {
                    cluster.addPort(neighbor);
                } else if (isClusterBlock(neighborState)) {
                    distances.putIfAbsent(neighbor, distance + 1);
                    queue.add(neighbor);
                }
            }
        }

        return cluster.cabinetCount() == 0 ? null : cluster;
    }

    private static boolean isClusterBlock(BlockState state) {
        return state.getBlock() == ModBlocks.CABINET.get()
                || state.getBlock() == ModBlocks.CABLE.get()
                || state.getBlock() == ModBlocks.LOGISTICS_PIPE.get();
    }

    private ClusterManager() {
    }
}
