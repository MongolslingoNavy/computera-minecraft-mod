package computera.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Map;
import java.util.function.Consumer;

public class ProgramStorageItem extends Item {
    public ProgramStorageItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        Map<String, String> files = ProgramItems.files(stack);
        int limit = ProgramItems.limitFor(stack);
        builder.accept(Component.translatable("computera.tooltip.storage", files.size(), limit));
        int shown = 0;
        for (String name : files.keySet()) {
            if (shown++ >= 3) {
                break;
            }
            builder.accept(Component.literal(" - " + name));
        }
    }
}
