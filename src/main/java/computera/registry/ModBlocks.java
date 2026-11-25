package computera.registry;

import computera.Computera;
import computera.block.CabinetBlock;
import computera.block.CableBlock;
import computera.block.CrtTerminalBlock;
import computera.block.CrateBlock;
import computera.block.DisplayPanelBlock;
import computera.block.EuAdapterBlock;
import computera.block.LogisticsPipeBlock;
import computera.block.LogisticsPortBlock;
import computera.block.PunchMachineBlock;
import computera.block.TeletypeTerminalBlock;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Computera.MODID);

    public static final DeferredBlock<CabinetBlock> CABINET = BLOCKS.registerBlock("cabinet", CabinetBlock::new,
            properties -> properties.strength(3.5f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<CableBlock> CABLE = BLOCKS.registerBlock("cable", CableBlock::new,
            properties -> properties.strength(0.6f).sound(SoundType.WOOL).noOcclusion());

    public static final DeferredBlock<TeletypeTerminalBlock> TERMINAL_TELETYPE = BLOCKS.registerBlock(
            "terminal_teletype", TeletypeTerminalBlock::new,
            properties -> properties.strength(2.5f).sound(SoundType.WOOD).noOcclusion());

    public static final DeferredBlock<CrtTerminalBlock> TERMINAL_CRT = BLOCKS.registerBlock(
            "terminal_crt", CrtTerminalBlock::new,
            properties -> properties.strength(2.5f).sound(SoundType.GLASS).noOcclusion());

    public static final DeferredBlock<PunchMachineBlock> PUNCH_MACHINE = BLOCKS.registerBlock(
            "punch_machine", PunchMachineBlock::new,
            properties -> properties.strength(2.5f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<LogisticsPortBlock> LOGISTICS_PORT = BLOCKS.registerBlock(
            "logistics_port", LogisticsPortBlock::new,
            properties -> properties.strength(2f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<LogisticsPipeBlock> LOGISTICS_PIPE = BLOCKS.registerBlock(
            "logistics_pipe", LogisticsPipeBlock::new,
            properties -> properties.strength(0.8f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<EuAdapterBlock> EU_ADAPTER = BLOCKS.registerBlock(
            "eu_adapter", EuAdapterBlock::new,
            properties -> properties.strength(3f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<CrateBlock> CRATE = BLOCKS.registerBlock(
            "crate", CrateBlock::new,
            properties -> properties.strength(2.5f).sound(SoundType.WOOD));

    public static final DeferredBlock<DisplayPanelBlock> DISPLAY_PANEL = BLOCKS.registerBlock(
            "display_panel", DisplayPanelBlock::new,
            properties -> properties.strength(1.2f).sound(SoundType.GLASS).noOcclusion());

    private ModBlocks() {
    }
}
