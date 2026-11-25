package computera.registry;

import computera.Computera;
import computera.item.ComputeCardItem;
import computera.item.GuideBookItem;
import computera.item.NetworkConnectorItem;
import computera.item.ProgramStorageItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Computera.MODID);

    public static final DeferredItem<BlockItem> CABINET = ITEMS.registerSimpleBlockItem("cabinet", ModBlocks.CABINET);
    public static final DeferredItem<BlockItem> CABLE = ITEMS.registerSimpleBlockItem("cable", ModBlocks.CABLE);
    public static final DeferredItem<BlockItem> TERMINAL_TELETYPE =
            ITEMS.registerSimpleBlockItem("terminal_teletype", ModBlocks.TERMINAL_TELETYPE);
    public static final DeferredItem<BlockItem> TERMINAL_CRT =
            ITEMS.registerSimpleBlockItem("terminal_crt", ModBlocks.TERMINAL_CRT);
    public static final DeferredItem<BlockItem> PUNCH_MACHINE =
            ITEMS.registerSimpleBlockItem("punch_machine", ModBlocks.PUNCH_MACHINE);
    public static final DeferredItem<BlockItem> LOGISTICS_PORT =
            ITEMS.registerSimpleBlockItem("logistics_port", ModBlocks.LOGISTICS_PORT);
    public static final DeferredItem<BlockItem> LOGISTICS_PIPE =
            ITEMS.registerSimpleBlockItem("logistics_pipe", ModBlocks.LOGISTICS_PIPE);
    public static final DeferredItem<BlockItem> EU_ADAPTER =
            ITEMS.registerSimpleBlockItem("eu_adapter", ModBlocks.EU_ADAPTER);
    public static final DeferredItem<BlockItem> DISPLAY_PANEL =
            ITEMS.registerSimpleBlockItem("display_panel", ModBlocks.DISPLAY_PANEL);
    public static final DeferredItem<computera.item.CrateItem> CRATE =
            ITEMS.registerItem("crate", properties -> new computera.item.CrateItem(ModBlocks.CRATE.get(), properties));

    public static final DeferredItem<ComputeCardItem> COMPUTE_CARD_1 = card("compute_card_1", 1);
    public static final DeferredItem<ComputeCardItem> COMPUTE_CARD_2 = card("compute_card_2", 2);
    public static final DeferredItem<ComputeCardItem> COMPUTE_CARD_3 = card("compute_card_3", 3);
    public static final DeferredItem<ComputeCardItem> COMPUTE_CARD_4 = card("compute_card_4", 4);
    public static final DeferredItem<ComputeCardItem> COMPUTE_CARD_5 = card("compute_card_5", 5);

    public static final DeferredItem<ProgramStorageItem> HARD_DRIVE =
            ITEMS.registerItem("hard_drive", ProgramStorageItem::new);
    public static final DeferredItem<Item> COPPER_WIRE = ITEMS.registerSimpleItem("copper_wire");
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK =
            ITEMS.registerItem("guide_book", GuideBookItem::new);
    public static final DeferredItem<NetworkConnectorItem> NETWORK_CONNECTOR =
            ITEMS.registerItem("network_connector", NetworkConnectorItem::new);
    public static final DeferredItem<Item> BLANK_CARD = ITEMS.registerSimpleItem("blank_card");
    public static final DeferredItem<ProgramStorageItem> PUNCHED_CARD =
            ITEMS.registerItem("punched_card", ProgramStorageItem::new);
    public static final DeferredItem<Item> PAPER_TAPE = ITEMS.registerSimpleItem("paper_tape");
    public static final DeferredItem<ProgramStorageItem> FLASH_DRIVE =
            ITEMS.registerItem("flash_drive", ProgramStorageItem::new);

    private static DeferredItem<ComputeCardItem> card(String name, int tier) {
        return ITEMS.registerItem(name, properties -> new ComputeCardItem(properties, tier));
    }

    private ModItems() {
    }
}
