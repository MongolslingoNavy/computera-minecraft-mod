package computera.block;

import com.mojang.serialization.MapCodec;
import computera.blockentity.DisplayPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * A wall display. Panels placed next to each other with the same facing merge
 * into one screen; the merged wall shows the cluster status computed by its
 * controller block entity.
 */
public class DisplayPanelBlock extends BaseEntityBlock {
    public static final MapCodec<DisplayPanelBlock> CODEC = simpleCodec(DisplayPanelBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = Shapes.box(0, 0, 0.875, 1, 1, 1);
    private static final VoxelShape SOUTH_SHAPE = Shapes.box(0, 0, 0, 1, 1, 0.125);
    private static final VoxelShape EAST_SHAPE = Shapes.box(0, 0, 0, 0.125, 1, 1);
    private static final VoxelShape WEST_SHAPE = Shapes.box(0.875, 0, 0, 1, 1, 1);

    public DisplayPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal()
                ? clicked
                : context.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return this.canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, blockPos, blockState, blockEntity) -> {
            if (blockEntity instanceof DisplayPanelBlockEntity panel) {
                DisplayPanelBlockEntity.tick(lvl, blockPos, blockState, panel);
            }
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        DisplayPanelBlockEntity.refreshAround(level, pos);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            computera.cluster.ClusterManager.invalidate(serverLevel, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        DisplayPanelBlockEntity.refreshAround(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean movedByPiston) {
        for (Direction direction : Direction.values()) {
            DisplayPanelBlockEntity.refreshAround(level, pos.relative(direction));
        }
        computera.cluster.ClusterManager.invalidate(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel) {
            if (player.isShiftKeyDown()) {
                panel.openStatus(serverPlayer);
            } else {
                panel.openFor(serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (computera.util.WrenchSupport.isWrench(stack)) {
            return computera.util.WrenchSupport.handleBlockUse(stack, state, level, pos, player, hand, hitResult);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
