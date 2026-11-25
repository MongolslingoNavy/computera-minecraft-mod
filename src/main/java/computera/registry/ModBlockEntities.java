package computera.registry;

import computera.Computera;
import computera.blockentity.CabinetBlockEntity;
import computera.blockentity.CrateBlockEntity;
import computera.blockentity.DisplayPanelBlockEntity;
import computera.blockentity.EuAdapterBlockEntity;
import computera.blockentity.LogisticsPortBlockEntity;
import computera.blockentity.PunchMachineBlockEntity;
import computera.blockentity.TerminalBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Computera.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CabinetBlockEntity>> CABINET =
            BLOCK_ENTITIES.register("cabinet",
                    () -> new BlockEntityType<>(CabinetBlockEntity::new, ModBlocks.CABINET.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> TERMINAL =
            BLOCK_ENTITIES.register("terminal",
                    () -> new BlockEntityType<>(TerminalBlockEntity::new,
                            ModBlocks.TERMINAL_TELETYPE.get(), ModBlocks.TERMINAL_CRT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PunchMachineBlockEntity>> PUNCH_MACHINE =
            BLOCK_ENTITIES.register("punch_machine",
                    () -> new BlockEntityType<>(PunchMachineBlockEntity::new, ModBlocks.PUNCH_MACHINE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogisticsPortBlockEntity>> LOGISTICS_PORT =
            BLOCK_ENTITIES.register("logistics_port",
                    () -> new BlockEntityType<>(LogisticsPortBlockEntity::new, ModBlocks.LOGISTICS_PORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrateBlockEntity>> CRATE =
            BLOCK_ENTITIES.register("crate",
                    () -> new BlockEntityType<>(CrateBlockEntity::new, ModBlocks.CRATE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayPanelBlockEntity>> DISPLAY_PANEL =
            BLOCK_ENTITIES.register("display_panel",
                    () -> new BlockEntityType<>(DisplayPanelBlockEntity::new, ModBlocks.DISPLAY_PANEL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EuAdapterBlockEntity>> EU_ADAPTER =
            BLOCK_ENTITIES.register("eu_adapter",
                    () -> new BlockEntityType<>(EuAdapterBlockEntity::new, ModBlocks.EU_ADAPTER.get()));

    private ModBlockEntities() {
    }
}
