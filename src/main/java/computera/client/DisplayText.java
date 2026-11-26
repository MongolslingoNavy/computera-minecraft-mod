package computera.client;

import computera.blockentity.DisplayMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Turns the wall's raw entries into lines for the current mode, on the client so names are localised. */
public final class DisplayText {
    public static List<String> content(DisplayMode mode, List<String> lines,
                                       List<String> items, List<String> devices, boolean compact) {
        return switch (mode) {
            case ITEMS -> formatItems(items);
            case DEVICES -> formatDevices(devices, compact);
            default -> formatOverview(lines);
        };
    }

    private static List<String> formatOverview(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("@")) {
                out.add(line);
                continue;
            }
            String[] parts = line.substring(1).split("\\|", -1);
            String kind = parts[0];
            switch (kind) {
                case "title" -> out.add(label("computera.gui.display.overview.title"));
                case "offline" -> out.add(label("computera.gui.display.overview.offline"));
                case "items" -> {
                    if (parts.length >= 3) {
                        out.add(Component.translatable("computera.gui.display.overview.items", parts[1], parts[2]).getString());
                    }
                }
                case "containers" -> {
                    if (parts.length >= 3) {
                        out.add(Component.translatable("computera.gui.display.overview.containers", parts[1], parts[2]).getString());
                    }
                }
                case "cabinets" -> {
                    if (parts.length >= 3) {
                        out.add(Component.translatable("computera.gui.display.overview.cabinets", parts[1], parts[2]).getString());
                    }
                }
                case "threads" -> {
                    if (parts.length >= 3) {
                        out.add(Component.translatable("computera.gui.display.overview.threads", parts[1], parts[2]).getString());
                    }
                }
                case "energy" -> {
                    if (parts.length >= 2) {
                        out.add(Component.translatable("computera.gui.display.overview.energy", parts[1]).getString());
                    }
                }
                default -> out.add(line);
            }
        }
        return out;
    }

    private static List<String> formatItems(List<String> entries) {
        List<String> out = new ArrayList<>();
        out.add(label("computera.gui.display.items_title"));
        for (String entry : entries) {
            int split = entry.lastIndexOf('=');
            if (split <= 0) {
                continue;
            }
            out.add(itemName(entry.substring(0, split)) + " " + entry.substring(split + 1));
        }
        return out;
    }

    /** entries are {@code key|alias|count|status|blockId}. */
    private static List<String> formatDevices(List<String> entries, boolean compact) {
        List<String> out = new ArrayList<>();
        out.add(label("computera.gui.display.devices_title"));
        for (String entry : entries) {
            String[] parts = entry.split("\\|", 5);
            if (parts.length < 5) {
                continue;
            }
            String key = parts[0];
            String alias = parts[1];
            String count = parts[2];
            String status = switch (parts[3]) {
                case "run" -> label(compact ? "computera.gui.display.status.compact.run" : "computera.gui.display.status.run");
                case "full" -> label(compact ? "computera.gui.display.status.compact.full" : "computera.gui.display.status.full");
                default -> label(compact ? "computera.gui.display.status.compact.idle" : "computera.gui.display.status.idle");
            };
            String name = alias.equals(key) ? localizeBlock(parts[4], key) : alias;
            out.add(name + " " + count + " " + status);
        }
        return out;
    }

    private static String localizeBlock(String blockId, String key) {
        try {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
            if (block != null && block != Blocks.AIR) {
                Item item = block.asItem();
                String name = item != Items.AIR
                        ? new ItemStack(item).getHoverName().getString()
                        : block.getName().getString();
                String index = key.substring(key.lastIndexOf('_') + 1);
                return name + " #" + index;
            }
        } catch (RuntimeException ignored) {
        }
        return key;
    }

    private static String itemName(String id) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            if (item == null || item == Items.AIR) {
                return id;
            }
            return new ItemStack(item).getHoverName().getString();
        } catch (RuntimeException e) {
            return id;
        }
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private DisplayText() {
    }
}
