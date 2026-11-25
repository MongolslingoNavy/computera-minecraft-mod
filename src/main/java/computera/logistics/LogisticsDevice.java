package computera.logistics;

import computera.cluster.Cluster;
import computera.blockentity.LogisticsPortBlockEntity;
import computera.script.Device;
import computera.script.ScriptRuntimeException;
import computera.script.Value;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public final class LogisticsDevice implements Device {
    private final ServerLevel level;
    private final Cluster cluster;

    public LogisticsDevice(ServerLevel level, Cluster cluster) {
        this.level = level;
        this.cluster = cluster;
    }

    @Override
    public Value call(String method, List<Value> args) {
        return switch (method) {
            case "count" -> Value.of(LogisticsIndex.count(level, cluster, stringArg(args, 0)));
            case "find" -> find(stringArg(args, 0));
            case "ports" -> ports();
            default -> throw new ScriptRuntimeException("logistics has no method " + method);
        };
    }

    @Override
    public Value get(String property) {
        throw new ScriptRuntimeException("logistics has no property " + property);
    }

    private Value find(String itemId) {
        List<Value> lines = new ArrayList<>();
        for (LogisticsIndex.Entry entry : LogisticsIndex.build(level, cluster).getOrDefault(itemId, List.of())) {
            lines.add(Value.of(entry.container() + " slot " + entry.slot() + " x " + entry.count()));
        }
        return Value.list(lines);
    }

    private Value ports() {
        List<Value> lines = new ArrayList<>();
        for (BlockPos pos : cluster.ports()) {
            if (level.getBlockEntity(pos) instanceof LogisticsPortBlockEntity port) {
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
