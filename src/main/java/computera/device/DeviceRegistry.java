package computera.device;

import computera.cluster.Cluster;
import computera.script.Device;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DeviceRegistry {
    public static java.util.Map<String, BlockPos> positions(ServerLevel level, Cluster cluster) {
        java.util.Map<String, BlockPos> map = new java.util.LinkedHashMap<>();
        scanInto(level, cluster, (name, pos, device) -> map.put(name, pos));
        return map;
    }

    public static java.util.List<String> describe(ServerLevel level, Cluster cluster) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        scanInto(level, cluster, (name, pos, device) ->
                lines.add(name + "  " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath()
                        + " @ " + pos.toShortString()));
        return lines;
    }

    private interface Sink {
        void accept(String name, BlockPos pos, Device device);
    }

    private static void scanInto(ServerLevel level, Cluster cluster, Sink sink) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> crateRoots = new HashSet<>();
        Map<String, Integer> counters = new HashMap<>();
        Set<BlockPos> clusterBlocks = cluster.blocks();
        for (BlockPos base : clusterBlocks) {
            for (Direction direction : Direction.values()) {
                BlockPos pos = base.relative(direction);
                if (!level.hasChunkAt(pos) || clusterBlocks.contains(pos) || !seen.add(pos)) {
                    continue;
                }
                var itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, direction.getOpposite());
                if (itemHandler == null) {
                    itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
                }
                String type = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
                if (itemHandler != null) {
                    if (level.getBlockState(pos).getBlock() instanceof computera.block.CrateBlock
                            && level.getBlockEntity(pos) instanceof computera.blockentity.CrateBlockEntity crate
                            && !crateRoots.add(crate.groupRoot())) {
                        continue;
                    }
                    int index = counters.merge(type, 1, Integer::sum);
                    sink.accept(type + "_" + index, pos, new ContainerDevice(itemHandler));
                    continue;
                }
                var fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, direction.getOpposite());
                if (fluidHandler == null) {
                    fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
                }
                if (fluidHandler != null) {
                    int index = counters.merge(type + "_fluid", 1, Integer::sum);
                    sink.accept(type + "_fluid_" + index, pos, new FluidDevice(fluidHandler));
                }
            }
        }
        for (computera.cluster.Cluster ignored : java.util.List.of(cluster)) {
            for (BlockPos cabinetPos : cluster.cabinets()) {
                if (!(level.getBlockEntity(cabinetPos) instanceof computera.blockentity.CabinetBlockEntity cabinet)) {
                    continue;
                }
                for (BlockPos wirelessPos : cabinet.wireless()) {
                    if (!level.hasChunkAt(wirelessPos) || !seen.add(wirelessPos)) {
                        continue;
                    }
                    var handler = level.getCapability(Capabilities.Item.BLOCK, wirelessPos, null);
                    if (handler == null) {
                        continue;
                    }
                    if (level.getBlockEntity(wirelessPos) instanceof computera.blockentity.CrateBlockEntity crate
                            && !crateRoots.add(crate.groupRoot())) {
                        continue;
                    }
                    String type = BuiltInRegistries.BLOCK.getKey(level.getBlockState(wirelessPos).getBlock()).getPath();
                    int index = counters.merge(type + "_wireless", 1, Integer::sum);
                    sink.accept(type + "_wireless_" + index, wirelessPos, new ContainerDevice(handler));
                }
            }
        }
    }

    public static Map<String, Device> scan(ServerLevel level, Cluster cluster) {
        Map<String, Device> devices = new LinkedHashMap<>();
        scanInto(level, cluster, (name, pos, device) -> devices.put(name, device));
        return devices;
    }

    private DeviceRegistry() {
    }
}
