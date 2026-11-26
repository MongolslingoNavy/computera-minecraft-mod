package computera.compat;

import computera.block.TerminalKind;
import computera.blockentity.CabinetBlockEntity;
import computera.blockentity.CrateBlockEntity;
import computera.blockentity.DisplayPanelBlockEntity;
import computera.blockentity.EuAdapterBlockEntity;
import computera.blockentity.LogisticsPortBlockEntity;
import computera.blockentity.PunchMachineBlockEntity;
import computera.blockentity.TerminalBlockEntity;
import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.logistics.LogisticsMover;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Shared zero-dependency server telemetry serializer and client-localized tooltip builder
 * for Jade, WTHIT, and The One Probe.
 */
public final class ComputeraProbeHelper {

    private static final String KEY_KIND = "computeraProbeKind";

    private static final String KIND_EU_ADAPTER = "eu_adapter";
    private static final String KIND_CABINET = "cabinet";
    private static final String KIND_LOGISTICS_PORT = "logistics_port";
    private static final String KIND_CRATE = "crate";
    private static final String KIND_DISPLAY_PANEL = "display_panel";
    private static final String KIND_TERMINAL = "terminal";
    private static final String KIND_PUNCH_MACHINE = "punch_machine";

    private ComputeraProbeHelper() {
    }

    /** Serializes server-authoritative HUD telemetry for any supported Computera block entity. */
    public static void writeBlockData(CompoundTag tag, ServerLevel level, BlockEntity blockEntity) {
        if (tag == null || level == null || blockEntity == null) {
            return;
        }
        if (blockEntity instanceof EuAdapterBlockEntity adapter) {
            writeEuAdapter(tag, adapter);
        } else if (blockEntity instanceof CabinetBlockEntity cabinet) {
            writeCabinet(tag, level, cabinet);
        } else if (blockEntity instanceof LogisticsPortBlockEntity port) {
            writeLogisticsPort(tag, level, port);
        } else if (blockEntity instanceof CrateBlockEntity crate) {
            writeCrate(tag, crate);
        } else if (blockEntity instanceof DisplayPanelBlockEntity panel) {
            writeDisplayPanel(tag, panel);
        } else if (blockEntity instanceof TerminalBlockEntity terminal) {
            writeTerminal(tag, level, terminal);
        } else if (blockEntity instanceof PunchMachineBlockEntity punch) {
            writePunchMachine(tag, punch);
        }
    }

    /** Formats localized HUD lines directly on the server (used by The One Probe). */
    public static List<Component> buildDirectLines(ServerLevel level, BlockEntity blockEntity) {
        if (level == null || blockEntity == null) {
            return List.of();
        }
        CompoundTag tag = new CompoundTag();
        writeBlockData(tag, level, blockEntity);
        return readBlockLines(tag);
    }

    /** Formats localized HUD lines from synced server NBT (used by Jade, WTHIT, and TOP). */
    public static List<Component> readBlockLines(CompoundTag tag) {
        if (tag == null) {
            return List.of();
        }
        return switch (tag.getStringOr(KEY_KIND, "")) {
            case KIND_EU_ADAPTER -> readEuAdapter(tag);
            case KIND_CABINET -> readCabinet(tag);
            case KIND_LOGISTICS_PORT -> readLogisticsPort(tag);
            case KIND_CRATE -> readCrate(tag);
            case KIND_DISPLAY_PANEL -> readDisplayPanel(tag);
            case KIND_TERMINAL -> readTerminal(tag);
            case KIND_PUNCH_MACHINE -> readPunchMachine(tag);
            default -> List.of();
        };
    }

    // ---- EU Adapter ----

    private static void writeEuAdapter(CompoundTag tag, EuAdapterBlockEntity adapter) {
        tag.putString(KEY_KIND, KIND_EU_ADAPTER);
        tag.putLong("fe", adapter.energy().getAmountAsLong());
        tag.putLong("maxFe", adapter.energy().getCapacityAsLong());
        int gridState = adapter.connected() ? 2 : (IndustrialCraftEu.available() ? 1 : 0);
        tag.putInt("gridState", gridState);
    }

    private static List<Component> readEuAdapter(CompoundTag tag) {
        long fe = tag.getLongOr("fe", 0L);
        long maxFe = tag.getLongOr("maxFe", 100_000L);
        long eu = Math.round(fe / IndustrialCraftEu.FE_PER_EU);
        long maxEu = Math.round(maxFe / IndustrialCraftEu.FE_PER_EU);
        int gridState = tag.getIntOr("gridState", 0);

        Component energyLine = Component.translatable(
                "probe.computera.eu_adapter.energy", eu, maxEu, fe, maxFe
        ).withStyle(ChatFormatting.YELLOW);

        Component stateLine = switch (gridState) {
            case 2 -> Component.translatable("probe.computera.eu_adapter.grid_connected")
                    .withStyle(ChatFormatting.GREEN);
            case 1 -> Component.translatable("probe.computera.eu_adapter.grid_standby")
                    .withStyle(ChatFormatting.YELLOW);
            default -> Component.translatable("probe.computera.eu_adapter.grid_fe_only")
                    .withStyle(ChatFormatting.GRAY);
        };
        return List.of(energyLine, stateLine);
    }

