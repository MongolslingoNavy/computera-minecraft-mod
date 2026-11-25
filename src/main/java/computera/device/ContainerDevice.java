package computera.device;

import computera.script.Device;
import computera.script.ScriptRuntimeException;
import computera.script.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContainerDevice implements Device {
    private final ResourceHandler<ItemResource> handler;

    public ContainerDevice(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
    }

    @Override
    public Value call(String method, List<Value> args) {
        return switch (method) {
            case "size" -> Value.of(handler.size());
            case "get" -> getItem(intArg(args, 0));
            case "take" -> Value.of(take(intArg(args, 0), intArg(args, 1)));
            case "put" -> Value.of(put(intArg(args, 0), stringArg(args, 1), intArg(args, 2)));
            case "move" -> Value.of(move(intArg(args, 0), intArg(args, 1), intArg(args, 2)));
            case "id" -> Value.of(itemId(handler.getResource(intArg(args, 0))));
            case "count" -> Value.of(handler.getAmountAsLong(intArg(args, 0)));
            default -> throw new ScriptRuntimeException("container has no method " + method);
        };
    }

    @Override
    public Value get(String property) {
        throw new ScriptRuntimeException("container has no property " + property);
    }

    private Value getItem(int slot) {
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

    private long move(int from, int to, int amount) {
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
