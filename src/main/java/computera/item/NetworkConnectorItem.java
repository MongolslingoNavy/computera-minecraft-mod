package computera.item;

import computera.blockentity.CabinetBlockEntity;
import computera.registry.ModDataComponents;
import computera.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;

public class NetworkConnectorItem extends Item {
    public NetworkConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(clicked);
        Map<String, String> data = new HashMap<>(context.getItemInHand()
                .getOrDefault(ModDataComponents.FILES.get(), Map.of()));

        if (blockEntity instanceof CabinetBlockEntity cabinet) {
            data.put("hub", encode(clicked));
            data.put("dimension", level.dimension().identifier().toString());
            context.getItemInHand().set(ModDataComponents.FILES.get(), Map.copyOf(data));
            message(context, "hub set to " + clicked.toShortString());
            return InteractionResult.SUCCESS;
        }

        String hub = data.get("hub");
        if (hub == null) {
            message(context, "right-click a cabinet first to set the hub");
            return InteractionResult.SUCCESS;
        }
        if (!level.dimension().identifier().toString().equals(data.get("dimension"))) {
            message(context, "set the hub again in this dimension");
            return InteractionResult.SUCCESS;
        }
        BlockPos hubPos = decode(hub);
        if (hubPos == null || !level.hasChunkAt(hubPos)) {
            message(context, "hub is invalid or not loaded");
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(hubPos) instanceof CabinetBlockEntity cabinet) {
            if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
                if (cabinet.removeWireless(clicked)) {
                    if (context.getPlayer() != null) {
                        context.getPlayer().sendSystemMessage(Component.translatable(
                                "computera.msg.wireless_unlinked", clicked.toShortString(), hubPos.toShortString()));
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            net.minecraft.core.Direction clickedFace = context.getClickedFace();
            net.minecraft.core.Direction accessFace = null;
            if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, clicked, clickedFace) != null) {
                accessFace = clickedFace;
            } else if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, clicked, null) == null) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendSystemMessage(Component.translatable("computera.msg.wireless_not_container"));
                }
                return InteractionResult.SUCCESS;
            }
            if (!cabinet.addWireless(clicked, accessFace)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendSystemMessage(Component.translatable(
                            "computera.msg.wireless_full", CabinetBlockEntity.MAX_WIRELESS));
                }
                return InteractionResult.SUCCESS;
            }
            message(context, "linked " + clicked.toShortString() + " to hub " + hubPos.toShortString());
        } else {
            message(context, "hub cabinet is gone, set a new hub");
        }
        return InteractionResult.SUCCESS;
    }

    private static String encode(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos decode(String text) {
        String[] parts = text.split(",");
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void message(UseOnContext context, String text) {
        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.literal("[Computera] " + text));
        }
    }
}
