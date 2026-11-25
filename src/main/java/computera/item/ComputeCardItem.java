package computera.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class ComputeCardItem extends Item {
    private final int tier;
    private final int threads;

    public ComputeCardItem(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        this.threads = 1 << tier;
    }

    public int tier() {
        return tier;
    }

    public int threads() {
        return threads;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("computera.tooltip.threads", threads));
    }
}
