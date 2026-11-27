package computera.blockentity;

import computera.block.DisplayPanelBlock;
import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.net.DisplayStatePayload;
import computera.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DisplayPanelBlockEntity extends BlockEntity {
    private static final int MAX_PANELS = 512;
    private static final int REFRESH_TICKS = 60;

    private int wallW = 1;
    private int wallH = 1;
    private BlockPos controllerPos;
    private DisplayMode mode = DisplayMode.OVERVIEW;
    private List<String> lines = new ArrayList<>();
    private List<String> itemEntries = new ArrayList<>();
    private List<String> deviceEntries = new ArrayList<>();
    private BlockPos linkAnchor;

    public DisplayPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISPLAY_PANEL.get(), pos, state);
        this.controllerPos = pos;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DisplayPanelBlockEntity panel) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!panel.isController() || serverLevel.getGameTime() % REFRESH_TICKS != 0) {
            return;
        }
        panel.refreshStats();
    }

    public int wallWidth() {
        return wallW;
    }

    public int wallHeight() {
        return wallH;
    }

    public List<String> lines() {
        return lines;
    }

    public BlockPos controllerPosition() {
        return controllerPos;
    }

    public boolean isController() {
        return controllerPos.equals(worldPosition);
    }

    public DisplayMode mode() {
        return mode;
    }

    public List<String> itemEntries() {
        return itemEntries;
    }

    public List<String> deviceEntries() {
        return deviceEntries;
    }

    public void setMode(DisplayMode next) {
        if (mode != next) {
            mode = next;
            setChanged();
            refreshStats();
            sync();
        }
    }

    /** Right click: behave like a CRT terminal, using the nearest terminal of the cluster (preferring CRT). */
    public void openFor(ServerPlayer player) {
        DisplayPanelBlockEntity target = controller();
        Cluster cluster = target.findCluster();
        if (cluster != null && level instanceof ServerLevel serverLevel) {
            TerminalBlockEntity best = null;
            boolean bestIsCrt = false;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos pos : cluster.terminals()) {
                if (serverLevel.hasChunkAt(pos) && serverLevel.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
                    boolean isCrt = terminal.kind() == computera.block.TerminalKind.CRT;
                    double distance = pos.distSqr(target.worldPosition);
                    if (best == null || (isCrt && !bestIsCrt) || (isCrt == bestIsCrt && distance < bestDistance)) {
                        bestDistance = distance;
                        best = terminal;
                        bestIsCrt = isCrt;
                    }
                }
            }
            if (best != null) {
                best.beginSession(player, worldPosition);
                return;
            }
        }
        target.openStatus(player);
    }

    /** Sneak + right click: the wall's own status page. */
    public void openStatus(ServerPlayer player) {
        DisplayPanelBlockEntity target = controller();
        target.recomputeAndSync();
        PacketDistributor.sendToPlayer(player, new DisplayStatePayload(target.controllerPos,
                target.wallW, target.wallH, target.mode.name(),
                target.lines, target.itemEntries, target.deviceEntries));
    }

    public DisplayPanelBlockEntity controller() {
        if (isController() || level == null || !level.hasChunkAt(controllerPos)) {
            return this;
        }
        return level.getBlockEntity(controllerPos) instanceof DisplayPanelBlockEntity controller
                ? controller : this;
    }

    public Cluster linkedCluster() {
        return controller().findCluster();
    }

    private Cluster findCluster() {
        return level instanceof ServerLevel serverLevel ? findClusterInWall(serverLevel) : null;
    }

    public void recomputeAndSync() {
        refreshStats();
        sync();
    }

    private void sync() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ wall

    public static void refreshAround(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            refreshWall(serverLevel, pos);
        }
    }

    /** Rebuilds the merge result for every panel of the wall containing {@code origin}. */
    public static void refreshWall(ServerLevel level, BlockPos origin) {
        if (!level.hasChunkAt(origin)) {
            return;
        }
        BlockState originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof DisplayPanelBlock)) {
            return;
        }
        Direction facing = originState.getValue(DisplayPanelBlock.FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.Z;

        Set<BlockPos> members = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        int minX = origin.getX(), maxX = origin.getX();
        int minY = origin.getY(), maxY = origin.getY();
        int minZ = origin.getZ(), maxZ = origin.getZ();

        while (!queue.isEmpty() && members.size() < MAX_PANELS) {
            BlockPos pos = queue.poll();
            if (!level.hasChunkAt(pos) || !members.add(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DisplayPanelBlock) || state.getValue(DisplayPanelBlock.FACING) != facing) {
                members.remove(pos);
                continue;
            }
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() == facing.getAxis()) {
                    continue;
                }
                BlockPos next = pos.relative(direction);
                if (!members.contains(next)) {
                    queue.add(next);
                }
            }
        }
        members.removeIf(pos -> !level.hasChunkAt(pos)
                || !(level.getBlockState(pos).getBlock() instanceof DisplayPanelBlock));

        int width = alongX ? maxX - minX + 1 : maxZ - minZ + 1;
        int height = maxY - minY + 1;
        boolean rectangular = members.size() == width * height
                && (alongX ? minZ == maxZ : minX == maxX);

        BlockPos controller = null;
        if (rectangular) {
            controller = origin;
            for (BlockPos pos : members) {
                if (controller == null || pos.getY() < controller.getY()
                        || (pos.getY() == controller.getY() && pos.getX() < controller.getX())
                        || (pos.getY() == controller.getY() && pos.getX() == controller.getX()
                            && pos.getZ() < controller.getZ())) {
                    controller = pos;
                }
            }
        }

        for (BlockPos pos : members) {
            if (!(level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel)) {
                continue;
            }
            panel.linkAnchor = null;
            int nextW = rectangular ? width : 1;
            int nextH = rectangular ? height : 1;
            BlockPos nextController = rectangular ? controller : pos;
            if (panel.wallW == nextW && panel.wallH == nextH && panel.controllerPos.equals(nextController)) {
                continue;
            }
            panel.wallW = nextW;
            panel.wallH = nextH;
            panel.controllerPos = nextController.immutable();
            if (!panel.controllerPos.equals(pos)) {
                panel.lines = new ArrayList<>();
            }
            panel.setChanged();
            panel.sync();
        }
    }

    // ------------------------------------------------------------------ stats

    private void refreshStats() {
        List<String> freshLines = new ArrayList<>();
        List<String> freshItems = new ArrayList<>();
        List<String> freshDevices = new ArrayList<>();
        computeStats(freshLines, freshItems, freshDevices);
        if (!freshLines.equals(lines) || !freshItems.equals(itemEntries)
                || !freshDevices.equals(deviceEntries)) {
            lines = freshLines;
            itemEntries = freshItems;
            deviceEntries = freshDevices;
            setChanged();
            sync();
        }
    }

    private void computeStats(List<String> lines, List<String> items, List<String> devices) {
        lines.add("@title");
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Cluster cluster = null;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = worldPosition.relative(direction);
            if (!serverLevel.hasChunkAt(adjacent)) {
                continue;
            }
            cluster = ClusterManager.clusterFor(serverLevel, adjacent);
            if (cluster != null) {
                break;
            }
        }
        if (cluster == null) {
            cluster = findClusterInWall(serverLevel);
        }
        if (cluster == null) {
            lines.add("@offline");
            return;
        }

        CabinetBlockEntity cabinet = cluster.primaryCabinet();
        long totalItems = 0;
        int containers = 0;
        int running = 0;
        java.util.Map<String, Long> itemCounts = new java.util.LinkedHashMap<>();
        java.util.List<String> deviceEntriesRaw = new java.util.ArrayList<>();

        for (var entry : computera.device.DeviceRegistry.entries(serverLevel, cluster).values()) {
            if (entry.fluid()) {
                continue;
            }
            var handler = computera.device.DeviceRegistry.itemHandler(serverLevel, entry);
            if (handler == null) {
                continue;
            }
            containers++;
            long containerTotal = 0;
            for (int slot = 0; slot < handler.size(); slot++) {
                long amount = handler.getAmountAsLong(slot);
                if (amount <= 0) {
                    continue;
                }
                var resource = handler.getResource(slot);
                if (resource.isEmpty()) {
                    continue;
                }
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(resource.toStack(1).getItem()).toString();
                itemCounts.merge(id, amount, Long::sum);
                containerTotal += amount;
            }
            totalItems += containerTotal;
            boolean isRunning = isMachineRunning(serverLevel, entry.pos());
            if (isRunning) {
                running++;
            }
            if (deviceEntriesRaw.size() < 64) {
                String name = cabinet == null ? entry.key() : cabinet.alias(entry.key());
                if (name == null || name.isBlank()) {
                    name = entry.key();
                }
                String status = isRunning ? "run" : (containerTotal > 0 ? "full" : "idle");
                String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(serverLevel.getBlockState(entry.pos()).getBlock()).toString();
                deviceEntriesRaw.add(entry.key() + "|" + name + "|" + containerTotal
                        + "|" + status + "|" + blockId);
            }
        }

        int kinds = itemCounts.size();
        itemCounts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(64)
                .forEach(entry -> items.add(entry.getKey() + "=" + entry.getValue()));
        devices.addAll(deviceEntriesRaw);

        long energy = 0;
        for (BlockPos pos : cluster.cabinets()) {
            if (serverLevel.hasChunkAt(pos) && serverLevel.getBlockEntity(pos) instanceof CabinetBlockEntity cab) {
                energy += cab.energy().getAmountAsLong();
            }
        }

        lines.add("@items|" + compact(totalItems) + "|" + kinds);
        lines.add("@containers|" + containers + "|" + running);
        lines.add("@cabinets|" + cluster.cabinetCount() + "|" + cluster.terminalCount());
        lines.add("@threads|" + cluster.usedThreads() + "|" + cluster.threadCapacity());
        lines.add("@energy|" + compact(energy));
    }

    /** A big wall may be linked at a far corner, so every panel is checked. */
    private Cluster findClusterInWall(ServerLevel level) {
        if (linkAnchor != null && level.hasChunkAt(linkAnchor)) {
            Cluster cached = ClusterManager.clusterFor(level, linkAnchor);
            if (cached != null) {
                return cached;
            }
            linkAnchor = null;
        }
        Direction facing = getBlockState().getValue(DisplayPanelBlock.FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.Z;
        for (int u = 0; u < wallW; u++) {
            for (int v = 0; v < wallH; v++) {
                BlockPos panel = alongX
                        ? new BlockPos(controllerPos.getX() + u, controllerPos.getY() + v, controllerPos.getZ())
                        : new BlockPos(controllerPos.getX(), controllerPos.getY() + v, controllerPos.getZ() + u);
                for (Direction direction : Direction.values()) {
                    BlockPos anchor = panel.relative(direction);
                    if (!level.hasChunkAt(anchor)) {
                        continue;
                    }
                    Cluster cluster = ClusterManager.clusterFor(level, anchor);
                    if (cluster != null) {
                        linkAnchor = anchor.immutable();
                        return cluster;
                    }
                }
            }
        }
        return null;
    }

    /** True when the block state says the machine is working (furnace lit, machine active, ...). */
    private static boolean isMachineRunning(net.minecraft.world.level.Level level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        for (var property : state.getProperties()) {
            if (!(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bool)) {
                continue;
            }
            String name = property.getName();
            if (!(name.equals("lit") || name.equals("active") || name.equals("working")
                    || name.equals("running") || name.equals("powered") || name.equals("on"))) {
                continue;
            }
            if (state.getValue(bool)) {
                return true;
            }
        }
        return false;
    }

    private static String compact(long value) {
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    // ------------------------------------------------------------------ nbt

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("WallW", wallW);
        output.putInt("WallH", wallH);
        output.putLong("Controller", controllerPos.asLong());
        output.putString("Mode", mode.name());
        var lineList = output.list("Lines", com.mojang.serialization.Codec.STRING);
        for (String line : lines) {
            lineList.add(line);
        }
        var itemList = output.list("Items", com.mojang.serialization.Codec.STRING);
        for (String line : itemEntries) {
            itemList.add(line);
        }
        var deviceList = output.list("Devices", com.mojang.serialization.Codec.STRING);
        for (String line : deviceEntries) {
            deviceList.add(line);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        wallW = Math.max(1, input.getIntOr("WallW", 1));
        wallH = Math.max(1, input.getIntOr("WallH", 1));
        long controller = input.getLongOr("Controller", worldPosition.asLong());
        controllerPos = BlockPos.of(controller);
        mode = DisplayMode.byName(input.getStringOr("Mode", "OVERVIEW"));
        lines = new ArrayList<>();
        input.listOrEmpty("Lines", com.mojang.serialization.Codec.STRING).forEach(lines::add);
        itemEntries = new ArrayList<>();
        input.listOrEmpty("Items", com.mojang.serialization.Codec.STRING).forEach(itemEntries::add);
        deviceEntries = new ArrayList<>();
        input.listOrEmpty("Devices", com.mojang.serialization.Codec.STRING).forEach(deviceEntries::add);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithFullMetadata(registries);
    }
}
