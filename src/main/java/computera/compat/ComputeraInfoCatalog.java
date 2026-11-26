package computera.compat;

import computera.registry.ModItems;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Shared zero-dependency catalog of Computera hardware info pages and custom Card Punch recipes,
 * consumed by both the JEI and EMI optional plugins.
 */
public final class ComputeraInfoCatalog {

    public record InfoEntry(String id, List<ItemStack> stacks, String translationKey) {
    }

    public record PunchRecipe(String id, ItemStack input, ItemStack output, String noteKey) {
    }

    private ComputeraInfoCatalog() {
    }

    /** Returns all Computera hardware documentation entries for JEI and EMI info pages. */
    public static List<InfoEntry> infoEntries() {
        return List.of(
                new InfoEntry(
                        "cabinet",
                        List.of(new ItemStack(ModItems.CABINET.get())),
                        "info.computera.cabinet"
                ),
                new InfoEntry(
                        "terminal_crt",
                        List.of(new ItemStack(ModItems.TERMINAL_CRT.get())),
                        "info.computera.terminal_crt"
                ),
                new InfoEntry(
                        "terminal_teletype",
                        List.of(new ItemStack(ModItems.TERMINAL_TELETYPE.get())),
                        "info.computera.terminal_teletype"
                ),
                new InfoEntry(
                        "display_panel",
                        List.of(new ItemStack(ModItems.DISPLAY_PANEL.get())),
                        "info.computera.display_panel"
                ),
                new InfoEntry(
                        "eu_adapter",
                        List.of(new ItemStack(ModItems.EU_ADAPTER.get())),
                        "info.computera.eu_adapter"
                ),
                new InfoEntry(
                        "logistics_port",
                        List.of(new ItemStack(ModItems.LOGISTICS_PORT.get())),
                        "info.computera.logistics_port"
                ),
                new InfoEntry(
                        "logistics_pipe",
                        List.of(new ItemStack(ModItems.LOGISTICS_PIPE.get())),
                        "info.computera.logistics_pipe"
                ),
                new InfoEntry(
                        "crate",
                        List.of(new ItemStack(ModItems.CRATE.get())),
                        "info.computera.crate"
                ),
                new InfoEntry(
                        "punch_machine",
                        List.of(
                                new ItemStack(ModItems.PUNCH_MACHINE.get()),
                                new ItemStack(ModItems.BLANK_CARD.get()),
                                new ItemStack(ModItems.PUNCHED_CARD.get())
                        ),
                        "info.computera.punch_machine"
                ),
                new InfoEntry(
                        "storage_media",
                        List.of(
                                new ItemStack(ModItems.HARD_DRIVE.get()),
                                new ItemStack(ModItems.FLASH_DRIVE.get()),
                                new ItemStack(ModItems.PAPER_TAPE.get())
                        ),
                        "info.computera.storage_media"
                ),
                new InfoEntry(
                        "compute_cards",
                        List.of(
                                new ItemStack(ModItems.COMPUTE_CARD_1.get()),
                                new ItemStack(ModItems.COMPUTE_CARD_2.get()),
                                new ItemStack(ModItems.COMPUTE_CARD_3.get()),
                                new ItemStack(ModItems.COMPUTE_CARD_4.get()),
                                new ItemStack(ModItems.COMPUTE_CARD_5.get())
                        ),
                        "info.computera.compute_cards"
                ),
                new InfoEntry(
                        "networking",
                        List.of(
                                new ItemStack(ModItems.CABLE.get()),
                                new ItemStack(ModItems.NETWORK_CONNECTOR.get())
                        ),
                        "info.computera.networking"
                ),
                new InfoEntry(
                        "basics",
                        List.of(
                                new ItemStack(ModItems.COPPER_WIRE.get()),
                                new ItemStack(ModItems.GUIDE_BOOK.get())
                        ),
                        "info.computera.basics"
                )
        );
    }

    /** Returns custom in-world Card Punch recipes for JEI and EMI. */
    public static List<PunchRecipe> punchRecipes() {
        return List.of(
                new PunchRecipe(
                        "punch_program",
                        new ItemStack(ModItems.BLANK_CARD.get()),
                        new ItemStack(ModItems.PUNCHED_CARD.get()),
                        "jei.computera.card_punch.note"
                )
        );
    }
}
