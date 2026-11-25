package computera.item;

import computera.blockentity.CrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Refuses to grow a crate group past 64 crates. */
public class CrateItem extends BlockItem {
    public CrateItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return context;
        }
        BlockPos pos = context.getClickedPos();
        int total = 1;
        Set<BlockPos> roots = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (context.getLevel().getBlockEntity(neighbour) instanceof CrateBlockEntity crate
                    && roots.add(crate.groupRoot())) {
                total += crate.groupMembers().size();
            }
        }
        if (total > CrateBlockEntity.MAX_CRATES) {
            player.sendSystemMessage(Component.literal(
                    "[Computera] 集装箱组最多 " + CrateBlockEntity.MAX_CRATES + " 个"));
            return null;
        }
        return context;
    }
}
