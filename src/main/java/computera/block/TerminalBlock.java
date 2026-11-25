package computera.block;

import computera.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
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

public abstract class TerminalBlock extends Block implements EntityBlock {
    private final TerminalKind kind;

    protected TerminalBlock(Properties properties, TerminalKind kind) {
        super(properties);
        this.kind = kind;
    }

    public TerminalKind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide() && stack.is(computera.registry.ModItems.PAPER_TAPE.get())
                && level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            terminal.addTape(TerminalBlockEntity.LINES_PER_TAPE);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[Computera] paper tape loaded (" + terminal.tapeLines() + " lines)"));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            terminal.beginSession(player);
        }
        return InteractionResult.SUCCESS;
    }
}
