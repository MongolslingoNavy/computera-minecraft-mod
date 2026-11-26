package computera.logistics;

import computera.blockentity.LogisticsPortBlockEntity;
import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.script.Device;
import computera.script.ScriptRuntimeException;
import computera.script.Value;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LogisticsDevice implements Device {
    private final ServerLevel level;
    @Nullable
    private final BlockPos anchorPos;
    @Nullable
    private final Cluster fallbackCluster;

    public LogisticsDevice(ServerLevel level, BlockPos anchorPos) {
        this.level = level;
        this.anchorPos = anchorPos.immutable();
        this.fallbackCluster = null;
    }

    public LogisticsDevice(ServerLevel level, Cluster cluster) {
        this.level = level;
        this.anchorPos = cluster.blocks().isEmpty() ? null : cluster.blocks().iterator().next().immutable();
        this.fallbackCluster = cluster;
    }

    private Cluster liveCluster() {
        if (anchorPos != null) {
            if (!level.hasChunkAt(anchorPos)) {
                throw new ScriptRuntimeException("logistics cluster unloaded");
            }
            Cluster live = ClusterManager.clusterFor(level, anchorPos);
            if (live == null) {
                throw new ScriptRuntimeException("logistics cluster disconnected");
            }
            return live;
        }
        if (fallbackCluster == null) {
            throw new ScriptRuntimeException("logistics cluster disconnected");
        }
        return fallbackCluster;
    }

    @Override
    public Value call(String method, List<Value> args) {
        Cluster cluster = liveCluster();
        return switch (method) {
            case "count" -> Value.of(LogisticsIndex.count(level, cluster, stringArg(args, 0)));
            case "find" -> find(cluster, stringArg(args, 0));
            case "ports" -> ports(cluster);
            default -> throw new ScriptRuntimeException("logistics has no method " + method);
        };
    }

    @Override
    public Value get(String property) {
        throw new ScriptRuntimeException("logistics has no property " + property);
    }

    private Value find(Cluster cluster, String itemId) {
        List<Value> lines = new ArrayList<>();
        for (LogisticsIndex.Entry entry : LogisticsIndex.build(level, cluster).getOrDefault(itemId, List.of())) {
            lines.add(Value.of(entry.container() + " slot " + entry.slot() + " x " + entry.count()));
        }
        return Value.list(lines);
    }

    private Value ports(Cluster cluster) {
        List<Value> lines = new ArrayList<>();
        for (BlockPos pos : cluster.ports()) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof LogisticsPortBlockEntity port) {
                lines.add(Value.of(pos.toShortString() + " -> " + (port.filter() == null ? "(none)" : port.filter())));
            }
        }
        return Value.list(lines);
    }

    private static String stringArg(List<Value> args, int index) {
        if (index >= args.size()) {
            throw new ScriptRuntimeException("missing argument " + (index + 1));
        }
        return args.get(index).asString();
    }
}
