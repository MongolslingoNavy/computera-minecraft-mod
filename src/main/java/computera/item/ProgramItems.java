package computera.item;

import computera.registry.ModDataComponents;
import computera.registry.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProgramItems {
    public static final int HARD_DRIVE_LIMIT = 16;
    public static final int FLASH_LIMIT = 4;
    public static final int CARD_LIMIT = 1;
    public static final int MAX_FILE_SIZE = 16_384;
    public static final int MAX_NAME_LENGTH = 64;

    public static Map<String, String> files(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FILES.get(), Map.of());
    }

    public static int limitFor(ItemStack stack) {
        if (stack.is(ModItems.HARD_DRIVE.get())) {
            return HARD_DRIVE_LIMIT;
        }
        if (stack.is(ModItems.FLASH_DRIVE.get())) {
            return FLASH_LIMIT;
        }
        if (stack.is(ModItems.PUNCHED_CARD.get())) {
            return CARD_LIMIT;
        }
        return 0;
    }

    public static boolean store(ItemStack stack, String name, String source) {
        int limit = limitFor(stack);
        if (limit <= 0 || name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        String text = source == null ? "" : source;
        if (text.length() > MAX_FILE_SIZE) {
            return false;
        }
        Map<String, String> files = new LinkedHashMap<>(files(stack));
        if (!files.containsKey(name) && files.size() >= limit) {
            return false;
        }
        files.put(name, text);
        stack.set(ModDataComponents.FILES.get(), Map.copyOf(files));
        return true;
    }

    public static String read(ItemStack stack, String name) {
        return files(stack).get(name);
    }

    public static boolean writeCard(ItemStack card, String name, String source) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        String text = source == null ? "" : source;
        if (text.length() > MAX_FILE_SIZE) {
            return false;
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put(name, text);
        card.set(ModDataComponents.FILES.get(), Map.copyOf(files));
        return true;
    }

    public static String singleName(ItemStack stack) {
        return files(stack).keySet().stream().findFirst().orElse("");
    }

    private ProgramItems() {
    }
}
