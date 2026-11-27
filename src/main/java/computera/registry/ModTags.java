package computera.registry;

import computera.Computera;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;

/**
 * Common ({@code c:*}) and mod-scoped ({@code computera:*}) block and item tag keys.
 */
public final class ModTags {

    public static final class Blocks {
        public static final TagKey<Block> C_RELOCATION_NOT_SUPPORTED = Tags.Blocks.RELOCATION_NOT_SUPPORTED;
        public static final TagKey<Block> C_CHESTS = Tags.Blocks.CHESTS;

        public static final TagKey<Block> CABLES = modBlockTag("cables");

        private static TagKey<Block> modBlockTag(String path) {
            return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Computera.MODID, path));
        }

        private Blocks() {
        }
    }

    public static final class Items {
        public static final TagKey<Item> C_WIRES = commonItemTag("wires");
        public static final TagKey<Item> C_WIRES_COPPER = commonItemTag("wires/copper");
        public static final TagKey<Item> C_RUBBERS = commonItemTag("rubbers");
        public static final TagKey<Item> C_WRENCHES = commonItemTag("wrenches");
        public static final TagKey<Item> C_BOOKS = commonItemTag("books");

        public static final TagKey<Item> C_CHESTS = Tags.Items.CHESTS;
        public static final TagKey<Item> C_TOOLS = Tags.Items.TOOLS;
        public static final TagKey<Item> C_TOOLS_WRENCH = Tags.Items.TOOLS_WRENCH;

        public static final TagKey<Item> CABLE_INSULATION = modItemTag("cable_insulation");
        public static final TagKey<Item> COMPUTE_CARDS = modItemTag("compute_cards");
        public static final TagKey<Item> PROGRAM_STORAGE = modItemTag("program_storage");
        public static final TagKey<Item> CABLES = modItemTag("cables");

        private static TagKey<Item> commonItemTag(String path) {
            return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
        }

        private static TagKey<Item> modItemTag(String path) {
            return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Computera.MODID, path));
        }

        private Items() {
        }
    }

    private ModTags() {
    }
}