    // ---- Computer Cabinet ----

    private static void writeCabinet(CompoundTag tag, ServerLevel level, CabinetBlockEntity cabinet) {
        tag.putString(KEY_KIND, KIND_CABINET);
        tag.putLong("fe", cabinet.energy().getAmountAsLong());
        tag.putLong("maxFe", cabinet.energy().getCapacityAsLong());
        tag.putInt("cards", cabinet.installedCards());
        tag.putInt("drives", cabinet.installedDrives());
        tag.putInt("files", cabinet.files().size());
        tag.putInt("wireless", cabinet.wireless().size());
        tag.putInt("rules", cabinet.rules().size());

        Cluster cluster = ClusterManager.clusterFor(level, cabinet.getBlockPos());
        int usedThreads = cluster == null ? 0 : cluster.usedThreads();
        int maxThreads = cluster == null ? cabinet.installedThreads() : cluster.threadCapacity();
        int runningCount = 0;
        List<String> activePrograms = new ArrayList<>();
        if (cluster != null) {
            for (BlockPos terminalPos : cluster.terminals()) {
                if (level.hasChunkAt(terminalPos)
                        && level.getBlockEntity(terminalPos) instanceof TerminalBlockEntity terminal
                        && !terminal.isRemoved()
                        && terminal.isRunning()) {
                    runningCount++;
                    String file = terminal.runningFile();
                    if (file != null && !file.isBlank() && activePrograms.size() < 3) {
                        activePrograms.add(file);
                    }
                }
            }
        }
        String summary = String.join(", ", activePrograms);
        if (runningCount > activePrograms.size() && !summary.isEmpty()) {
            summary += " +" + (runningCount - activePrograms.size());
        }
        tag.putInt("usedThreads", usedThreads);
        tag.putInt("maxThreads", maxThreads);
        tag.putInt("runningCount", runningCount);
        tag.putString("programs", summary);
    }

