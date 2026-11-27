package computera.datagen;

import computera.Computera;
import computera.registry.ModBlocks;
import computera.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import java.util.concurrent.CompletableFuture;

public final class ComputeraBlockTagsProvider extends BlockTagsProvider {

    public ComputeraBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Computera.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.CABINET.get(),
                ModBlocks.CABLE.get(),
                ModBlocks.TERMINAL_TELETYPE.get(),
                ModBlocks.TERMINAL_CRT.get(),
                ModBlocks.PUNCH_MACHINE.get(),
                ModBlocks.LOGISTICS_PORT.get(),
                ModBlocks.LOGISTICS_PIPE.get(),
                ModBlocks.EU_ADAPTER.get(),
                ModBlocks.CRATE.get(),
                ModBlocks.DISPLAY_PANEL.get()
        );

        tag(BlockTags.MINEABLE_WITH_AXE).add(
                ModBlocks.CRATE.get(),
                ModBlocks.TERMINAL_TELETYPE.get()
        );

        tag(ModTags.Blocks.C_RELOCATION_NOT_SUPPORTED).add(
                ModBlocks.CABINET.get(),
                ModBlocks.CRATE.get(),
                ModBlocks.EU_ADAPTER.get(),
                ModBlocks.DISPLAY_PANEL.get(),
                ModBlocks.TERMINAL_CRT.get(),
                ModBlocks.TERMINAL_TELETYPE.get(),
                ModBlocks.LOGISTICS_PORT.get(),
                ModBlocks.PUNCH_MACHINE.get()
        );

        // Modular Crate is a non-wooden storage chest; intentionally excluded from c:chests/wooden.
        tag(ModTags.Blocks.C_CHESTS).add(
                ModBlocks.CRATE.get()
        );

        tag(ModTags.Blocks.CABLES).add(
                ModBlocks.CABLE.get(),
                ModBlocks.LOGISTICS_PIPE.get()
        );
    }
}
