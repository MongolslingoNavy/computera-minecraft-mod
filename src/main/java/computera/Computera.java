package computera;

import com.mojang.logging.LogUtils;
import computera.blockentity.CabinetBlockEntity;
import computera.command.ComputeraCommands;
import computera.net.ComputeraNetwork;
import computera.registry.ModDataComponents;
import computera.registry.ModEntities;
import computera.registry.ModMenus;
import computera.cluster.ClusterManager;
import computera.registry.ModBlockEntities;
import computera.registry.ModBlocks;
import computera.registry.ModCreativeTabs;
import computera.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Computera.MODID)
public class Computera {
    public static final String MODID = "computera";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Computera(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(Computera::registerCapabilities);
        modEventBus.addListener(ComputeraNetwork::register);
        NeoForge.EVENT_BUS.addListener(ClusterManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(ComputeraCommands::register);
        NeoForge.EVENT_BUS.addListener(computera.compat.ConnectionRefresher::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(computera.compat.ConnectionRefresher::onServerTick);
        NeoForge.EVENT_BUS.addListener(Computera::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(Computera::onServerStopping);
    }

    private static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ClusterManager.onLevelUnload(serverLevel);
            computera.compat.ConnectionRefresher.onLevelUnload(serverLevel);
            computera.menu.RemoteMenus.onLevelUnload(serverLevel);
        }
    }

    private static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        ClusterManager.clearAll();
        computera.compat.ConnectionRefresher.clearAll();
        computera.menu.RemoteMenus.clearAll();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        blockEntity instanceof CabinetBlockEntity cabinet ? cabinet.energy() : null,
                ModBlocks.CABINET.get());
        event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        blockEntity instanceof computera.blockentity.EuAdapterBlockEntity adapter
                                ? adapter.energy() : null,
                ModBlocks.EU_ADAPTER.get());
        event.registerBlock(Capabilities.Item.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        blockEntity instanceof computera.blockentity.CrateBlockEntity crate
                                ? crate.groupHandler() : null,
                ModBlocks.CRATE.get());
    }
}
