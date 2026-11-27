package computera.datagen;

import computera.Computera;
import computera.registry.ModItems;
import computera.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

import java.util.concurrent.CompletableFuture;

public final class ComputeraItemTagsProvider extends ItemTagsProvider {

    public ComputeraItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Computera.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Common copper wire tag (kept separate from IC2 c:cables/* per ecosystem isolation rules).
        tag(ModTags.Items.C_WIRES_COPPER).add(
                ModItems.COPPER_WIRE.get()
        );
        tag(ModTags.Items.C_WIRES)
                .addTag(ModTags.Items.C_WIRES_COPPER);

        // Data cable insulation accepts leather standalone and optionally IC2/modded rubber.
        tag(ModTags.Items.CABLE_INSULATION)
                .addTag(Tags.Items.LEATHERS)
                .addOptionalTag(ModTags.Items.C_RUBBERS);

        // Mod-scoped groupings.
        tag(ModTags.Items.COMPUTE_CARDS).add(
                ModItems.COMPUTE_CARD_1.get(),
                ModItems.COMPUTE_CARD_2.get(),
                ModItems.COMPUTE_CARD_3.get(),
                ModItems.COMPUTE_CARD_4.get(),
                ModItems.COMPUTE_CARD_5.get()
        );

        tag(ModTags.Items.PROGRAM_STORAGE).add(
                ModItems.HARD_DRIVE.get(),
                ModItems.FLASH_DRIVE.get(),
                ModItems.PUNCHED_CARD.get()
        );

        tag(ModTags.Items.CABLES).add(
                ModItems.CABLE.get(),
                ModItems.LOGISTICS_PIPE.get()
        );

        // Symmetric with block tag c:chests; intentionally excluded from c:chests/wooden.
        tag(ModTags.Items.C_CHESTS).add(
                ModItems.CRATE.get()
        );

        // Network Connector is a wireless linking tool, not a wrench (excluded from c:tools/wrench).
        tag(ModTags.Items.C_TOOLS).add(
                ModItems.NETWORK_CONNECTOR.get()
        );

        tag(ModTags.Items.C_BOOKS).add(
                ModItems.GUIDE_BOOK.get()
        );
    }
}
