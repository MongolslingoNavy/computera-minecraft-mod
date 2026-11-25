package computera.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Optional IC2: The New Era electricity bridge. IC2's EU grid is not exposed through
 * NeoForge's energy capability, so this class talks to it reflectively and converts
 * EU into FE for the adapter block.
 */
public final class IndustrialCraftEu {
    public static final double FE_PER_EU = 4.0;
    public static final double PACKET_LIMIT = 32.0;

    private static boolean checked;
    private static boolean available;
    private static Class<?> portInterface;
    private static Object portFaces;
    private static Constructor<?> portsConstructor;
    private static Method load;
    private static Method unload;

    public static synchronized boolean available() {
        if (checked) {
            return available;
        }
        checked = true;
        try {
            Class<?> faceClass = Class.forName("com.industrialage.ia2.platform.CapabilityFacade$Face");
            Set<Object> faces = new LinkedHashSet<>(Arrays.asList(faceClass.getEnumConstants()));
            Class<?> portsClass = Class.forName("com.industrialage.ia2.energy.graph.EnergyGraph$Ports");
            portsConstructor = portsClass.getConstructor(Set.class, Set.class, boolean.class);
            portFaces = portsConstructor.newInstance(faces, Set.of(), false);
            portInterface = Class.forName("com.industrialage.ia2.power.WorldPowerPort");
            Class<?> network = Class.forName("com.industrialage.ia2.gameplay.EnergyNetwork");
            load = network.getMethod("load", ServerLevel.class, portInterface);
            unload = network.getMethod("unload", ServerLevel.class, portInterface);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    public static Object createPort(BlockPos pos, BooleanSupplier removed, DoubleConsumer feSink) {
        if (!available()) {
            return null;
        }
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getBlockPos" -> {
                    return pos;
                }
                case "isRemoved" -> {
                    return removed.getAsBoolean();
                }
                case "ranThisTick" -> {
                    return true;
                }
                case "energyPorts" -> {
                    return portFaces;
                }
                case "receive" -> {
                    double offered = (double) args[0];
                    feSink.accept(offered * FE_PER_EU);
                    return offered;
                }
                case "packetLimit" -> {
                    return PACKET_LIMIT;
                }
                case "source" -> {
                    return false;
                }
                case "offer", "draw" -> {
                    return 0.0;
                }
                case "packetsPerTick" -> {
                    return 1;
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return "computera:eu_adapter";
                }
                default -> {
                    return defaultValue(method.getReturnType());
                }
            }
        };
        return Proxy.newProxyInstance(portInterface.getClassLoader(), new Class<?>[]{portInterface}, handler);
    }

    public static boolean register(ServerLevel level, Object port) {
        if (port == null || !available()) {
            return false;
        }
        try {
            load.invoke(null, level, port);
            return true;
        } catch (ReflectiveOperationException e) {
            computera.Computera.LOGGER.warn("Unable to register IC2 EU adapter", e);
            return false;
        }
    }

    public static void unregister(ServerLevel level, Object port) {
        if (port == null || !available()) {
            return;
        }
        try {
            unload.invoke(null, level, port);
        } catch (Throwable ignored) {
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    private IndustrialCraftEu() {
    }
}
