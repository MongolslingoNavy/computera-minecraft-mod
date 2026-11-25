package computera.menu;

import computera.blockentity.TerminalBlockEntity;
import computera.cluster.ClusterManager;
import computera.device.DeviceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.WeakHashMap;

public final class RemoteMenus {
    private record Session(ServerPlayer player, TerminalBlockEntity terminal, BlockEntity target) {}

    private static final Map<AbstractContainerMenu, Session> SESSIONS = new WeakHashMap<>();

    public static void register(ServerPlayer player, TerminalBlockEntity terminal, BlockEntity target) {
        SESSIONS.put(player.containerMenu, new Session(player, terminal, target));
    }

    public static boolean stillValid(AbstractContainerMenu menu, Player player) {
        Session session = SESSIONS.get(menu);
        if (session == null) {
            return menu.stillValid(player);
        }
        if (player != session.player || player.containerMenu != menu || !session.terminal.canAccess(session.player)
                || session.target.isRemoved() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos pos = session.target.getBlockPos();
        if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != session.target) {
            return false;
        }
        var cluster = ClusterManager.clusterFor(level, session.terminal.getBlockPos());
        return cluster != null && DeviceRegistry.positions(level, cluster).containsValue(pos);
    }

    private RemoteMenus() {}
}
