package computera.block;

import computera.blockentity.EuAdapterBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class EuAdapterBlock extends Block implements EntityBlock {
    public static final MapCodec<EuAdapterBlock> CODEC = simpleCodec(EuAdapterBlock::new);

    public EuAdapterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EuAdapterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof EuAdapterBlockEntity adapter) {
                adapter.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EuAdapterBlockEntity adapter) {
            player.sendSystemMessage(Component.literal(
                    "[Computera] EU adapter: FE=" + adapter.energy().getAmountAsLong()
                            + " IC2=" + (computera.compat.IndustrialCraftEu.available() ? "connected" : "not installed")));
        }
        return InteractionResult.SUCCESS;
    }
}
