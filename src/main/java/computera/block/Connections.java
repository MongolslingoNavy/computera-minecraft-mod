package computera.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class Connections {
    /**
     * A cord connects to other cords, to our own machines and terminals, and to any
     * block that carries a block entity (chests, furnaces, other mods' machines).
     */
    public static boolean canConnect(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof CordBlock
                || block instanceof CabinetBlock
                || block instanceof TerminalBlock
                || block instanceof LogisticsPortBlock
                || block instanceof EuAdapterBlock
                || block instanceof DisplayPanelBlock
                || block instanceof PunchMachineBlock
                || state.hasBlockEntity();
    }

    /**
     * Recomputes every arm of a cord block. Sides that face a chunk which is not
     * loaded keep their previous value so a chunk border never wipes a connection.
     */
    public static BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.hasChunkAt(neighbour)) {
                continue;
            }
            result = result.setValue(CordBlock.property(direction),
                    canConnect(level.getBlockState(neighbour)));
        }
        return result;
    }

    private Connections() {
    }
}
