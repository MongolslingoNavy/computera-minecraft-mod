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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContainerDevice implements Device {
    @Nullable
    private final ResourceHandler<ItemResource> directHandler;
    @Nullable
    private final ServerLevel level;
    @Nullable
    private final BlockPos anchorPos;
    @Nullable
    private final String key;
    @Nullable
    private final BlockPos targetPos;

    public ContainerDevice(ResourceHandler<ItemResource> handler) {
        this.directHandler = handler;
        this.level = null;
        this.anchorPos = null;
        this.key = null;
        this.targetPos = null;
    }

    public ContainerDevice(ServerLevel level, BlockPos anchorPos, String key, BlockPos targetPos) {
        this.directHandler = null;
        this.level = level;
        this.anchorPos = anchorPos.immutable();
        this.key = key;
        this.targetPos = targetPos.immutable();
    }

    private ResourceHandler<ItemResource> resolveHandler() {
        if (directHandler != null) {
            return directHandler;
        }
        if (level == null || anchorPos == null || targetPos == null
                || !level.hasChunkAt(anchorPos) || !level.hasChunkAt(targetPos)) {
            throw new ScriptRuntimeException("container disconnected: " + key);
        }
        Cluster cluster = ClusterManager.clusterFor(level, anchorPos);
        if (cluster == null) {
            throw new ScriptRuntimeException("container disconnected: " + key);
        }
        DeviceRegistry.DeviceEntry entry = null;
        for (DeviceRegistry.DeviceEntry candidate : DeviceRegistry.entries(level, cluster).values()) {
            if (!candidate.fluid() && candidate.pos().equals(targetPos)) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            throw new ScriptRuntimeException("container disconnected: " + key);
        }
        ResourceHandler<ItemResource> handler = DeviceRegistry.itemHandler(level, entry);
        if (handler == null) {
            throw new ScriptRuntimeException("container unavailable: " + key);
        }
        return handler;
    }

    private static void checkSlot(ResourceHandler<ItemResource> handler, int slot) {
        if (slot < 0 || slot >= handler.size()) {
            throw new ScriptRuntimeException("slot out of range: " + slot);
        }
    }

    @Override
    public Value call(String method, List<Value> args) {
        ResourceHandler<ItemResource> handler = resolveHandler();
        return switch (method) {
            case "size" -> Value.of(handler.size());
            case "get" -> getItem(handler, intArg(args, 0));
            case "take" -> Value.of(take(intArg(args, 0), intArg(args, 1)));
            case "put" -> Value.of(put(intArg(args, 0), stringArg(args, 1), intArg(args, 2)));
            case "move" -> Value.of(move(handler, intArg(args, 0), intArg(args, 1), intArg(args, 2)));
            case "id" -> {
                int slot = intArg(args, 0);
                checkSlot(handler, slot);
                yield Value.of(itemId(handler.getResource(slot)));
            }
            case "count" -> {
                int slot = intArg(args, 0);
                checkSlot(handler, slot);
                yield Value.of(handler.getAmountAsLong(slot));
            }
            case "has_tag" -> {
                int slot = intArg(args, 0);
                String tag = stringArg(args, 1);
                checkSlot(handler, slot);
                yield Value.of(computera.util.ItemFilter.hasTag(handler.getResource(slot), tag));
            }
            case "tags" -> {
                int slot = intArg(args, 0);
                checkSlot(handler, slot);
                List<Value> list = computera.util.ItemFilter.tagsOf(handler.getResource(slot))
                        .stream()
                        .map(Value::of)
                        .toList();
                yield Value.list(list);
            }
            default -> throw new ScriptRuntimeException("container has no method " + method);
        };
    }

    @Override
    public Value get(String property) {
        throw new ScriptRuntimeException("container has no property " + property);
    }

    private Value getItem(ResourceHandler<ItemResource> handler, int slot) {
        checkSlot(handler, slot);
        ItemResource resource = handler.getResource(slot);
        long amount = handler.getAmountAsLong(slot);
        Map<String, Value> map = new LinkedHashMap<>();
        map.put("id", Value.of(itemId(resource)));
        map.put("count", Value.of(amount));
        return Value.map(map);
    }

    private long take(int slot, int amount) {
        throw new ScriptRuntimeException("take is unsafe: use move or logistics automation to transfer existing items");
    }

    private long put(int slot, String id, int amount) {
        throw new ScriptRuntimeException("put cannot create items: use move or logistics automation");
    }

    private long move(ResourceHandler<ItemResource> handler, int from, int to, int amount) {
        checkSlot(handler, from);
        checkSlot(handler, to);
        if (amount <= 0 || from == to) {
            return 0;
        }
        ItemResource resource = handler.getResource(from);
        if (resource.isEmpty()) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted;
            try (Transaction probe = Transaction.open(transaction)) {
                accepted = handler.insert(to, resource, amount, probe);
            }
            int extracted = handler.extract(from, resource, accepted, transaction);
            if (extracted <= 0) {
                return 0;
            }
            int inserted = handler.insert(to, resource, extracted, transaction);
            if (inserted != extracted) {
                return 0;
            }
            transaction.commit();
            return inserted;
        }
    }

    private static String itemId(ItemResource resource) {
        if (resource.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(resource.toStack(1).getItem()).toString();
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
