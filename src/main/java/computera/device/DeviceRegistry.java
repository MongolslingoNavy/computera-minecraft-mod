package computera.device;

import computera.block.CrateBlock;
import computera.blockentity.CabinetBlockEntity;
import computera.blockentity.CrateBlockEntity;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeviceRegistry {
    public record DeviceEntry(String key, BlockPos pos, @Nullable Direction accessSide, boolean fluid) {
    }

    public static Map<String, DeviceEntry> entries(ServerLevel level, Cluster cluster) {
        Map<String, DeviceEntry> map = new LinkedHashMap<>();
        scanEntries(level, cluster, entry -> map.put(entry.key(), entry));
        return map;
    }

    public static Map<String, BlockPos> positions(ServerLevel level, Cluster cluster) {
        Map<String, BlockPos> map = new LinkedHashMap<>();
        scanEntries(level, cluster, entry -> map.put(entry.key(), entry.pos()));
        return map;
    }

    public static List<String> describe(ServerLevel level, Cluster cluster) {
        List<String> lines = new ArrayList<>();
        scanEntries(level, cluster, entry ->
                lines.add(entry.key() + "  "
                        + BuiltInRegistries.BLOCK.getKey(level.getBlockState(entry.pos()).getBlock()).getPath()
                        + " @ " + entry.pos().toShortString()));
        return lines;
    }

    @Nullable
    public static ResourceHandler<ItemResource> itemHandler(ServerLevel level, DeviceEntry entry) {
        if (entry == null || entry.fluid() || !level.hasChunkAt(entry.pos())) {
            return null;
        }
        return level.getCapability(Capabilities.Item.BLOCK, entry.pos(), entry.accessSide());
    }

    @Nullable
    public static ResourceHandler<FluidResource> fluidHandler(ServerLevel level, DeviceEntry entry) {
        if (entry == null || !entry.fluid() || !level.hasChunkAt(entry.pos())) {
            return null;
        }
        return level.getCapability(Capabilities.Fluid.BLOCK, entry.pos(), entry.accessSide());
    }

    private interface EntrySink {
        void accept(DeviceEntry entry);
    }

    private static void scanEntries(ServerLevel level, Cluster cluster, EntrySink sink) {
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
                Direction contactFace = direction.getOpposite();
                Direction itemSide = contactFace;
                var itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, contactFace);
                if (itemHandler == null) {
                    itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
                    itemSide = null;
                }
                String type = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
                if (itemHandler != null) {
                    if (level.getBlockState(pos).getBlock() instanceof CrateBlock
                            && level.getBlockEntity(pos) instanceof CrateBlockEntity crate
                            && !crateRoots.add(crate.groupRoot())) {
                        continue;
                    }
                    int index = counters.merge(type, 1, Integer::sum);
                    sink.accept(new DeviceEntry(type + "_" + index, pos.immutable(), itemSide, false));
                    continue;
                }
                Direction fluidSide = contactFace;
                var fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, contactFace);
                if (fluidHandler == null) {
                    fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
                    fluidSide = null;
                }
                if (fluidHandler != null) {
                    int index = counters.merge(type + "_fluid", 1, Integer::sum);
                    sink.accept(new DeviceEntry(type + "_fluid_" + index, pos.immutable(), fluidSide, true));
                }
            }
        }
        for (BlockPos cabinetPos : cluster.cabinets()) {
            if (!level.hasChunkAt(cabinetPos)
                    || !(level.getBlockEntity(cabinetPos) instanceof CabinetBlockEntity cabinet)) {
                continue;
            }
            cabinet.pruneWireless(level);
            for (BlockPos wirelessPos : List.copyOf(cabinet.wireless())) {
                if (!level.hasChunkAt(wirelessPos) || !seen.add(wirelessPos)) {
                    continue;
                }
                Direction face = cabinet.wirelessFace(wirelessPos);
                var handler = level.getCapability(Capabilities.Item.BLOCK, wirelessPos, face);
                if (handler == null && face != null) {
                    handler = level.getCapability(Capabilities.Item.BLOCK, wirelessPos, null);
                    face = null;
                }
                if (handler == null) {
                    continue;
                }
                if (level.getBlockEntity(wirelessPos) instanceof CrateBlockEntity crate
                        && !crateRoots.add(crate.groupRoot())) {
                    continue;
                }
                String type = BuiltInRegistries.BLOCK.getKey(level.getBlockState(wirelessPos).getBlock()).getPath();
                int index = counters.merge(type + "_wireless", 1, Integer::sum);
                sink.accept(new DeviceEntry(type + "_wireless_" + index, wirelessPos.immutable(), face, false));
            }
        }
    }

    public static Map<String, Device> scan(ServerLevel level, Cluster cluster) {
        BlockPos anchor = cluster.cabinets().isEmpty()
                ? (cluster.blocks().isEmpty() ? null : cluster.blocks().iterator().next())
                : cluster.cabinets().iterator().next();
        return scan(level, anchor, cluster);
    }

    public static Map<String, Device> scan(ServerLevel level, @Nullable BlockPos anchorPos, Cluster cluster) {
        Map<String, Device> devices = new LinkedHashMap<>();
        scanEntries(level, cluster, entry -> {
            if (anchorPos != null) {
                devices.put(entry.key(), entry.fluid()
                        ? new FluidDevice(level, anchorPos, entry.key(), entry.pos())
                        : new ContainerDevice(level, anchorPos, entry.key(), entry.pos()));
            } else if (entry.fluid()) {
                var h = fluidHandler(level, entry);
                if (h != null) {
                    devices.put(entry.key(), new FluidDevice(h));
                }
            } else {
                var h = itemHandler(level, entry);
                if (h != null) {
                    devices.put(entry.key(), new ContainerDevice(h));
                }
            }
        });
        return devices;
    }

    private DeviceRegistry() {
    }
}
