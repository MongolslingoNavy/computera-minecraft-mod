package computera.device;

import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.script.Device;
import computera.script.ScriptRuntimeException;
import computera.script.Value;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FluidDevice implements Device {
    @Nullable
    private final ResourceHandler<FluidResource> directHandler;
    @Nullable
    private final ServerLevel level;
    @Nullable
    private final BlockPos anchorPos;
    @Nullable
    private final String key;
    @Nullable
    private final BlockPos targetPos;

    public FluidDevice(ResourceHandler<FluidResource> handler) {
        this.directHandler = handler;
        this.level = null;
        this.anchorPos = null;
        this.key = null;
        this.targetPos = null;
    }

    public FluidDevice(ServerLevel level, BlockPos anchorPos, String key, BlockPos targetPos) {
        this.directHandler = null;
        this.level = level;
        this.anchorPos = anchorPos.immutable();
        this.key = key;
        this.targetPos = targetPos.immutable();
    }

    private ResourceHandler<FluidResource> resolveHandler() {
        if (directHandler != null) {
            return directHandler;
        }
        if (level == null || anchorPos == null || targetPos == null
                || !level.hasChunkAt(anchorPos) || !level.hasChunkAt(targetPos)) {
            throw new ScriptRuntimeException("tank disconnected: " + key);
        }
        Cluster cluster = ClusterManager.clusterFor(level, anchorPos);
        if (cluster == null) {
            throw new ScriptRuntimeException("tank disconnected: " + key);
        }
        DeviceRegistry.DeviceEntry entry = null;
        for (DeviceRegistry.DeviceEntry candidate : DeviceRegistry.entries(level, cluster).values()) {
            if (candidate.fluid() && candidate.pos().equals(targetPos)) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            throw new ScriptRuntimeException("tank disconnected: " + key);
        }
        ResourceHandler<FluidResource> handler = DeviceRegistry.fluidHandler(level, entry);
        if (handler == null) {
            throw new ScriptRuntimeException("tank unavailable: " + key);
        }
        return handler;
    }

    private static void checkSlot(ResourceHandler<FluidResource> handler, int index) {
        if (index < 0 || index >= handler.size()) {
            throw new ScriptRuntimeException("tank index out of range: " + index);
        }
    }

    @Override
    public Value call(String method, List<Value> args) {
        ResourceHandler<FluidResource> handler = resolveHandler();
        return switch (method) {
            case "size" -> Value.of(handler.size());
            case "get" -> getFluid(handler, intArg(args, 0));
            case "take" -> Value.of(take(intArg(args, 0), intArg(args, 1)));
            case "put" -> Value.of(put(intArg(args, 0), stringArg(args, 1), intArg(args, 2)));
            case "id" -> {
                int index = intArg(args, 0);
                checkSlot(handler, index);
                yield Value.of(fluidId(handler.getResource(index)));
            }
            case "count" -> {
                int index = intArg(args, 0);
                checkSlot(handler, index);
                yield Value.of(handler.getAmountAsLong(index));
            }
            default -> throw new ScriptRuntimeException("tank has no method " + method);
        };
    }

    @Override
    public Value get(String property) {
        throw new ScriptRuntimeException("tank has no property " + property);
    }

    private Value getFluid(ResourceHandler<FluidResource> handler, int index) {
        checkSlot(handler, index);
        FluidResource resource = handler.getResource(index);
        long amount = handler.getAmountAsLong(index);
        Map<String, Value> map = new LinkedHashMap<>();
        map.put("id", Value.of(fluidId(resource)));
        map.put("count", Value.of(amount));
        return Value.map(map);
    }

    private long take(int index, int amount) {
        throw new ScriptRuntimeException("take cannot discard fluids without a destination");
    }

    private long put(int index, String id, int amount) {
        throw new ScriptRuntimeException("put cannot create fluids");
    }

    private static String fluidId(FluidResource resource) {
        if (resource.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
    }

    private static int intArg(List<Value> args, int index) {
        if (index >= args.size()) {
            throw new ScriptRuntimeException("missing argument " + (index + 1));
        }
        return (int) args.get(index).asLong();
    }

    private static String stringArg(List<Value> args, int index) {
        if (index >= args.size()) {
            throw new ScriptRuntimeException("missing argument " + (index + 1));
        }
        return args.get(index).asString();
    }
}
