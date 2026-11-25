package computera.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

public class DisplayPanelRenderState extends BlockEntityRenderState {
    public boolean controller;
    public Direction facing = Direction.NORTH;
    public int wallW = 1;
    public int wallH = 1;
    public BlockPos controllerPos = BlockPos.ZERO;
    public List<String> lines = List.of();
    public List<String> items = List.of();
    public List<String> devices = List.of();
    public computera.blockentity.DisplayMode mode = computera.blockentity.DisplayMode.OVERVIEW;
}
