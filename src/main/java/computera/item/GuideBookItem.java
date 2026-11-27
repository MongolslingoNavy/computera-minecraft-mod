package computera.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class GuideBookItem extends Item {
    private static Runnable clientOpener = () -> {
    };

    public static void setClientOpener(Runnable opener) {
        clientOpener = opener == null ? () -> {
        } : opener;
    }

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            clientOpener.run();
        }
        return InteractionResult.SUCCESS;
    }
}
