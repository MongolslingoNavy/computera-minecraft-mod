package computera.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import computera.block.DisplayPanelBlock;
import computera.blockentity.DisplayPanelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws the merged wall's status text. Only the controller panel submits
 * anything, and it spans the whole wall, so a 3x2 group of panels renders one
 * large screen instead of six small ones.
 */
public class DisplayPanelRenderer
        implements BlockEntityRenderer<DisplayPanelBlockEntity, DisplayPanelRenderState> {

    public DisplayPanelRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
    }

    private static final int LINE_HEIGHT = 9;
    private static final float HEADER_GAP = 3f;
    private static final float MIN_READABLE_SCALE = 0.055f;
    private static final int TITLE_COLOR = 0xFF7FF0C8;
    private static final int TEXT_COLOR = 0xFFB8E8C8;

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(DisplayPanelBlockEntity panel) {
        net.minecraft.core.BlockPos controller = panel.controllerPosition();
        int width = Math.max(1, panel.wallWidth()) - 1;
        int height = Math.max(1, panel.wallHeight()) - 1;
        net.minecraft.core.Direction facing = panel.getBlockState().getValue(DisplayPanelBlock.FACING);
        double minX = controller.getX();
        double minY = controller.getY();
        double minZ = controller.getZ();
        double maxX = controller.getX() + 1;
        double maxZ = controller.getZ() + 1;
        if (facing.getAxis() == net.minecraft.core.Direction.Axis.Z) {
            maxX += width;
        } else {
            maxZ += width;
        }
        return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, minY + 1 + height, maxZ).inflate(2.0);
    }

    @Override
    public DisplayPanelRenderState createRenderState() {
        return new DisplayPanelRenderState();
    }

    @Override
    public void extractRenderState(DisplayPanelBlockEntity panel, DisplayPanelRenderState state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(panel, state, partialTick, cameraPosition, breakProgress);
        state.controller = panel.isController();
        state.facing = panel.getBlockState().getValue(DisplayPanelBlock.FACING);
        state.wallW = panel.wallWidth();
        state.wallH = panel.wallHeight();
        state.controllerPos = panel.controllerPosition();
        state.lines = List.copyOf(panel.lines());
        state.items = List.copyOf(panel.itemEntries());
        state.devices = List.copyOf(panel.deviceEntries());
        state.mode = panel.mode();
    }

    @Override
    public void submit(DisplayPanelRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.controller) {
            return;
        }
        List<String> content = DisplayText.content(state.mode, state.lines, state.items, state.devices, true);
        if (content.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Direction facing = state.facing;
        int w = Math.max(1, state.wallW);
        int h = Math.max(1, state.wallH);

        String title = content.get(0);
        List<String> body = content.subList(1, content.size());
        int titleWidth = Math.max(1, font.width(title));
        int maxColumns = Math.max(1, Math.min(4, w / 3));
        int capacityPerColumn = Math.max(1, h);
        int widestBody = 0;
        for (String line : body) {
            widestBody = Math.max(widestBody, font.width(line));
        }
        widestBody = Math.max(1, widestBody);
        float widthBlocks = w - 0.25f;
        float heightBlocks = h - 0.25f;

        int columns = 1;
        int total = Math.min(body.size(), capacityPerColumn);
        int linesPerColumn = total;
        float scale = fitScale(widthBlocks, heightBlocks, titleWidth, 1, widestBody, linesPerColumn);
        for (int candidate = 2; candidate <= maxColumns; candidate++) {
            int shown = Math.min(body.size(), candidate * capacityPerColumn);
            int lines = shown <= 0 ? 0 : (shown + candidate - 1) / candidate;
            float fit = fitScale(widthBlocks, heightBlocks, titleWidth, candidate, widestBody, lines);
            if (fit >= MIN_READABLE_SCALE) {
                columns = candidate;
                total = shown;
                linesPerColumn = lines;
                scale = fit;
            }
        }
        float cap = 1f / 24f + Math.max(0, h - 4) * 0.0125f;
        scale = Math.min(scale, cap);

        float dx;
        float dy = h;
        float dz;
        float yaw;
        switch (facing) {
            case SOUTH -> {
                dx = 0;
                dz = 2f / 16f + 0.002f;
                yaw = 0;
            }
            case EAST -> {
                dx = 2f / 16f + 0.002f;
                dz = w;
                yaw = 90;
            }
            case WEST -> {
                dx = 14f / 16f - 0.002f;
                dz = 0;
                yaw = 270;
            }
            default -> {
                dx = w;
                dz = 14f / 16f - 0.002f;
                yaw = 180;
            }
        }

        float headerPx = LINE_HEIGHT + HEADER_GAP;
        float bodyTopPx = headerPx + Math.max(0,
                (heightBlocks / scale - headerPx - linesPerColumn * (float) LINE_HEIGHT) / 2f);
        float columnWidthPx = widthBlocks / scale / columns;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.scale(scale, -scale, scale);
        collector.submitText(poseStack, Math.max(0, (columnWidthPx * columns - titleWidth) / 2f), 0,
                FormattedCharSequence.forward(title, net.minecraft.network.chat.Style.EMPTY),
                false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, TITLE_COLOR, 0, 0);
        for (int column = 0; column < columns; column++) {
            int from = column * linesPerColumn;
            int to = Math.min(total, from + linesPerColumn);
            if (from >= to) {
                break;
            }
            float offsetX = column * columnWidthPx + Math.max(0, (columnWidthPx - widestBody) / 2f);
            float offsetY = bodyTopPx;
            for (int i = from; i < to; i++) {
                FormattedCharSequence text = FormattedCharSequence.forward(body.get(i),
                        net.minecraft.network.chat.Style.EMPTY);
                collector.submitText(poseStack, offsetX, offsetY, text, false,
                        Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, TEXT_COLOR, 0, 0);
                offsetY += LINE_HEIGHT;
            }
        }
        poseStack.popPose();
    }

    private static float fitScale(float widthBlocks, float heightBlocks, int titleWidth,
                                  int columns, int widest, int lines) {
        float width = widthBlocks / Math.max((float) titleWidth, columns * (float) widest);
        float height = heightBlocks / ((1 + lines) * (float) LINE_HEIGHT + HEADER_GAP);
        return Math.min(width, height);
    }
}