    private static List<Component> readCabinet(CompoundTag tag) {
        long fe = tag.getLongOr("fe", 0L);
        long maxFe = tag.getLongOr("maxFe", CabinetBlockEntity.ENERGY_CAPACITY);
        int cards = tag.getIntOr("cards", 0);
        int drives = tag.getIntOr("drives", 0);
        int usedThreads = tag.getIntOr("usedThreads", 0);
        int maxThreads = tag.getIntOr("maxThreads", 0);
        int runningCount = tag.getIntOr("runningCount", 0);
        String programs = tag.getStringOr("programs", "");
        int files = tag.getIntOr("files", 0);
        int wireless = tag.getIntOr("wireless", 0);
        int rules = tag.getIntOr("rules", 0);

        List<Component> lines = new ArrayList<>(4);
        lines.add(Component.translatable("probe.computera.cabinet.energy", fe, maxFe)
                .withStyle(fe > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED));

        if (runningCount > 0) {
            String label = programs.isEmpty() ? Integer.toString(runningCount) : programs;
            lines.add(Component.translatable("probe.computera.cabinet.running", label)
                    .withStyle(ChatFormatting.GREEN));
        } else if (maxThreads > 0) {
            lines.add(Component.translatable("probe.computera.cabinet.idle")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("probe.computera.cabinet.no_cards")
                    .withStyle(ChatFormatting.GOLD));
        }

        lines.add(Component.translatable(
                "probe.computera.cabinet.compute",
                usedThreads,
                maxThreads,
                cards,
                CabinetBlockEntity.CARD_SLOTS,
                drives,
                CabinetBlockEntity.DRIVE_SLOTS
        ).withStyle(ChatFormatting.GRAY));

        if (files > 0 || wireless > 0 || rules > 0) {
            lines.add(Component.translatable("probe.computera.cabinet.storage", files, wireless, rules)
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    // ---- Logistics Port ----

    private static void writeLogisticsPort(CompoundTag tag, ServerLevel level, LogisticsPortBlockEntity port) {
        tag.putString(KEY_KIND, KIND_LOGISTICS_PORT);
        Cluster cluster = ClusterManager.clusterFor(level, port.getBlockPos());
        boolean linked = cluster != null;
        int pipes = linked ? cluster.pipes().size() : 0;
        int throughput = pipes * LogisticsMover.ITEMS_PER_PIPE_PER_TICK;
        String filter = port.filter() == null ? "" : port.filter();

        int containers = 0;
        long queuedItems = 0L;
        Set<BlockPos> seen = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos adj = port.getBlockPos().relative(direction);
            if (!level.hasChunkAt(adj)) {
                continue;
            }
            BlockPos canonical = level.getBlockEntity(adj) instanceof CrateBlockEntity crate
                    ? crate.groupRoot()
                    : adj;
            if (!seen.add(canonical)) {
                continue;
            }
            ResourceHandler<ItemResource> handler =
                    level.getCapability(Capabilities.Item.BLOCK, adj, direction.getOpposite());
            if (handler == null) {
                continue;
            }
            containers++;
            for (int slot = 0; slot < handler.size(); slot++) {
                queuedItems += handler.getAmountAsLong(slot);
            }
        }

        tag.putBoolean("linked", linked);
        tag.putInt("pipes", pipes);
        tag.putInt("throughput", throughput);
        tag.putString("filter", filter);
        tag.putInt("containers", containers);
        tag.putLong("queued", queuedItems);
    }

    private static List<Component> readLogisticsPort(CompoundTag tag) {
        boolean linked = tag.getBooleanOr("linked", false);
        int pipes = tag.getIntOr("pipes", 0);
        int throughput = tag.getIntOr("throughput", 0);
        String filter = tag.getStringOr("filter", "");
        int containers = tag.getIntOr("containers", 0);
        long queued = tag.getLongOr("queued", 0L);

        List<Component> lines = new ArrayList<>(3);
        if (linked) {
            lines.add(Component.translatable("probe.computera.logistics_port.linked", pipes, throughput)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("probe.computera.logistics_port.unlinked")
                    .withStyle(ChatFormatting.RED));
        }

        if (!filter.isEmpty()) {
            lines.add(Component.translatable("probe.computera.logistics_port.role_dest", filter)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("probe.computera.logistics_port.role_source")
                    .withStyle(ChatFormatting.GOLD));
        }

        if (containers > 0) {
            lines.add(Component.translatable("probe.computera.logistics_port.queue", containers, queued)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("probe.computera.logistics_port.no_container")
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    // ---- Modular Crate ----

    private static void writeCrate(CompoundTag tag, CrateBlockEntity crate) {
        tag.putString(KEY_KIND, KIND_CRATE);
        List<CrateBlockEntity> members = crate.groupMembers();
        int totalSlots = members.size() * CrateBlockEntity.SLOTS;
        int usedSlots = 0;
        long totalItems = 0L;
        for (CrateBlockEntity member : members) {
            for (int slot = 0; slot < CrateBlockEntity.SLOTS; slot++) {
                long amount = member.handler().getAmountAsLong(slot);
                if (amount > 0) {
                    usedSlots++;
                    totalItems += amount;
                }
            }
        }
        tag.putInt("crates", members.size());
        tag.putInt("usedSlots", usedSlots);
        tag.putInt("totalSlots", totalSlots);
        tag.putLong("totalItems", totalItems);
    }

    private static List<Component> readCrate(CompoundTag tag) {
        int crates = tag.getIntOr("crates", 1);
        int usedSlots = tag.getIntOr("usedSlots", 0);
        int totalSlots = tag.getIntOr("totalSlots", CrateBlockEntity.SLOTS);
        long totalItems = tag.getLongOr("totalItems", 0L);

        Component groupLine = Component.translatable(
                "probe.computera.crate.group", crates, CrateBlockEntity.MAX_CRATES
        ).withStyle(crates > 1 ? ChatFormatting.AQUA : ChatFormatting.GRAY);

        ChatFormatting capColor = (totalSlots > 0 && usedSlots >= totalSlots)
                ? ChatFormatting.RED
                : (usedSlots > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        Component capacityLine = Component.translatable(
                "probe.computera.crate.capacity", usedSlots, totalSlots, totalItems
        ).withStyle(capColor);

        return List.of(groupLine, capacityLine);
    }

    // ---- Network Display Panel ----

    private static void writeDisplayPanel(CompoundTag tag, DisplayPanelBlockEntity panel) {
        tag.putString(KEY_KIND, KIND_DISPLAY_PANEL);
        DisplayPanelBlockEntity controller = panel.controller();
        Cluster cluster = controller.linkedCluster();
        tag.putInt("wallW", controller.wallWidth());
        tag.putInt("wallH", controller.wallHeight());
        tag.putString("mode", controller.mode().name().toLowerCase(Locale.ROOT));
        tag.putBoolean("linked", cluster != null);
        tag.putInt("cabinets", cluster == null ? 0 : cluster.cabinetCount());
        tag.putInt("terminals", cluster == null ? 0 : cluster.terminalCount());
    }

    private static List<Component> readDisplayPanel(CompoundTag tag) {
        int wallW = tag.getIntOr("wallW", 1);
        int wallH = tag.getIntOr("wallH", 1);
        String mode = tag.getStringOr("mode", "overview");
        boolean linked = tag.getBooleanOr("linked", false);
        int cabinets = tag.getIntOr("cabinets", 0);
        int terminals = tag.getIntOr("terminals", 0);

        Component modeName = Component.translatable("computera.gui.display.mode." + mode);
        Component wallLine = Component.translatable(
                "probe.computera.display_panel.wall", wallW, wallH, modeName
        ).withStyle(ChatFormatting.AQUA);

        Component linkLine = linked
                ? Component.translatable("probe.computera.display_panel.linked", cabinets, terminals)
                        .withStyle(ChatFormatting.GREEN)
                : Component.translatable("probe.computera.display_panel.unlinked")
                        .withStyle(ChatFormatting.RED);

        return List.of(wallLine, linkLine);
    }

    // ---- CRT & Teletype Terminals ----

    private static void writeTerminal(CompoundTag tag, ServerLevel level, TerminalBlockEntity terminal) {
        tag.putString(KEY_KIND, KIND_TERMINAL);
        boolean isCrt = terminal.kind() == TerminalKind.CRT;
        Cluster cluster = ClusterManager.clusterFor(level, terminal.getBlockPos());
        CabinetBlockEntity primary = cluster == null ? null : cluster.primaryCabinet();
        boolean hasPower = primary != null && primary.energy().getAmountAsInt() >= 3;

        tag.putBoolean("crt", isCrt);
        tag.putBoolean("running", terminal.isRunning());
        tag.putString("program", terminal.runningFile() == null ? "" : terminal.runningFile());
        tag.putInt("tape", terminal.tapeLines());
        tag.putBoolean("hasPower", hasPower);
        tag.putBoolean("linked", cluster != null);
        tag.putInt("usedThreads", cluster == null ? 0 : cluster.usedThreads());
        tag.putInt("maxThreads", cluster == null ? 0 : cluster.threadCapacity());
    }

    private static List<Component> readTerminal(CompoundTag tag) {
        boolean isCrt = tag.getBooleanOr("crt", true);
        boolean running = tag.getBooleanOr("running", false);
        String program = tag.getStringOr("program", "");
        int tape = tag.getIntOr("tape", 0);
        boolean hasPower = tag.getBooleanOr("hasPower", false);
        boolean linked = tag.getBooleanOr("linked", false);
        int usedThreads = tag.getIntOr("usedThreads", 0);
        int maxThreads = tag.getIntOr("maxThreads", 0);

        List<Component> lines = new ArrayList<>(3);
        if (running) {
            lines.add(Component.translatable("probe.computera.terminal.running", program)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("probe.computera.terminal.idle")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (isCrt) {
            lines.add(Component.translatable(
                    hasPower ? "probe.computera.terminal.power_ok" : "probe.computera.terminal.power_low"
            ).withStyle(hasPower ? ChatFormatting.GREEN : ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("probe.computera.terminal.tape", tape)
                    .withStyle(tape > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        }

        if (linked) {
            lines.add(Component.translatable("probe.computera.terminal.linked", usedThreads, maxThreads)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("probe.computera.terminal.unlinked")
                    .withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    // ---- Card Punch ----

    private static void writePunchMachine(CompoundTag tag, PunchMachineBlockEntity punch) {
        tag.putString(KEY_KIND, KIND_PUNCH_MACHINE);
        TerminalBlockEntity terminal = punch.adjacentTerminal();
        tag.putBoolean("hasTerminal", terminal != null);
        String openFile = terminal != null && terminal.editingFile() != null ? terminal.editingFile() : "";
        tag.putString("openFile", openFile);
    }

    private static List<Component> readPunchMachine(CompoundTag tag) {
        boolean hasTerminal = tag.getBooleanOr("hasTerminal", false);
        String openFile = tag.getStringOr("openFile", "");
        if (hasTerminal && !openFile.isEmpty()) {
            return List.of(Component.translatable("probe.computera.punch_machine.ready", openFile)
                    .withStyle(ChatFormatting.GREEN));
        }
        if (hasTerminal) {
            return List.of(Component.translatable("probe.computera.punch_machine.no_file")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return List.of(Component.translatable("probe.computera.punch_machine.no_terminal")
                .withStyle(ChatFormatting.GRAY));
    }
}
