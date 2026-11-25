package computera.block;

import computera.blockentity.CabinetBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
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

public class CabinetBlock extends Block implements EntityBlock {
    public static final MapCodec<CabinetBlock> CODEC = simpleCodec(CabinetBlock::new);

    public CabinetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CabinetBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CabinetBlockEntity cabinet) {
                cabinet.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet
                && (stack.getItem() instanceof computera.item.ComputeCardItem
                    || stack.is(computera.registry.ModItems.HARD_DRIVE.get()))) {
            boolean inserted = stack.is(computera.registry.ModItems.HARD_DRIVE.get())
                    ? cabinet.insertDrive(stack)
                    : cabinet.insertCard(stack);
            if (inserted) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.sendSystemMessage(Component.literal("[Computera] card inserted, threads="
                        + cabinet.installedThreads()));
            } else {
                player.sendSystemMessage(Component.literal("[Computera] no free slot for that item"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
            if (player.isShiftKeyDown()) {
                ItemStack card = cabinet.popCard();
                if (card.isEmpty()) {
                    player.sendSystemMessage(Component.literal("[Computera] no card installed"));
                } else if (!player.getInventory().add(card)) {
                    player.drop(card, false);
                }
                if (!card.isEmpty()) {
                    player.sendSystemMessage(Component.literal("[Computera] card removed, threads="
                            + cabinet.installedThreads()));
                }
            } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.openMenu(cabinet, buffer -> buffer.writeBlockPos(cabinet.getBlockPos()));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
