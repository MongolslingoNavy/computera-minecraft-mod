package computera.net;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ComputeraNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TerminalStatePayload.TYPE, TerminalStatePayload.STREAM_CODEC);
        registrar.playToClient(TerminalLinesPayload.TYPE, TerminalLinesPayload.STREAM_CODEC);
        registrar.playToClient(TerminalFilePayload.TYPE, TerminalFilePayload.STREAM_CODEC);
        registrar.playToClient(LogisticsStatePayload.TYPE, LogisticsStatePayload.STREAM_CODEC);
        registrar.playToClient(AutomationStatePayload.TYPE, AutomationStatePayload.STREAM_CODEC);
        registrar.playToClient(DisplayStatePayload.TYPE, DisplayStatePayload.STREAM_CODEC);
        registrar.playToClient(DisplayListPayload.TYPE, DisplayListPayload.STREAM_CODEC);
        registrar.playToServer(TerminalActionPayload.TYPE, TerminalActionPayload.STREAM_CODEC,
                ComputeraNetwork::handleAction);
        registrar.playToServer(DisplayActionPayload.TYPE, DisplayActionPayload.STREAM_CODEC,
                ComputeraNetwork::handleDisplayAction);
    }

    private static void handleAction(TerminalActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().hasChunkAt(payload.pos())) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
        if (blockEntity instanceof computera.blockentity.TerminalBlockEntity terminal) {
            terminal.handleAction(player, payload.action(), payload.name(), payload.data());
        } else {
            PacketDistributor.sendToPlayer(player, new TerminalLinesPayload(payload.pos(),
                    java.util.List.of("[Computera] terminal not found")));
        }
    }

    private static void handleDisplayAction(DisplayActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isAlive() || !player.level().hasChunkAt(payload.pos())
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) > 64.0) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos())
                instanceof computera.blockentity.DisplayPanelBlockEntity panel) {
            panel.openStatus(player);
        }
    }

    private ComputeraNetwork() {
    }
}
