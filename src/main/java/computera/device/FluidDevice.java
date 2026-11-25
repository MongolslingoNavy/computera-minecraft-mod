package computera.device;

import computera.script.Device;
import computera.script.ScriptRuntimeException;
import computera.script.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FluidDevice implements Device {
    private final ResourceHandler<FluidResource> handler;

    public FluidDevice(ResourceHandler<FluidResource> handler) {
        this.handler = handler;
    }

    @Override
    public Value call(String method, List<Value> args) {
        return switch (method) {
            case "size" -> Value.of(handler.size());
            case "get" -> getFluid(intArg(args, 0));
            case "take" -> Value.of(take(intArg(args, 0), intArg(args, 1)));
            case "put" -> Value.of(put(intArg(args, 0), stringArg(args, 1), intArg(args, 2)));
            case "id" -> Value.of(fluidId(handler.getResource(intArg(args, 0))));
            case "count" -> Value.of(handler.getAmountAsLong(intArg(args, 0)));
            default -> throw new ScriptRuntimeException("tank has no method " + method);
        };
    }

    @Override
    public Value get(String property) {
        throw new ScriptRuntimeException("tank has no property " + property);
    }

    private Value getFluid(int index) {
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
