package computera.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Parses and matches item filter specifications that accept either an exact item ID
 * (e.g. {@code minecraft:iron_ingot}) or an item tag prefixed with {@code #}
 * (e.g. {@code #c:ingots/iron}).
 */
public final class ItemFilter {

    private ItemFilter() {
    }

    public static boolean isTagSpec(@Nullable String spec) {
        return spec != null && spec.trim().startsWith("#");
    }

    @Nullable
    private static Identifier parseIdentifier(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(raw);
        if (id == null || id.getPath().isEmpty()) {
            return null;
        }
        return id;
    }

    @Nullable
    public static TagKey<Item> parseTag(@Nullable String spec) {
        if (spec == null) {
            return null;
        }
        String trimmed = spec.trim();
        if (!trimmed.startsWith("#") || trimmed.length() <= 1) {
            return null;
        }
        Identifier id = parseIdentifier(trimmed.substring(1));
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    /**
     * Parses a tag identifier with or without a leading {@code #}.
     */
    @Nullable
    public static TagKey<Item> parseLooseTag(@Nullable String spec) {
        if (spec == null) {
            return null;
        }
        String trimmed = spec.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        Identifier id = parseIdentifier(trimmed);
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    @Nullable
    public static String normalize(@Nullable String spec) {
        if (spec == null) {
            return null;
        }
        String trimmed = spec.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("#")) {
            Identifier id = parseIdentifier(trimmed.substring(1));
            return id == null ? null : "#" + id;
        }
        Identifier id = parseIdentifier(trimmed);
        return id == null ? null : id.toString();
    }

    public static boolean isValid(@Nullable String spec) {
        return normalize(spec) != null;
    }

    public static boolean matches(@Nullable String spec, @Nullable ItemResource resource) {
        if (spec == null || spec.isBlank() || resource == null || resource.isEmpty()) {
            return false;
        }
        String trimmed = spec.trim();
        if (trimmed.startsWith("#")) {
            TagKey<Item> tag = parseTag(trimmed);
            return tag != null && resource.typeHolder().is(tag);
        }
        Identifier expected = parseIdentifier(trimmed);
        if (expected == null) {
            return false;
        }
        Identifier actual = BuiltInRegistries.ITEM.getKey(resource.getItem());
        return expected.equals(actual);
    }

    public static boolean matches(@Nullable String spec, @Nullable ItemStack stack) {
        if (spec == null || spec.isBlank() || stack == null || stack.isEmpty()) {
            return false;
        }
        String trimmed = spec.trim();
        if (trimmed.startsWith("#")) {
            TagKey<Item> tag = parseTag(trimmed);
            return tag != null && stack.is(tag);
        }
        Identifier expected = parseIdentifier(trimmed);
        if (expected == null) {
            return false;
        }
        Identifier actual = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return expected.equals(actual);
    }

    public static boolean matchesItemId(@Nullable String spec, @Nullable String itemId) {
        if (spec == null || spec.isBlank() || itemId == null || itemId.isBlank()) {
            return false;
        }
        String trimmed = spec.trim();
        if (trimmed.startsWith("#")) {
            TagKey<Item> tag = parseTag(trimmed);
            Identifier parsedItem = parseIdentifier(itemId.trim());
            if (tag == null || parsedItem == null) {
                return false;
            }
            Item item = BuiltInRegistries.ITEM.getValue(parsedItem);
            return item != null && item != Items.AIR && item.builtInRegistryHolder().is(tag);
        }
        Identifier expected = parseIdentifier(trimmed);
        Identifier actual = parseIdentifier(itemId.trim());
        return expected != null && expected.equals(actual);
    }

    /**
     * Resolves an item for client GUI icon preview: returns the exact item for IDs,
     * or the first registered item in the tag for {@code #namespace:path} specs.
     */
    @Nullable
    public static Item previewItem(@Nullable String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String trimmed = spec.trim();
        if (trimmed.startsWith("#")) {
            TagKey<Item> tag = parseTag(trimmed);
            if (tag == null) {
                return null;
            }
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                Item item = holder.value();
                if (item != Items.AIR) {
                    return item;
                }
            }
            return null;
        }
        Identifier id = parseIdentifier(trimmed);
        return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
    }

    public static boolean hasTag(@Nullable ItemResource resource, @Nullable String tagSpec) {
        if (resource == null || resource.isEmpty()) {
            return false;
        }
        TagKey<Item> tag = parseLooseTag(tagSpec);
        return tag != null && resource.typeHolder().is(tag);
    }

    public static List<String> tagsOf(@Nullable ItemResource resource) {
        if (resource == null || resource.isEmpty()) {
            return List.of();
        }
        return resource.typeHolder().tags()
                .map(key -> key.location().toString())
                .sorted()
                .distinct()
                .toList();
    }
}
