package computera.command;

import computera.blockentity.CabinetBlockEntity;
import computera.blockentity.TerminalBlockEntity;
import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.device.DeviceRegistry;
import computera.registry.ModBlocks;
import computera.registry.ModItems;
import computera.script.Device;
import computera.script.ScriptCompileException;
import computera.script.ScriptCompiler;
import computera.script.ScriptHost;
import computera.script.ScriptProgram;
import computera.script.ScriptVM;
import computera.script.Value;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComputeraCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("computera")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("selftest")
                        .executes(ComputeraCommands::selfTest))
                .then(Commands.literal("selftestpower")
                        .executes(ComputeraCommands::selfTestPower))
                .then(Commands.literal("setdisplay")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ComputeraCommands::setDisplay))))
                .then(Commands.literal("openterminal")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                            .getBlockPos(context, "pos");
                                    if (player != null && context.getSource().getLevel()
                                            .getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
                                        terminal.beginSession(player);
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "opened terminal at " + pos), false);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(Component.literal(
                                            (player == null ? "no player" : "no terminal") + " at " + pos));
                                    return 0;
                                })))
                .then(Commands.literal("opencrate")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                            .getBlockPos(context, "pos");
                                    if (player != null && context.getSource().getLevel()
                                            .getBlockEntity(pos) instanceof computera.blockentity.CrateBlockEntity crate) {
                                        player.openMenu(crate, buffer -> buffer.writeBlockPos(pos));
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "opened crate at " + pos), false);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(Component.literal(
                                            (player == null ? "no player" : "no crate") + " at " + pos));
                                    return 0;
                                })))
                .then(Commands.literal("probe")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ComputeraCommands::probeBlock)))
                .then(Commands.literal("displays")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ComputeraCommands::openDisplays)))
                .then(Commands.literal("fixconnections")
                        .executes(context -> fixConnections(context, 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> fixConnections(
                                        context, IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int fixConnections(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int fixed = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!(state.getBlock() instanceof computera.block.CordBlock)) {
                        continue;
                    }
                    BlockState updated = computera.block.Connections.withConnections(level, cursor, state);
                    if (updated != state) {
                        level.setBlock(cursor, updated, 3);
                        fixed++;
                    }
                }
            }
        }
        int result = fixed;
        source.sendSuccess(() -> Component.literal("computera: refreshed " + result
                + " cable/pipe blocks within " + radius + " blocks"), true);
        return fixed;
    }

    private static int setDisplay(CommandContext<CommandSourceStack> context) {
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(context, "pos");
        String posText = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        String modeText = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mode");
        ServerLevel level = context.getSource().getLevel();
        if (level.getBlockEntity(pos) instanceof computera.blockentity.DisplayPanelBlockEntity panel) {
            panel.setMode(computera.blockentity.DisplayMode.byName(modeText));
            context.getSource().sendSuccess(() -> Component.literal("display mode = " + panel.mode()), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("no display panel at " + posText));
        return 0;
    }

    private static int probeBlock(CommandContext<CommandSourceStack> context) {
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();
        var handler = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, null);
        if (handler == null) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                var sided = level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, direction);
                if (sided != null) {
                    handler = sided;
                    context.getSource().sendSuccess(() -> Component.literal(
                            "item handler on side " + direction + ", size " + sided.size()), false);
                    break;
                }
            }
        } else {
            var h = handler;
            context.getSource().sendSuccess(() -> Component.literal("item handler (unsided), size " + h.size()), false);
        }
        if (handler == null) {
            context.getSource().sendFailure(Component.literal("no item handler at " + pos));
            return 0;
        }
        for (int slot = 0; slot < Math.min(handler.size(), 12); slot++) {
            var resource = handler.getResource(slot);
            long amount = handler.getAmountAsLong(slot);
            int finalSlot = slot;
            context.getSource().sendSuccess(() -> Component.literal(
                    "slot " + finalSlot + ": " + (resource.isEmpty() ? "-" : resource.toStack(1).getItem().toString())
                            + " x" + amount), false);
        }
        return 1;
    }

    private static int openDisplays(CommandContext<CommandSourceStack> context) {
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("run as a player"));
            return 0;
        }
        if (level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            terminal.sendDisplayList(player);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("no terminal at " + pos));
        return 0;
    }

    private static int selfTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = findOrigin(level, BlockPos.containing(source.getPosition()));

        BlockPos cabinetPos = origin;
        BlockPos cablePos = origin.east();
        BlockPos terminalPos = origin.east(2);
        BlockPos sourcePort = origin.south();
        BlockPos sourceChest = sourcePort.south();
        BlockPos destPort = origin.north();
        BlockPos destChest = destPort.north();
        BlockPos pipePos = origin.west();
        BlockPos euPos = origin.above();
        BlockPos cratePos = origin.below();
        BlockPos remotePos = origin.east(12);

        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        for (BlockPos pos : List.of(cabinetPos, cablePos, terminalPos,
                sourcePort, sourceChest, destPort, destChest, pipePos, euPos, cratePos, remotePos)) {
            previous.put(pos, level.getBlockState(pos));
        }

        List<String> results = new ArrayList<>();
        boolean ok = true;
        try {
            level.setBlock(cabinetPos, ModBlocks.CABINET.get().defaultBlockState(), 3);
            level.setBlock(cablePos, ModBlocks.CABLE.get().defaultBlockState(), 3);
            level.setBlock(terminalPos, ModBlocks.TERMINAL_TELETYPE.get().defaultBlockState(), 3);
            level.setBlock(sourcePort, ModBlocks.LOGISTICS_PORT.get().defaultBlockState(), 3);
            level.setBlock(sourceChest, Blocks.CHEST.defaultBlockState(), 3);
            ClusterManager.invalidate(level, cabinetPos);

            ok &= check(results, "cabinet block entity",
                    level.getBlockEntity(cabinetPos) instanceof CabinetBlockEntity);
            CabinetBlockEntity cabinet = (CabinetBlockEntity) level.getBlockEntity(cabinetPos);
            if (cabinet != null) {
                cabinet.setCard(0, new ItemStack(ModItems.COMPUTE_CARD_1.get()));
            }
            ok &= check(results, "compute card gives 2 threads",
                    cabinet != null && cabinet.installedThreads() == 2);

            Cluster cluster = ClusterManager.clusterFor(level, cabinetPos);
            ok &= check(results, "cluster found",
                    cluster != null && cluster.cabinetCount() == 1 && cluster.terminalCount() >= 1);
            ok &= check(results, "terminal admitted",
                    cluster != null && cluster.canAdmitTerminal());

            ResourceHandler<ItemResource> chest =
                    level.getCapability(Capabilities.Item.BLOCK, sourceChest, null);
            ok &= check(results, "chest capability", chest != null);
            if (chest != null) {
                try (Transaction transaction = Transaction.openRoot()) {
                    chest.insert(0, ItemResource.of(new ItemStack(Items.DIAMOND)), 5, transaction);
                    transaction.commit();
                }
            }

            Map<String, Device> devices = cluster == null ? Map.of() : DeviceRegistry.scan(level, cluster);
            ok &= check(results, "container device scanned", devices.containsKey("chest_1"));
            ok &= check(results, "logistics port discovered", cluster != null && !cluster.ports().isEmpty());
            ok &= check(results, "logistics index counts diamonds", cluster != null
                    && computera.logistics.LogisticsIndex.count(level, cluster, "minecraft:diamond") == 5);
            ok &= check(results, "logistics index counts #c:gems/diamond tag", cluster != null
                    && computera.logistics.LogisticsIndex.count(level, cluster, "#c:gems/diamond") == 5);

            TerminalBlockEntity terminal = level.getBlockEntity(terminalPos) instanceof TerminalBlockEntity t
                    ? t
                    : null;
            ok &= check(results, "terminal block entity", terminal != null);
            if (cabinet != null) {
                cabinet.putFile("test.py",
                        "box = devices.chest_1\n"
                                + "print(\"size\", box.size())\n"
                                + "cell = box.get(0)\n"
                                + "print(\"slot0\", cell[\"id\"], cell[\"count\"])\n");
            }

            List<String> output = new ArrayList<>();
            String programSource = terminal == null ? null : terminal.fileSource("test.py");
            ok &= check(results, "program stored on cabinet", programSource != null);
            if (programSource != null) {
                try {
                    ScriptProgram program = ScriptCompiler.compile(programSource, "test.py");
                    ScriptVM vm = new ScriptVM(program, new ScriptHost() {
                        @Override
                        public void print(String text) {
                            output.add(text);
                        }

                        @Override
                        public void error(String text) {
                            output.add("[error] " + text);
                        }

                        @Override
                        public Device device(String name) {
                            return devices.get(name);
                        }

                        @Override
                        public long currentTimeMillis() {
                            return System.currentTimeMillis();
                        }
                    });
                    int guard = 0;
                    while (vm.step(50_000) && guard++ < 200) {
                        // drain the program in bounded slices
                    }
                    ok &= check(results, "script finished", vm.isFinished());
                } catch (ScriptCompileException e) {
                    results.add("FAIL compile: " + e.getMessage());
                    ok = false;
                }
            }
            ok &= check(results, "container read works",
                    output.stream().anyMatch(line -> line.contains("minecraft:diamond") && line.contains("5")));

            if (chest != null) {
                Device box = new computera.device.ContainerDevice(chest);
                boolean rejected = false;
                try {
                    box.call("put", List.of(Value.of(1L), Value.of("minecraft:diamond"), Value.of(64L)));
                } catch (computera.script.ScriptRuntimeException expected) {
                    rejected = true;
                }
                ok &= check(results, "container has_tag matches diamond tag",
                        box.call("has_tag", List.of(Value.of(0L), Value.of("c:gems/diamond"))).asBool()
                                && box.call("has_tag", List.of(Value.of(0L), Value.of("#c:gems/diamond"))).asBool()
                                && !box.call("has_tag", List.of(Value.of(0L), Value.of("c:ingots/iron"))).asBool());
                Value tagList = box.call("tags", List.of(Value.of(0L)));
                ok &= check(results, "container tags lists c:gems/diamond",
                        tagList.isList() && tagList.asList().stream().anyMatch(v -> "c:gems/diamond".equals(v.asString())));
                ok &= check(results, "script cannot create items", rejected && chest.getAmountAsLong(0) == 5);
                box.call("move", List.of(Value.of(0L), Value.of(1L), Value.of(2L)));
                ok &= check(results, "script move conserves items",
                        chest.getAmountAsLong(0) == 3 && chest.getAmountAsLong(1) == 2);
                box.call("move", List.of(Value.of(1L), Value.of(0L), Value.of(2L)));
                try (Transaction transaction = Transaction.openRoot()) {
                    chest.insert(1, ItemResource.of(new ItemStack(Items.DIAMOND)), 63, transaction);
                    transaction.commit();
                }
                box.call("move", List.of(Value.of(0L), Value.of(1L), Value.of(5L)));
                ok &= check(results, "partial target accepts only available space",
                        chest.getAmountAsLong(0) == 4 && chest.getAmountAsLong(1) == 64);
                try (Transaction transaction = Transaction.openRoot()) {
                    chest.extract(1, ItemResource.of(new ItemStack(Items.DIAMOND)), 64, transaction);
                    chest.insert(0, ItemResource.of(new ItemStack(Items.DIAMOND)), 1, transaction);
                    transaction.commit();
                }
            }
            if (cabinet != null && cluster != null) {
                cabinet.addWireless(sourceChest);
                cabinet.addWireless(destChest);
                ok &= check(results, "script and GUI discover identical devices",
                        DeviceRegistry.scan(level, cluster).keySet().equals(DeviceRegistry.positions(level, cluster).keySet()));
            }
            if (terminal != null && cabinet != null) {
                terminal.addTape(8192);
                var first = new net.neoforged.neoforge.common.util.FakePlayer(level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AuditFirst"));
                var second = new net.neoforged.neoforge.common.util.FakePlayer(level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AuditSecond"));
                first.setPos(terminalPos.getX() + 0.5, terminalPos.getY(), terminalPos.getZ() + 0.5);
                second.setPos(first.position());
                terminal.beginSession(first);
                terminal.beginSession(second);
                level.setBlock(remotePos, Blocks.CHEST.defaultBlockState(), 3);
                cabinet.addWireless(remotePos);
                var provider = (net.minecraft.world.MenuProvider) level.getBlockEntity(remotePos);
                var remote = provider.createMenu(81, first.getInventory(), first);
                first.containerMenu = remote;
                computera.menu.RemoteMenus.register(first, terminal, level.getBlockEntity(remotePos));
                ok &= check(results, "native chest distance check rejects remote player", !remote.stillValid(first));
                ok &= check(results, "terminal authorizes remote native chest", computera.menu.RemoteMenus.stillValid(remote, first));
                remote.slots.get(0).set(new ItemStack(Items.EMERALD, 3));
                remote.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, first);
                ok &= check(results, "remote native menu click moves real items", remote.getCarried().getCount() == 3
                        && ((net.minecraft.world.Container) provider).getItem(0).isEmpty());
                first.setPos(first.getX() + 20, first.getY(), first.getZ());
                ok &= check(results, "remote menu rejects leaving terminal", !computera.menu.RemoteMenus.stillValid(remote, first));
                first.setPos(terminalPos.getX() + 0.5, terminalPos.getY(), terminalPos.getZ() + 0.5);
                cabinet.removeWireless(remotePos);
                ok &= check(results, "remote menu rejects disconnected device", !computera.menu.RemoteMenus.stillValid(remote, first));
                cabinet.addWireless(remotePos);
                level.setBlock(remotePos, Blocks.AIR.defaultBlockState(), 3);
                ok &= check(results, "remote menu rejects removed device", !computera.menu.RemoteMenus.stillValid(remote, first));
                remote.removed(first);
                first.containerMenu = first.inventoryMenu;
                first.getInventory().clearContent();
                var uiPlayer = new net.neoforged.neoforge.common.util.FakePlayer(level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AuditMenu")) {
                    @Override
                    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider menuProvider) {
                        var menu = menuProvider.createMenu(82, getInventory(), this);
                        if (menu == null) {
                            return java.util.OptionalInt.empty();
                        }
                        containerMenu = menu;
                        return java.util.OptionalInt.of(82);
                    }

                    @Override
                    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider menuProvider,
                            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer) {
                        return openMenu(menuProvider);
                    }
                };
                uiPlayer.setPos(first.position());
                terminal.beginSession(uiPlayer);
                for (String blockId : List.of("minecraft:chest", "minecraft:furnace", "ic2:generator")) {
                    var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                            net.minecraft.resources.Identifier.parse(blockId));
                    if (block == null || block == Blocks.AIR) {
                        continue;
                    }
                    level.setBlock(remotePos, block.defaultBlockState(), 3);
                    cabinet.addWireless(remotePos);
                    String key = DeviceRegistry.positions(level, cluster).entrySet().stream()
                            .filter(entry -> entry.getValue().equals(remotePos)).map(Map.Entry::getKey).findFirst().orElse("");
                    terminal.handleAction(uiPlayer, "logi_open", key, "");
                    var opened = uiPlayer.containerMenu;
                    boolean expectOpen = !blockId.startsWith("ic2:");
                    if (expectOpen) {
                        ok &= check(results, "logi_open native menu " + blockId,
                                opened != uiPlayer.inventoryMenu && computera.menu.RemoteMenus.stillValid(opened, uiPlayer));
                    } else {
                        ok &= check(results, "logi_open rejects third-party menu " + blockId,
                                opened == uiPlayer.inventoryMenu);
                    }
                    if (opened != uiPlayer.inventoryMenu) {
                        opened.removed(uiPlayer);
                        uiPlayer.containerMenu = uiPlayer.inventoryMenu;
                    }
                }
                terminal.handleAction(uiPlayer, "close", "", "");
                ok &= check(results, "two independent terminal sessions", terminal.canAccess(first) && terminal.canAccess(second));
                first.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
                terminal.handleAction(first, "logi_pickup", "player", "0:half");
                ok &= check(results, "odd stack pickup rounds up",
                        first.inventoryMenu.getCarried().getCount() == 3 && first.getInventory().getItem(0).getCount() == 2);
                terminal.handleAction(first, "logi_drop", "player", "1:one");
                ok &= check(results, "right click places exactly one",
                        first.inventoryMenu.getCarried().getCount() == 2 && first.getInventory().getItem(1).getCount() == 1);
                terminal.handleAction(first, "close", "", "");
                ok &= check(results, "closing one viewer preserves other session",
                        !terminal.canAccess(first) && terminal.canAccess(second));
                ok &= check(results, "closing returns carried stack", first.inventoryMenu.getCarried().isEmpty()
                        && first.getInventory().countItem(Items.DIAMOND) == 5);
                second.setPos(second.getX() + 20, second.getY(), second.getZ());
                terminal.handleAction(second, "save", "unauthorized.py", "print(1)");
                ok &= check(results, "out of range action rejected", !terminal.canAccess(second)
                        && !cabinet.files().containsKey("unauthorized.py"));
            }
            var clientCrate = new computera.menu.CrateContainer(null, true);
            clientCrate.data().set(0, 2);
            clientCrate.data().set(1, 3);
            ok &= check(results, "crate client receives page count", clientCrate.pages() == 3 && clientCrate.page() == 2);

            level.setBlock(pipePos, ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 3);
            level.setBlock(destPort, ModBlocks.LOGISTICS_PORT.get().defaultBlockState(), 3);
            level.setBlock(destChest, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(destPort) instanceof computera.blockentity.LogisticsPortBlockEntity port) {
                port.setFilter("#c:gems/diamond");
            }
            ClusterManager.invalidate(level, cabinetPos);
            Cluster pipeCluster = ClusterManager.clusterFor(level, cabinetPos);
            ok &= check(results, "logistics pipe discovered",
                    pipeCluster != null && !pipeCluster.pipes().isEmpty());
            int pipeMoved = pipeCluster == null ? 0 : computera.logistics.LogisticsMover.move(level, pipeCluster);
            ResourceHandler<ItemResource> destHandler =
                    level.getCapability(Capabilities.Item.BLOCK, destChest, null);
            long destCount = destHandler == null ? -1 : destHandler.getAmountAsLong(0);
            ok &= check(results, "pipe moved 4 diamonds via #tag filter", pipeMoved == 4 && destCount == 4);

            level.setBlock(euPos, ModBlocks.EU_ADAPTER.get().defaultBlockState(), 3);
            if (level.getBlockEntity(euPos) instanceof computera.blockentity.EuAdapterBlockEntity adapter) {
                adapter.serverTick();
                boolean ic2 = computera.compat.IndustrialCraftEu.available();
                results.add("INFO IC2 present=" + ic2 + " euBridge=" + adapter.connected());
                if (ic2) {
                    ok &= check(results, "EU adapter joined IC2 grid", adapter.connected());
                }
                try (Transaction transaction = Transaction.openRoot()) {
                    adapter.energy().insert(10_000, transaction);
                    transaction.commit();
                }
                adapter.serverTick();
                ok &= check(results, "EU adapter powers cabinet",
                        cabinet != null && cabinet.energy().getAmountAsInt() > 0);
            }
            level.setBlock(cratePos, ModBlocks.CRATE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(cratePos) instanceof computera.blockentity.CrateBlockEntity crate) {
                crate.setItem(0, new ItemStack(Items.DIAMOND, 5));
                var crateWindow = new computera.menu.CrateContainer(crate, false);
                crateWindow.getItem(0).shrink(2);
                crateWindow.setChanged();
                ok &= check(results, "crate menu mutates real inventory", crate.getItem(0).getCount() == 3);
                ok &= check(results, "crate rejects unused last-page slots", !crateWindow.canPlace(27));
            } else {
                ok &= check(results, "crate block entity", false);
            }
            results.add(ok ? "RESULT: PASS" : "RESULT: FAIL");
        } finally {
            for (Map.Entry<BlockPos, BlockState> entry : previous.entrySet()) {
                if (level.getBlockEntity(entry.getKey()) instanceof net.minecraft.world.Container container) {
                    container.clearContent();
                }
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            ClusterManager.invalidate(level, cabinetPos);
        }

        for (String line : results) {
            source.sendSuccess(() -> Component.literal("[Computera] " + line), false);
        }
        return ok ? 1 : 0;
    }

    private static int selfTestPower(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var level = source.getLevel();
        var generator = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.parse("ic2:generator"));
        var cable = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.parse("ic2:copper_cable"));
        if (generator == null || generator == Blocks.AIR || cable == null || cable == Blocks.AIR) {
            source.sendFailure(Component.literal("IC2 generator/cable unavailable"));
            return 0;
        }
        BlockPos origin = new BlockPos(2, Math.min(250, level.getMaxY() - 10), 50);
        level.getChunkAt(origin);
        var positions = List.of(origin, origin.east(), origin.east(2), origin.east(3), origin.east(4), origin.east(5));
        if (positions.stream().anyMatch(pos -> !level.isEmptyBlock(pos))) {
            source.sendFailure(Component.literal("Power test requires empty space"));
            return 0;
        }
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        boolean addedTicket = level.setChunkForced(chunkX, chunkZ, true);
        level.setBlock(origin, generator.defaultBlockState(), 3);
        level.setBlock(origin.east(), cable.defaultBlockState(), 3);
        level.setBlock(origin.east(2), ModBlocks.EU_ADAPTER.get().defaultBlockState(), 3);
        level.setBlock(origin.east(3), ModBlocks.CABLE.get().defaultBlockState(), 3);
        level.setBlock(origin.east(4), ModBlocks.CABLE.get().defaultBlockState(), 3);
        level.setBlock(origin.east(5), ModBlocks.CABINET.get().defaultBlockState(), 3);
        var fuel = level.getCapability(Capabilities.Item.BLOCK, origin, null);
        if (fuel != null) {
            try (var transaction = Transaction.openRoot()) {
                fuel.insert(ItemResource.of(new ItemStack(Items.COAL)), 8, transaction);
                transaction.commit();
            }
        }
        long start = level.getGameTime();
        var listener = new java.util.function.Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post>() {
            private long beforeDisconnect = -1;

            @Override
            public void accept(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
                if (event.getServer() != level.getServer()) {
                    return;
                }
                long elapsed = level.getGameTime() - start;
                if (elapsed < 200) {
                    return;
                }
                if (beforeDisconnect < 0) {
                    beforeDisconnect = level.getBlockEntity(origin.east(5)) instanceof CabinetBlockEntity cabinet
                            ? cabinet.energy().getAmountAsLong() : 0;
                    level.setBlock(origin.east(4), Blocks.AIR.defaultBlockState(), 3);
                    ClusterManager.invalidate(level, origin.east(4));
                    return;
                }
                if (elapsed < 220) {
                    return;
                }
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
                long energy = level.getBlockEntity(origin.east(5)) instanceof CabinetBlockEntity cabinet
                        ? cabinet.energy().getAmountAsLong() : 0;
                source.sendSuccess(() -> Component.literal("[Computera] POWER RESULT: "
                        + (beforeDisconnect > 0 && energy == beforeDisconnect ? "PASS" : "FAIL")
                        + " generator -> copper cable -> EU adapter -> 2 data cables -> cabinet: "
                        + energy + " FE; disconnected cable stops transfer=" + (energy == beforeDisconnect)), false);
                for (BlockPos pos : positions) {
                    if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                        container.clearContent();
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                ClusterManager.invalidate(level, origin);
                if (addedTicket) {
                    level.setChunkForced(chunkX, chunkZ, false);
                }
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        source.sendSuccess(() -> Component.literal("[Computera] power test running for 200 ticks"), false);
        return 1;
    }

    private static boolean check(List<String> results, String name, boolean condition) {
        results.add((condition ? "PASS " : "FAIL ") + name);
        return condition;
    }

    private static BlockPos findOrigin(ServerLevel level, BlockPos around) {
        int x = around.getX() + 2;
        int z = around.getZ() + 2;
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
        return new BlockPos(x, surface + 3, z);
    }

    private ComputeraCommands() {
    }
}
