package computera.block;

import computera.blockentity.LogisticsPortBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class LogisticsPortBlock extends Block implements EntityBlock {
    public static final MapCodec<LogisticsPortBlock> CODEC = simpleCodec(LogisticsPortBlock::new);

    public LogisticsPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsPortBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            computera.cluster.ClusterManager.invalidate(serverLevel, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean movedByPiston) {
        computera.cluster.ClusterManager.invalidate(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LogisticsPortBlockEntity port) {
            if (!stack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                port.setFilter(id);
                player.sendSystemMessage(Component.literal("[Computera] port filter set to " + id));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LogisticsPortBlockEntity port) {
            if (player.isShiftKeyDown()) {
                port.setFilter(null);
                player.sendSystemMessage(Component.literal("[Computera] port filter cleared"));
            } else {
                player.sendSystemMessage(Component.literal(
                        "[Computera] port filter: " + (port.filter() == null ? "(none)" : port.filter())));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
