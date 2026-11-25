package computera.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

/**
 * Shared behaviour for the data cable and the logistics pipe: six boolean arms
 * that are recomputed whenever a neighbour changes, the block is placed, or a
 * chunk is loaded. Keeping the recompute in one place is what stops a connection
 * from being drawn on one side only.
 */
public abstract class CordBlock extends Block {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    protected CordBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    public static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Connections.withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        if (!level.hasChunkAt(neighbourPos)) {
            return state;
        }
        return state.setValue(property(direction), Connections.canConnect(neighbourState));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        refresh(level, pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        refresh(level, pos, state);
    }

    /**
     * Writes the recomputed arms back without triggering another neighbour pass;
     * every cord receives its own refresh, so cascading updates are not needed.
     */
    public static boolean refresh(Level level, BlockPos pos, BlockState state) {
        BlockState updated = Connections.withConnections(level, pos, state);
        if (updated == state) {
            return false;
        }
        level.setBlock(pos, updated, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        return true;
    }
}
