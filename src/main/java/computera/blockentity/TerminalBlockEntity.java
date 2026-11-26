package computera.blockentity;

import computera.block.TerminalBlock;
import computera.block.TerminalKind;
import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.device.DeviceRegistry;
import computera.item.ComputeCardItem;
import computera.item.ProgramItems;
import computera.net.TerminalFilePayload;
import computera.net.TerminalLinesPayload;
import computera.net.TerminalStatePayload;
import computera.registry.ModBlockEntities;
import computera.script.Device;
import computera.script.ScriptCompileException;
import computera.script.ScriptCompiler;
import computera.script.ScriptHost;
import computera.script.ScriptProgram;
import computera.script.ScriptVM;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TerminalBlockEntity extends BlockEntity {
    public static final int MAX_OUTPUT_LINES = 200;
    public static final int MAX_OUTPUT_LINE_LEN = 512;
    public static final int MAX_PENDING_PER_TICK = 64;
    public static final int TICK_BUDGET = 200_000;

    public static final int LINES_PER_TAPE = 8192;

    private record LogisticsSelection(String key, int page) {
    }

    private final List<String> output = new ArrayList<>();
    private final List<String> pendingOutput = new ArrayList<>();
    private int tapeLines;

    private final Map<UUID, BlockPos> sessionOrigins = new java.util.HashMap<>();
    private final Map<UUID, String> editingFiles = new java.util.HashMap<>();
    private final Map<UUID, LogisticsSelection> logisticsSelections = new java.util.HashMap<>();

    @Nullable
    private ScriptVM vm;
    @Nullable
    private String runningFile;
    @Nullable
    private String lastEditingFile;
    private final java.util.Set<UUID> viewers = new java.util.LinkedHashSet<>();

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL.get(), pos, state);
    }

    public TerminalKind kind() {
        return getBlockState().getBlock() instanceof TerminalBlock terminal
                ? terminal.kind()
                : TerminalKind.TELETYPE;
    }

    public List<String> output() {
        return output;
    }

    public boolean isRunning() {
        return vm != null;
    }

    public int tapeLines() {
        return tapeLines;
    }

    public void addTape(int lines) {
        tapeLines += lines;
        setChanged();
        print("[paper tape loaded, " + tapeLines + " lines left]");
    }

    @Nullable
    public String editingFile() {
        return lastEditingFile;
    }

    @Nullable
    public String editingFile(Player player) {
        if (player != null) {
            String owned = editingFiles.get(player.getUUID());
            if (owned != null && !owned.isEmpty()) {
                return owned;
            }
        }
        return lastEditingFile;
    }

    @Nullable
    public String fileSource(String name) {
        ResolvedFile file = resolveFile(name);
        return file == null ? null : file.source();
    }

    public boolean saveFileFromCard(String name, String source) {
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        return cabinet != null && cabinet.putFile(name, source);
    }

    private static String clampLine(String line) {
        if (line == null) {
            return "";
        }
        return line.length() > MAX_OUTPUT_LINE_LEN ? line.substring(0, MAX_OUTPUT_LINE_LEN) + "..." : line;
    }

    public void print(String line) {
        if (kind().usesPaperTape()) {
            if (tapeLines <= 0) {
                if (vm != null) {
                    vm.halt("out of paper tape");
                }
                return;
            }
            tapeLines--;
            setChanged();
        }
        output.add(clampLine(line));
        while (output.size() > MAX_OUTPUT_LINES) {
            output.removeFirst();
        }
        setChanged();
    }

    public void beginSession(Player player) {
        beginSession(player, worldPosition);
    }

    public void beginSession(Player player, BlockPos origin) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level() != level
                || !level.hasChunkAt(origin)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(origin)) > 64.0) {
            return;
        }
        Cluster cluster = cluster();
        int capacity = cluster == null ? 0 : cluster.threadCapacity();
        if (capacity <= 0) {
            serverPlayer.sendSystemMessage(Component.literal("[Computera] no compute card installed"));
            return;
        }
        if (!isValidOrigin(origin, cluster)) {
            return;
        }
        if (!cluster.canAdmitTerminal()) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "[Computera] thread limit reached (" + cluster.terminalCount() + "/" + capacity + ")"));
            return;
        }
        if (kind().usesPaperTape() && tapeLines <= 0) {
            serverPlayer.sendSystemMessage(Component.literal("[Computera] insert a paper tape first"));
            return;
        }
        if (kind().usesPower() && !hasPower(cluster)) {
            serverPlayer.sendSystemMessage(Component.literal("[Computera] no power"));
            return;
        }
        viewers.add(serverPlayer.getUUID());
        sessionOrigins.put(serverPlayer.getUUID(), origin.immutable());
        sendState(serverPlayer);
    }

    private boolean isValidOrigin(BlockPos origin, @Nullable Cluster cluster) {
        if (origin.equals(worldPosition)) {
            return true;
        }
        if (cluster == null || level == null || !level.hasChunkAt(origin)) {
            return false;
        }
        if (level.getBlockEntity(origin) instanceof DisplayPanelBlockEntity panel) {
            Cluster panelCluster = panel.linkedCluster();
            return panelCluster != null && panelCluster.blocks().contains(worldPosition);
        }
        return false;
    }

    public void sendState(ServerPlayer player) {
        Cluster cluster = cluster();
        int threads = cluster == null ? 0 : cluster.threadCapacity();
        int used = cluster == null ? 0 : cluster.usedThreads();
        List<String> files = new ArrayList<>();
        CabinetBlockEntity cabinet = primaryCabinet(cluster);
        if (cabinet != null) {
            files.addAll(cabinet.files().keySet());
            for (int drive = 0; drive < CabinetBlockEntity.DRIVE_SLOTS; drive++) {
                for (String name : ProgramItems.files(cabinet.drive(drive)).keySet()) {
                    files.add("hd" + (drive + 1) + ":" + name);
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new TerminalStatePayload(
                worldPosition, kind().name(), threads, used, vm != null,
                runningFile == null ? "" : runningFile, List.copyOf(output), files));
    }

    public boolean canAccess(ServerPlayer player) {
        BlockPos origin = sessionOrigins.get(player.getUUID());
        if (isRemoved() || !player.isAlive() || player.level() != level || origin == null
                || !level.hasChunkAt(origin)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(origin)) > 64.0) {
            return false;
        }
        Cluster cluster = cluster();
        return cluster != null && cluster.threadCapacity() > 0 && isValidOrigin(origin, cluster);
    }

    public void handleAction(ServerPlayer player, String action, String name, String data) {
        if ("close".equals(action)) {
            releaseViewer(player);
            return;
        }
        if (!canAccess(player)) {
            releaseViewer(player);
            return;
        }
        Cluster cluster = cluster();
        if (kind().usesPower() && !hasPower(cluster)) {
            respond(player, "[Computera] no power");
            return;
        }
        if (kind().usesPaperTape() && tapeLines <= 0) {
            respond(player, "[Computera] out of paper tape");
            return;
        }
        switch (action) {
            case "request" -> sendState(player);
            case "command" -> handleCommand(player, name);
            case "save" -> saveFile(player, name, data);
            case "open" -> openFile(player, name);
            case "logi" -> sendLogisticsState(player);
            case "logi_select" -> logisticsSelect(player, name, data);
            case "logi_take" -> logisticsTake(player, name, data);
            case "logi_put" -> logisticsPut(player, name, data);
            case "logi_move" -> logisticsMove(player, name, data);
            case "logi_store" -> logisticsStore(player, name, data);
            case "logi_open" -> openDeviceMenu(player, name);
            case "logi_pickup" -> logisticsPickup(player, name, data);
            case "logi_drop" -> logisticsDrop(player, name, data);
            case "logi_rename" -> logisticsRename(player, name, data);
            case "logi_group" -> logisticsGroup(player, name, data);
            case "logi_distribute" -> logisticsDistribute(player, name, data);
            case "logi_give" -> logisticsGive(player, name, data);
            case "logi_close" -> returnCarried(player);
            case "auto" -> sendAutomationState(player);
            case "auto_add" -> automationAdd(player, name, data);
            case "auto_del" -> automationDelete(player, name);
            case "auto_toggle" -> automationToggle(player, name);
            case "display_list" -> sendDisplayList(player);
            case "display_mode" -> setDisplayMode(player, name, data);
            default -> respond(player, "[Computera] unknown action: " + action);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && !viewers.isEmpty()) {
            var playerList = serverLevel.getServer().getPlayerList();
            for (UUID id : List.copyOf(viewers)) {
                ServerPlayer player = playerList.getPlayer(id);
                if (player != null) {
                    returnCarried(player);
                }
            }
        }
        viewers.clear();
        sessionOrigins.clear();
        logisticsSelections.clear();
        editingFiles.clear();
        super.setRemoved();
    }

    public void handleCommand(ServerPlayer player, String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "help" -> respond(player,
                    "help   - show commands",
                    "ls     - list files",
                    "cat    - show a file",
                    "edit   - open editor",
                    "run    - run a program",
                    "stop   - stop the running program",
                    "rm     - delete a file",
                    "export - copy file to held drive",
                    "import - copy file from held drive",
                    "drive  - list held drive files",
                    "count  - total amount of an item",
                    "find   - where an item is stored",
                    "store  - move item from inventory to ports",
                    "take   - withdraw item from ports to inventory",
                    "ports  - list logistics ports",
                    "devices - list controllable devices",
                    "clear  - clear screen");
            case "ls" -> {
                CabinetBlockEntity cabinet = primaryCabinet(cluster());
                boolean any = false;
                if (cabinet != null) {
                    for (Map.Entry<String, String> entry : cabinet.files().entrySet()) {
                        respond(player, entry.getKey() + "  " + entry.getValue().length() + " bytes");
                        any = true;
                    }
                    for (int drive = 0; drive < CabinetBlockEntity.DRIVE_SLOTS; drive++) {
                        ItemStack stack = cabinet.drive(drive);
                        if (stack.isEmpty()) {
                            continue;
                        }
                        for (Map.Entry<String, String> entry : ProgramItems.files(stack).entrySet()) {
                            respond(player, "hd" + (drive + 1) + ":" + entry.getKey() + "  "
                                    + entry.getValue().length() + " bytes");
                            any = true;
                        }
                    }
                }
                if (!any) {
                    respond(player, "(no files)");
                }
            }
            case "cat" -> {
                ResolvedFile resolved = resolveFile(argument);
                String source = resolved == null ? null : resolved.source();
                if (source == null) {
                    respond(player, "no such file: " + argument);
                } else {
                    for (String fileLine : source.split("\n", -1)) {
                        respond(player, fileLine);
                    }
                }
            }
            case "edit" -> {
                if (argument.isEmpty()) {
                    respond(player, "usage: edit <file>");
                    return;
                }
                editingFiles.put(player.getUUID(), argument);
                lastEditingFile = argument;
                ResolvedFile resolved = resolveFile(argument);
                String source = resolved == null ? null : resolved.source();
                PacketDistributor.sendToPlayer(player, new TerminalFilePayload(
                        worldPosition, argument, source == null ? "" : source));
            }
            case "run" -> runFile(player, argument);
            case "export" -> exportFile(player, argument);
            case "import" -> importFile(player, argument);
            case "drive" -> listDrive(player);
            case "stop" -> {
                if (vm == null) {
                    respond(player, "nothing is running");
                } else {
                    vm.halt("stopped by user");
                    flushPending();
                    vm = null;
                    runningFile = null;
                    respond(player, "stopped");
                }
            }
            case "rm" -> {
                CabinetBlockEntity cabinet = primaryCabinet(cluster());
                ResolvedFile resolved = resolveFile(argument);
                if (cabinet == null || resolved == null || resolved.source() == null) {
                    respond(player, "no such file: " + argument);
                } else if (resolved.drive() >= 0) {
                    ItemStack stack = cabinet.drive(resolved.drive());
                    Map<String, String> files = new java.util.LinkedHashMap<>(ProgramItems.files(stack));
                    files.remove(resolved.name());
                    stack.set(computera.registry.ModDataComponents.FILES.get(), Map.copyOf(files));
                    cabinet.setChanged();
                    respond(player, "removed " + argument);
                } else {
                    cabinet.files().remove(resolved.name());
                    cabinet.setChanged();
                    respond(player, "removed " + argument);
                }
            }
            case "clear" -> {
                output.clear();
                setChanged();
                sendState(player);
            }
            case "count" -> countItem(player, argument);
            case "find" -> findItem(player, argument);
            case "store" -> storeItem(player, argument);
            case "take" -> takeItem(player, argument);
            case "ports" -> listPorts(player);
            case "devices" -> listDevices(player);
            default -> respond(player, "unknown command: " + command);
        }
    }

    private void runFile(ServerPlayer player, String name) {
        if (name.isEmpty()) {
            respond(player, "usage: run <file>");
            return;
        }
        if (vm != null) {
            respond(player, "already running " + runningFile + " (use stop)");
            return;
        }
        Cluster cluster = cluster();
        if (cluster == null) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        if (cluster.usedThreads() >= cluster.threadCapacity()) {
            respond(player, "no free thread");
            return;
        }
        ResolvedFile resolved = resolveFile(name);
        String source = resolved == null ? null : resolved.source();
        if (source == null) {
            respond(player, "no such file: " + name);
            return;
        }

        ScriptProgram program;
        try {
            program = ScriptCompiler.compile(source, name);
        } catch (ScriptCompileException e) {
            respond(player, e.getMessage());
            return;
        }

        Map<String, Device> devices = level instanceof ServerLevel serverLevel
                ? new java.util.HashMap<>(DeviceRegistry.scan(serverLevel, worldPosition, cluster))
                : new java.util.HashMap<String, Device>();
        if (level instanceof ServerLevel serverLevel && cluster != null) {
            devices.put("logistics", new computera.logistics.LogisticsDevice(serverLevel, worldPosition));
        }
        vm = new ScriptVM(program, new TerminalHost(devices));
        runningFile = name;
        respond(player, "running " + name);
    }

    public void serverTick() {
        java.util.List<ServerPlayer> audience = onlineViewers();
        if (kind() != TerminalKind.CRT) {
            return;
        }
        boolean active = vm != null || !audience.isEmpty();
        if (!active) {
            return;
        }
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        if (cabinet == null || !drain(cabinet, 3)) {
            if (vm != null) {
                vm.halt("no power");
                flushPending();
                vm = null;
                runningFile = null;
            }
            if (!audience.isEmpty() && (output.isEmpty() || !output.getLast().equals("[no power]"))) {
                respond(audience.getFirst(), "[no power]");
            }
        }
    }

    private boolean hasPower(Cluster cluster) {
        CabinetBlockEntity cabinet = primaryCabinet(cluster);
        return cabinet != null && cabinet.energy().getAmountAsInt() >= 3;
    }

    private boolean drain(CabinetBlockEntity cabinet, int fe) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = cabinet.energy().extract(fe, transaction);
            if (taken == fe) {
                transaction.commit();
                cabinet.setChanged();
                return true;
            }
        }
        return false;
    }

    public void tickScript(int instructionBudget) {
        if (vm == null) {
            return;
        }
        boolean alive = vm.step(instructionBudget);
        flushPending();
        if (!alive) {
            String finished = runningFile;
            vm = null;
            runningFile = null;
            print("[program " + finished + " finished]");
            flushPending();
        }
    }

    private void flushPending() {
        if (pendingOutput.isEmpty()) {
            return;
        }
        List<String> lines = List.copyOf(pendingOutput);
        pendingOutput.clear();
        for (String line : lines) {
            print(line);
        }
        for (ServerPlayer viewer : onlineViewers()) {
            PacketDistributor.sendToPlayer(viewer, new TerminalLinesPayload(worldPosition, lines));
        }
    }

    /** Every player that currently has this terminal open and is still online. */
    private java.util.List<ServerPlayer> onlineViewers() {
        if (viewers.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
        }
        var list = new java.util.ArrayList<ServerPlayer>();
        var playerList = serverLevel.getServer().getPlayerList();
        java.util.Iterator<UUID> iterator = viewers.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            ServerPlayer player = playerList.getPlayer(id);
            if (player == null || !canAccess(player)) {
                if (player != null) {
                    returnCarried(player);
                }
                sessionOrigins.remove(id);
                logisticsSelections.remove(id);
                iterator.remove();
            } else {
                list.add(player);
            }
        }
        return list;
    }

    private void saveFile(ServerPlayer player, String name, String source) {
        if (name == null || name.isBlank()) {
            respond(player, "file name required");
            return;
        }
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        if (cabinet == null) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        String text = source == null ? "" : source;
        if (name.length() > CabinetBlockEntity.MAX_FILE_NAME || text.length() > CabinetBlockEntity.MAX_FILE_SIZE) {
            respond(player, "file name or content too large (max " + CabinetBlockEntity.MAX_FILE_SIZE + " bytes)");
            return;
        }
        editingFiles.put(player.getUUID(), name);
        lastEditingFile = name;
        ResolvedFile resolved = resolveFile(name);
        if (resolved == null && (name.startsWith("hd1:") || name.startsWith("hd2:"))) {
            respond(player, "drive is missing");
            return;
        }
        if (resolved != null && resolved.drive() >= 0) {
            ItemStack stack = cabinet.drive(resolved.drive());
            if (stack.isEmpty() || !ProgramItems.store(stack, resolved.name(), text)) {
                respond(player, "drive is missing or full");
                return;
            }
            cabinet.setDrive(resolved.drive(), stack);
        } else {
            if (!cabinet.putFile(name, text)) {
                respond(player, "cabinet storage full (max " + CabinetBlockEntity.MAX_FILES + " files)");
                return;
            }
        }
        respond(player, "saved " + name + " (" + text.length() + " bytes)");
    }

    private void openFile(ServerPlayer player, String name) {
        if (name != null && !name.isBlank()) {
            editingFiles.put(player.getUUID(), name);
            lastEditingFile = name;
        }
        ResolvedFile resolved = resolveFile(name);
        String source = resolved == null ? "" : (resolved.source() == null ? "" : resolved.source());
        PacketDistributor.sendToPlayer(player, new TerminalFilePayload(worldPosition, name, source));
    }

    private record ResolvedFile(int drive, String name, @Nullable String source) {
    }

    @Nullable
    private ResolvedFile resolveFile(String token) {
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        if (cabinet == null) {
            return null;
        }
        int drive = -1;
        String name = token;
        if (token.startsWith("hd1:")) {
            drive = 0;
            name = token.substring(4);
        } else if (token.startsWith("hd2:")) {
            drive = 1;
            name = token.substring(4);
        }
        if (drive >= 0) {
            ItemStack stack = cabinet.drive(drive);
            if (stack.isEmpty()) {
                return null;
            }
            return new ResolvedFile(drive, name, ProgramItems.files(stack).get(name));
        }
        return new ResolvedFile(-1, token, cabinet.files().get(token));
    }

    private void countItem(ServerPlayer player, String itemId) {
        if (itemId.isEmpty()) {
            respond(player, "usage: count <item id>");
            return;
        }
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        respond(player, itemId + " x " + computera.logistics.LogisticsIndex.count(serverLevel, cluster, itemId));
    }

    private void findItem(ServerPlayer player, String itemId) {
        if (itemId.isEmpty()) {
            respond(player, "usage: find <item id>");
            return;
        }
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        var entries = computera.logistics.LogisticsIndex.build(serverLevel, cluster).getOrDefault(itemId, java.util.List.of());
        if (entries.isEmpty()) {
            respond(player, "not found: " + itemId);
            return;
        }
        for (var entry : entries) {
            respond(player, entry.container() + " slot " + entry.slot() + " x " + entry.count());
        }
    }

    private void storeItem(ServerPlayer player, String argument) {
        if (argument.isEmpty()) {
            respond(player, "usage: store <item id> [count]");
            return;
        }
        String[] parts = argument.split("\\s+", 2);
        String itemId = parts[0];
        int amount = parts.length > 1 ? parseCount(parts[1], Integer.MAX_VALUE) : Integer.MAX_VALUE;
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        int moved = computera.logistics.LogisticsIndex.store(serverLevel, cluster, player, itemId, amount);
        if (moved < 0) {
            respond(player, "unknown item: " + itemId);
        } else {
            respond(player, "stored " + moved + " x " + itemId);
        }
    }

    private void takeItem(ServerPlayer player, String argument) {
        if (argument.isEmpty()) {
            respond(player, "usage: take <item id> [count]");
            return;
        }
        String[] parts = argument.split("\\s+", 2);
        String itemId = parts[0];
        int amount = parts.length > 1 ? parseCount(parts[1], 64) : 64;
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        int moved = computera.logistics.LogisticsIndex.withdraw(serverLevel, cluster, player, itemId, amount);
        respond(player, "withdrew " + moved + " x " + itemId);
    }

    private static int parseIndex(String text, int size) {
        try {
            int index = Integer.parseInt(text.trim()) - 1;
            return index >= 0 && index < size ? index : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void listDevices(ServerPlayer player) {
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        var lines = computera.device.DeviceRegistry.describe(serverLevel, cluster);
        if (lines.isEmpty()) {
            respond(player, "(no devices found)");
            return;
        }
        for (String line : lines) {
            respond(player, line);
        }
    }

    private void listPorts(ServerPlayer player) {
        Cluster cluster = cluster();
        if (cluster == null) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        if (cluster.ports().isEmpty()) {
            respond(player, "(no logistics ports)");
            return;
        }
        for (var pos : cluster.ports()) {
            String filter = level != null
                    && level.getBlockEntity(pos) instanceof computera.blockentity.LogisticsPortBlockEntity port
                    && port.filter() != null ? port.filter() : "(none)";
            respond(player, pos.toShortString() + " -> " + filter);
        }
    }

    private static int parseCount(String text, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void exportFile(ServerPlayer player, String name) {
        if (name.isEmpty()) {
            respond(player, "usage: export <file>");
            return;
        }
        var drive = player.getMainHandItem();
        if (ProgramItems.limitFor(drive) <= 0) {
            respond(player, "hold a hard drive or flash module in your main hand");
            return;
        }
        String source = fileSource(name);
        if (source == null) {
            respond(player, "no such file: " + name);
            return;
        }
        if (!ProgramItems.store(drive, name, source)) {
            respond(player, "drive is full");
            return;
        }
        player.getInventory().setChanged();
        respond(player, "exported " + name + " (" + source.length() + " bytes)");
    }

    private void importFile(ServerPlayer player, String name) {
        if (name.isEmpty()) {
            respond(player, "usage: import <file>");
            return;
        }
        var drive = player.getMainHandItem();
        if (ProgramItems.limitFor(drive) <= 0) {
            respond(player, "hold a hard drive or flash module in your main hand");
            return;
        }
        String source = ProgramItems.read(drive, name);
        if (source == null) {
            respond(player, "no such file on drive: " + name);
            return;
        }
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        if (cabinet == null) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        if (!cabinet.putFile(name, source)) {
            respond(player, "cabinet storage full or file too large");
            return;
        }
        respond(player, "imported " + name + " (" + source.length() + " bytes)");
    }

    private void listDrive(ServerPlayer player) {
        var drive = player.getMainHandItem();
        if (ProgramItems.limitFor(drive) <= 0) {
            respond(player, "hold a hard drive or flash module in your main hand");
            return;
        }
        var files = ProgramItems.files(drive);
        if (files.isEmpty()) {
            respond(player, "(drive is empty)");
            return;
        }
        for (var entry : files.entrySet()) {
            respond(player, entry.getKey() + "  " + entry.getValue().length() + " bytes");
        }
    }

    private void logisticsSelect(ServerPlayer player, String deviceKey, String pageText) {
        int page = 0;
        Integer parsed = parseInt(pageText);
        if (parsed != null && parsed >= 0) {
            page = parsed;
        }
        logisticsSelections.put(player.getUUID(), new LogisticsSelection(deviceKey == null ? "" : deviceKey, page));
        sendLogisticsState(player);
    }

    public void sendLogisticsState(ServerPlayer player) {
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        var entries = DeviceRegistry.entries(serverLevel, cluster);
        CabinetBlockEntity cabinet = primaryCabinet(cluster);
        var deviceMeta = new java.util.ArrayList<String>();
        var activeHandlers = new java.util.LinkedHashMap<String,
                net.neoforged.neoforge.transfer.ResourceHandler<ItemResource>>();
        for (var entry : entries.values()) {
            if (entry.fluid()) {
                continue;
            }
            var handler = DeviceRegistry.itemHandler(serverLevel, entry);
            if (handler == null) {
                continue;
            }
            activeHandlers.put(entry.key(), handler);
            deviceMeta.add(entry.key() + '|'
                    + (cabinet == null ? entry.key() : cabinet.alias(entry.key())) + '|'
                    + (cabinet == null ? "" : cabinet.group(entry.key())) + '|'
                    + entry.pos().toShortString() + '|'
                    + handler.size());
        }
        LogisticsSelection selection = logisticsSelections.get(player.getUUID());
        String selectedKey = selection != null && activeHandlers.containsKey(selection.key())
                ? selection.key()
                : (activeHandlers.isEmpty() ? "" : activeHandlers.keySet().iterator().next());
        int page = selection != null && selectedKey.equals(selection.key()) ? Math.max(0, selection.page()) : 0;
        var pageSlots = new java.util.ArrayList<String>();
        if (!selectedKey.isEmpty()) {
            var handler = activeHandlers.get(selectedKey);
            int totalSlots = handler.size();
            int pageSize = 27;
            int maxPage = Math.max(0, (totalSlots - 1) / pageSize);
            page = Math.min(page, maxPage);
            int start = page * pageSize;
            int end = Math.min(totalSlots, start + pageSize);
            for (int slot = start; slot < end; slot++) {
                var resource = handler.getResource(slot);
                if (resource.isEmpty()) {
                    pageSlots.add("-");
                } else {
                    var stack = resource.toStack(1);
                    pageSlots.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                            + "=" + handler.getAmountAsLong(slot));
                }
            }
            logisticsSelections.put(player.getUUID(), new LogisticsSelection(selectedKey, page));
        }
        ItemStack carried = player.inventoryMenu.getCarried();
        String carriedText = carried.isEmpty() ? ""
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem()) + "=" + carried.getCount();
        PacketDistributor.sendToPlayer(player,
                new computera.net.LogisticsStatePayload(
                        worldPosition, deviceMeta, selectedKey, page, pageSlots, carriedText));
    }

    private void logisticsTake(ServerPlayer player, String device, String slotText) {
        var handler = logisticsHandler(device);
        Integer slot = parseInt(slotText);
        if (handler == null || slot == null || slot < 0 || slot >= handler.size()) {
            respond(player, "invalid slot");
            return;
        }
        var resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            respond(player, "slot is empty");
            return;
        }
        int maxStack = Math.max(1, resource.toStack(1).getMaxStackSize());
        int amount = (int) Math.min(handler.getAmountAsLong(slot), maxStack);
        int taken;
        try (Transaction transaction = Transaction.openRoot()) {
            taken = handler.extract(slot, resource, amount, transaction);
            transaction.commit();
        }
        if (taken > 0) {
            ItemStack stack = resource.toStack(taken);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        sendLogisticsState(player);
    }

    private void logisticsPut(ServerPlayer player, String device, String slotText) {
        var handler = logisticsHandler(device);
        Integer slot = parseInt(slotText);
        ItemStack held = player.getMainHandItem();
        if (handler == null || slot == null || slot < 0 || slot >= handler.size()) {
            respond(player, "invalid slot");
            return;
        }
        if (held.isEmpty()) {
            respond(player, "hold an item in your main hand");
            return;
        }
        int inserted;
        try (Transaction transaction = Transaction.openRoot()) {
            inserted = handler.insert(slot, net.neoforged.neoforge.transfer.item.ItemResource.of(held),
                    held.getCount(), transaction);
            transaction.commit();
        }
        if (inserted > 0) {
            held.shrink(inserted);
            player.getInventory().setChanged();
        }
        sendLogisticsState(player);
    }

    public void sendAutomationState(ServerPlayer player) {
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        java.util.List<String> rules = cabinet == null ? java.util.List.of() : java.util.List.copyOf(cabinet.rules());
        PacketDistributor.sendToPlayer(player,
                new computera.net.AutomationStatePayload(worldPosition, rules, automationItemCatalog()));
    }

    /** Distinct items known to the cluster, as id=count, for the icon picker. */
    private java.util.List<String> automationItemCatalog() {
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
        }
        var totals = new java.util.TreeMap<String, Long>();
        for (var entries : computera.logistics.LogisticsIndex.build(serverLevel, cluster).entrySet()) {
            long sum = 0;
            for (var entry : entries.getValue()) {
                sum += entry.count();
            }
            if (sum > 0) {
                totals.merge(entries.getKey(), sum, Long::sum);
            }
        }
        return totals.entrySet().stream()
                .limit(256)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    private void automationAdd(ServerPlayer player, String type, String data) {
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        if (cabinet == null) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        String[] parts = data.split("\\|", -1);
        if (parts.length != 5 || parts[0].isBlank() || parts[1].isBlank()
                || net.minecraft.resources.Identifier.tryParse(parts[2]) == null
                || parseInt(parts[3]) == null || parseInt(parts[3]) <= 0
                || parseInt(parts[4]) == null || parseInt(parts[4]) <= 0
                || cabinet.rules().size() >= 128) {
            respond(player, "invalid automation rule");
            return;
        }
        String rule = "move|" + data + "|true";
        cabinet.addRule(rule);
        sendAutomationState(player);
    }

    private void automationDelete(ServerPlayer player, String indexText) {
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        Integer index = parseInt(indexText);
        if (cabinet != null && index != null) {
            cabinet.removeRule(index);
        }
        sendAutomationState(player);
    }

    private void automationToggle(ServerPlayer player, String indexText) {
        CabinetBlockEntity cabinet = primaryCabinet(cluster());
        Integer index = parseInt(indexText);
        if (cabinet != null && index != null) {
            cabinet.toggleRule(index);
        }
        sendAutomationState(player);
    }

    private void logisticsPickup(ServerPlayer player, String device, String slotText) {
        boolean half = slotText.endsWith(":half");
        String clean = half ? slotText.substring(0, slotText.length() - 5) : slotText;
        Integer slot = parseInt(clean);
        if (slot == null) {
            return;
        }
        ItemStack carried = player.inventoryMenu.getCarried();
        if (!carried.isEmpty()) {
            sendLogisticsState(player);
            return;
        }
        if ("player".equals(device)) {
            var inventory = player.getInventory();
            if (slot < 0 || slot >= 36) {
                return;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                return;
            }
            int amount = half ? (stack.getCount() + 1) / 2 : stack.getCount();
            player.inventoryMenu.setCarried(stack.copyWithCount(amount));
            stack.shrink(amount);
            inventory.setChanged();
            sendLogisticsState(player);
            return;
        }
        var handler = logisticsHandler(device);
        if (handler == null || slot < 0 || slot >= handler.size()) {
            sendLogisticsState(player);
            return;
        }
        var resource = handler.getResource(slot);
        if (!resource.isEmpty()) {
            int maxStack = Math.max(1, resource.toStack(1).getMaxStackSize());
            int available = (int) Math.min(handler.getAmountAsLong(slot), maxStack);
            int want = half ? (available + 1) / 2 : available;
            if (want > 0) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int taken = handler.extract(slot, resource, want, transaction);
                    if (taken > 0) {
                        transaction.commit();
                        player.inventoryMenu.setCarried(resource.toStack(taken));
                    }
                }
            }
        }
        sendLogisticsState(player);
    }

    private void logisticsDrop(ServerPlayer player, String target, String slotText) {
        boolean one = slotText.endsWith(":one");
        String clean = one ? slotText.substring(0, slotText.length() - 4) : slotText;
        Integer slot = parseInt(clean);
        if (slot == null) {
            return;
        }
        ItemStack carried = player.inventoryMenu.getCarried();
        if (carried.isEmpty()) {
            sendLogisticsState(player);
            return;
        }
        int amount = one ? 1 : carried.getCount();
        if ("player".equals(target)) {
            var inventory = player.getInventory();
            if (slot < 0 || slot >= 36) {
                return;
            }
            ItemStack existing = inventory.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, carried)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int move = Math.min(space, amount);
                existing.grow(move);
                carried.shrink(move);
            } else if (existing.isEmpty()) {
                int move = Math.min(amount, carried.getMaxStackSize());
                inventory.setItem(slot, carried.copyWithCount(move));
                carried.shrink(move);
            }
            inventory.setChanged();
        } else {
            var handler = logisticsHandler(target);
            if (handler == null || slot < 0 || slot >= handler.size()) {
                return;
            }
            int inserted;
            try (Transaction transaction = Transaction.openRoot()) {
                inserted = handler.insert(slot, net.neoforged.neoforge.transfer.item.ItemResource.of(carried),
                        amount, transaction);
                transaction.commit();
            }
            if (inserted > 0) {
                carried.shrink(inserted);
            }
        }
        if (carried.isEmpty()) {
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
        } else {
            player.inventoryMenu.setCarried(carried);
        }
        sendLogisticsState(player);
    }

    private void logisticsRename(ServerPlayer player, String device, String alias) {
        Cluster cluster = cluster();
        CabinetBlockEntity cabinet = cluster == null ? null : primaryCabinet(cluster);
        if (cabinet != null) {
            cabinet.setAlias(device, alias);
        }
        sendLogisticsState(player);
    }

    private void logisticsGroup(ServerPlayer player, String device, String group) {
        Cluster cluster = cluster();
        CabinetBlockEntity cabinet = cluster == null ? null : primaryCabinet(cluster);
        if (cabinet != null) {
            cabinet.setGroup(device, group);
        }
        sendLogisticsState(player);
    }

    private void logisticsDistribute(ServerPlayer player, String spec, String slotText) {
        String sourceKey;
        String targetSpec;
        int sep = spec.indexOf('|');
        if (sep >= 0) {
            sourceKey = spec.substring(0, sep);
            targetSpec = spec.substring(sep + 1);
        } else {
            String[] legacy = spec.split(",");
            sourceKey = legacy.length > 0 ? legacy[0] : "";
            targetSpec = spec;
        }
        String[] devices = targetSpec.split(",");
        Integer slot = parseInt(slotText);
        if (sourceKey.isEmpty() || devices.length == 0 || slot == null) {
            return;
        }
        var source = logisticsHandler(sourceKey);
        if (source == null || slot < 0 || slot >= source.size()) {
            return;
        }
        var resource = source.getResource(slot);
        if (resource.isEmpty()) {
            return;
        }
        int maxStack = Math.max(1, resource.toStack(1).getMaxStackSize());
        int total = (int) Math.min(source.getAmountAsLong(slot), maxStack);
        int per = Math.max(1, total / devices.length);
        int remaining = total;
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = source.extract(slot, resource, total, transaction);
            if (extracted != total) {
                return;
            }
            for (String device : devices) {
                if (device.isEmpty()) {
                    continue;
                }
                var handler = logisticsHandler(device);
                if (handler != null && remaining > 0) {
                    remaining -= handler.insert(resource, Math.min(per, remaining), transaction);
                }
            }
            if (remaining == 0 || source.insert(slot, resource, remaining, transaction) == remaining) {
                transaction.commit();
            }
        }
        sendLogisticsState(player);
    }

    private void logisticsGive(ServerPlayer player, String targetSpec, String data) {
        String[] targets = targetSpec.split(",");
        String[] parts = data.split("\\|", 2);
        if (targets.length == 0 || parts.length < 2) {
            return;
        }
        Integer perBox = parseInt(parts[1]);
        ItemStack template = itemStackById(parts[0]);
        if (perBox == null || perBox <= 0 || perBox > 64 || targets.length > 256 || template.isEmpty()) {
            sendLogisticsState(player);
            return;
        }
        ItemResource resource = ItemResource.of(template);
        java.util.List<ItemStack> buffer = new java.util.ArrayList<>();
        int need = perBox * targets.length;
        if (sourceForGive(player, template, resource, need, buffer) <= 0) {
            respond(player, "[Computera] nothing to give: " + parts[0]);
            sendLogisticsState(player);
            return;
        }
        for (String device : targets) {
            var handler = logisticsHandler(device);
            if (handler == null) {
                continue;
            }
            int want = perBox;
            for (java.util.Iterator<ItemStack> iterator = buffer.iterator(); iterator.hasNext() && want > 0; ) {
                ItemStack stack = iterator.next();
                int amount = Math.min(want, stack.getCount());
                int inserted;
                try (Transaction transaction = Transaction.openRoot()) {
                    inserted = handler.insert(resource, amount, transaction);
                    transaction.commit();
                }
                if (inserted > 0) {
                    stack.shrink(inserted);
                    want -= inserted;
                }
                if (stack.isEmpty()) {
                    iterator.remove();
                } else if (inserted == 0) {
                    break;
                }
            }
        }
        int maxStack = Math.max(1, template.getMaxStackSize());
        for (ItemStack stack : buffer) {
            int leftover = stack.getCount();
            while (leftover > 0) {
                int chunk = Math.min(leftover, maxStack);
                ItemStack piece = stack.copyWithCount(chunk);
                if (!player.getInventory().add(piece)) {
                    player.drop(piece, false);
                }
                leftover -= chunk;
            }
        }
        sendLogisticsState(player);
    }

    /** Pulls the wanted items from the carried stack, the player, then the network. */
    private int sourceForGive(ServerPlayer player, ItemStack template, ItemResource resource,
                              int need, java.util.List<ItemStack> buffer) {
        int got = 0;
        int maxStack = Math.max(1, template.getMaxStackSize());
        ItemStack carried = player.inventoryMenu.getCarried();
        if (carried != null && !carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, template)) {
            int take = Math.min(need, carried.getCount());
            addSplitToBuffer(buffer, template, take, maxStack);
            carried.shrink(take);
            got += take;
            if (carried.isEmpty()) {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < 36 && got < need; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            int take = Math.min(need - got, stack.getCount());
            stack.shrink(take);
            addSplitToBuffer(buffer, template, take, maxStack);
            got += take;
        }
        Cluster cluster = cluster();
        if (got < need && cluster != null && level instanceof ServerLevel serverLevel) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(template.getItem()).toString();
            var entries = computera.logistics.LogisticsIndex.build(serverLevel, cluster)
                    .getOrDefault(itemId, java.util.List.of());
            for (var entry : entries) {
                if (got >= need) {
                    break;
                }
                if (!serverLevel.hasChunkAt(entry.pos())) {
                    continue;
                }
                var handler = serverLevel.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, entry.pos(), entry.accessSide());
                if (handler == null || entry.slot() < 0 || entry.slot() >= handler.size()) {
                    continue;
                }
                int want = Math.min(need - got, (int) Math.min(entry.count(), Integer.MAX_VALUE));
                int extracted;
                try (Transaction transaction = Transaction.openRoot()) {
                    extracted = handler.extract(entry.slot(), resource, want, transaction);
                    transaction.commit();
                }
                if (extracted > 0) {
                    addSplitToBuffer(buffer, template, extracted, maxStack);
                    got += extracted;
                }
            }
        }
        inventory.setChanged();
        return got;
    }

    private static void addSplitToBuffer(java.util.List<ItemStack> buffer, ItemStack template, int count, int maxStack) {
        int remaining = count;
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            buffer.add(template.copyWithCount(chunk));
            remaining -= chunk;
        }
    }

    private static ItemStack itemStackById(String id) {
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(net.minecraft.resources.Identifier.parse(id.trim()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    public void sendDisplayList(ServerPlayer player) {
        Cluster cluster = cluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            respond(player, "no cabinet in this cluster");
            return;
        }
        java.util.List<String> walls = new java.util.ArrayList<>();
        java.util.Set<BlockPos> seenControllers = new java.util.HashSet<>();
        scanWalls(serverLevel, cluster.blocks(), seenControllers, walls);
        scanWalls(serverLevel, cluster.terminals(), seenControllers, walls);
        scanWalls(serverLevel, cluster.ports(), seenControllers, walls);
        PacketDistributor.sendToPlayer(player, new computera.net.DisplayListPayload(worldPosition, walls));
    }

    private void scanWalls(ServerLevel serverLevel, java.util.Collection<BlockPos> anchors,
                           java.util.Set<BlockPos> seenControllers, java.util.List<String> out) {
        for (BlockPos anchor : anchors) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos pos = anchor.relative(direction);
                if (!serverLevel.hasChunkAt(pos)) {
                    continue;
                }
                if (!(serverLevel.getBlockState(pos).getBlock() instanceof computera.block.DisplayPanelBlock)) {
                    continue;
                }
                if (serverLevel.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel) {
                    DisplayPanelBlockEntity controller = panel.controller();
                    BlockPos ctrlPos = controller.controllerPosition();
                    if (seenControllers.add(ctrlPos)) {
                        out.add(ctrlPos.getX() + "," + ctrlPos.getY() + "," + ctrlPos.getZ()
                                + "|" + controller.wallWidth() + "|" + controller.wallHeight()
                                + "|" + controller.mode().name());
                    }
                }
            }
        }
    }

    private void setDisplayMode(ServerPlayer player, String posText, String modeText) {
        String[] parts = posText.split(",");
        if (parts.length == 3) {
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
                Cluster current = cluster();
                if (current != null && level != null && level.hasChunkAt(pos)
                        && level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel
                        && panel.isController()) {
                    Cluster wallCluster = panel.linkedCluster();
                    if (wallCluster != null && wallCluster.blocks().contains(worldPosition)) {
                        panel.setMode(computera.blockentity.DisplayMode.byName(modeText));
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        sendDisplayList(player);
    }

    private void returnCarried(ServerPlayer player) {
        ItemStack carried = player.inventoryMenu.getCarried();
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        if (!carried.isEmpty()) {
            if (!player.getInventory().add(carried)) {
                player.drop(carried, false);
            }
            player.getInventory().setChanged();
        }
    }

    private static boolean isWhitelistedVanillaMenu(net.minecraft.world.level.block.Block block) {
        return block instanceof net.minecraft.world.level.block.ChestBlock
                || block instanceof net.minecraft.world.level.block.BarrelBlock
                || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                || block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                || block instanceof net.minecraft.world.level.block.HopperBlock
                || block instanceof net.minecraft.world.level.block.DispenserBlock
                || block instanceof net.minecraft.world.level.block.BrewingStandBlock;
    }

    private static boolean isWhitelistedComputeraMenu(net.minecraft.world.level.block.Block block) {
        return block instanceof computera.block.CrateBlock
                || block instanceof computera.block.CabinetBlock;
    }

    private void openDeviceMenu(ServerPlayer player, String device) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Cluster cluster = cluster();
        if (cluster == null) {
            return;
        }
        var pos = DeviceRegistry.positions(serverLevel, cluster).get(device);
        if (pos == null || !serverLevel.hasChunkAt(pos)) {
            respond(player, "no such device");
            return;
        }
        BlockState state = serverLevel.getBlockState(pos);
        var block = state.getBlock();
        boolean isVanilla = isWhitelistedVanillaMenu(block);
        boolean isComputera = isWhitelistedComputeraMenu(block);
        if (!isVanilla && !isComputera) {
            player.sendSystemMessage(Component.translatable("computera.msg.native_unsupported"));
            return;
        }
        BlockEntity targetBe = serverLevel.getBlockEntity(pos);
        if (targetBe == null) {
            respond(player, "this block has no native menu");
            return;
        }
        net.minecraft.world.MenuProvider provider = isVanilla
                ? state.getMenuProvider(serverLevel, pos)
                : (targetBe instanceof net.minecraft.world.MenuProvider mp ? mp : null);
        if (provider == null) {
            respond(player, "this block has no native menu or is blocked");
            return;
        }
        returnCarried(player);
        var previous = player.containerMenu;
        var opened = isVanilla
                ? player.openMenu(provider)
                : player.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
        if (opened.isPresent() && player.containerMenu != previous) {
            computera.menu.RemoteMenus.register(player, this, targetBe);
        }
    }

    private void logisticsStore(ServerPlayer player, String device, String slotText) {
        var handler = logisticsHandler(device);
        Integer slot = parseInt(slotText);
        if (handler == null || slot == null || slot < 0 || slot >= 36) {
            respond(player, "invalid slot");
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            respond(player, "that slot is empty");
            return;
        }
        int inserted;
        try (Transaction transaction = Transaction.openRoot()) {
            inserted = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(stack),
                    stack.getCount(), transaction);
            transaction.commit();
        }
        if (inserted > 0) {
            stack.shrink(inserted);
            player.getInventory().setChanged();
        }
        sendLogisticsState(player);
    }

    private void logisticsMove(ServerPlayer player, String spec, String countText) {
        String[] parts = spec.split(">");
        if (parts.length != 2) {
            respond(player, "invalid move target");
            return;
        }
        String[] from = parts[0].split(":");
        String[] to = parts[1].split(":");
        if (from.length != 2 || to.length != 2) {
            respond(player, "invalid move target");
            return;
        }
        var source = logisticsHandler(from[0]);
        var target = logisticsHandler(to[0]);
        Integer fromSlot = parseInt(from[1]);
        Integer toSlot = parseInt(to[1]);
        Integer amount = parseInt(countText);
        if (source == null || target == null || fromSlot == null || toSlot == null
                || fromSlot < 0 || fromSlot >= source.size() || toSlot < 0 || toSlot >= target.size()) {
            respond(player, "invalid containers");
            return;
        }
        var resource = source.getResource(fromSlot);
        if (resource.isEmpty()) {
            respond(player, "source slot is empty");
            return;
        }
        int want = Math.max(1, Math.min(amount == null ? 64 : amount,
                (int) Math.min(source.getAmountAsLong(fromSlot), 64)));
        int inserted;
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted;
            try (Transaction probe = Transaction.open(transaction)) {
                accepted = target.insert(toSlot, resource, want, probe);
            }
            int extracted = source.extract(fromSlot, resource, accepted, transaction);
            inserted = extracted > 0 ? target.insert(toSlot, resource, extracted, transaction) : 0;
            if (inserted != extracted) {
                inserted = 0;
            }
            if (inserted > 0) {
                transaction.commit();
            }
        }
        if (inserted <= 0) {
            respond(player, "move failed (target full?)");
        }
        sendLogisticsState(player);
    }

    @Nullable
    private net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>
            logisticsHandler(String device) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Cluster cluster = cluster();
        if (cluster == null) {
            return null;
        }
        var entry = DeviceRegistry.entries(serverLevel, cluster).get(device);
        return DeviceRegistry.itemHandler(serverLevel, entry);
    }

    @Nullable
    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void releaseViewer(ServerPlayer player) {
        viewers.remove(player.getUUID());
        sessionOrigins.remove(player.getUUID());
        logisticsSelections.remove(player.getUUID());
        returnCarried(player);
    }

    private void respond(ServerPlayer player, String... lines) {
        List<String> clamped = new ArrayList<>(lines.length);
        for (String line : lines) {
            String safe = clampLine(line);
            clamped.add(safe);
            print(safe);
        }
        var audience = onlineViewers();
        for (ServerPlayer viewer : audience) {
            PacketDistributor.sendToPlayer(viewer, new TerminalLinesPayload(worldPosition, clamped));
        }
        if (!audience.contains(player)) {
            PacketDistributor.sendToPlayer(player, new TerminalLinesPayload(worldPosition, clamped));
        }
    }

    @Nullable
    private Cluster cluster() {
        return level instanceof ServerLevel serverLevel
                ? ClusterManager.clusterFor(serverLevel, worldPosition)
                : null;
    }

    private CabinetBlockEntity primaryCabinet(Cluster cluster) {
        return cluster == null ? null : cluster.primaryCabinet();
    }

    private final class TerminalHost implements ScriptHost {
        private final Map<String, Device> devices;

        private TerminalHost(Map<String, Device> devices) {
            this.devices = devices;
        }

        @Override
        public void print(String text) {
            if (pendingOutput.size() < MAX_PENDING_PER_TICK) {
                pendingOutput.add(clampLine(text));
            }
        }

        @Override
        public void error(String text) {
            if (pendingOutput.size() < MAX_PENDING_PER_TICK) {
                pendingOutput.add(clampLine("[error] " + text));
            }
        }

        @Override
        public Device device(String name) {
            return devices.get(name);
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("TapeLines", tapeLines);
        var lines = output.list("Output", com.mojang.serialization.Codec.STRING);
        for (String line : this.output) {
            lines.add(line);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tapeLines = input.getIntOr("TapeLines", 0);
        output.clear();
        for (String line : input.listOrEmpty("Output", com.mojang.serialization.Codec.STRING)) {
            output.add(line);
        }
    }
}
