package computera.menu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import computera.blockentity.TerminalBlockEntity;
import computera.cluster.ClusterManager;
import computera.device.DeviceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public final class RemoteMenus {
    private static final class Session {
        final ServerPlayer player;
        final TerminalBlockEntity terminal;
        final BlockEntity target;
        @Nullable
        final BlockPos pairedPos;
        @Nullable
        final BlockEntity pairedTarget;
        long validatedGeneration = Long.MIN_VALUE;
        boolean cachedConnected;

        Session(ServerPlayer player, TerminalBlockEntity terminal, BlockEntity target) {
            this.player = player;
            this.terminal = terminal;
            this.target = target;
            BlockPos pairPos = null;
            BlockEntity pairBe = null;
            if (target.getLevel() instanceof ServerLevel serverLevel) {
                BlockState state = target.getBlockState();
                if (state.getBlock() instanceof ChestBlock
                        && state.hasProperty(ChestBlock.TYPE)
                        && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos other = target.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
                    if (serverLevel.hasChunkAt(other)) {
                        pairPos = other.immutable();
                        pairBe = serverLevel.getBlockEntity(other);
                    }
                }
            }
            this.pairedPos = pairPos;
            this.pairedTarget = pairBe;
        }
    }

    private static final Map<AbstractContainerMenu, Session> SESSIONS = new WeakHashMap<>();

    public static void register(ServerPlayer player, TerminalBlockEntity terminal, BlockEntity target) {
        if (target != null) {
            SESSIONS.put(player.containerMenu, new Session(player, terminal, target));
        }
    }

    public static void onLevelUnload(ServerLevel level) {
        SESSIONS.values().removeIf(session -> session.target.getLevel() == level || session.terminal.getLevel() == level);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    public static boolean stillValid(AbstractContainerMenu menu, Player player) {
        return stillValid(menu, player, args -> menu.stillValid(player));
    }

    public static boolean stillValid(AbstractContainerMenu menu, Player player, Operation<Boolean> original) {
        Session session = SESSIONS.get(menu);
        if (session == null) {
            return original.call(menu, player);
        }
        if (player != session.player || player.containerMenu != menu
                || !session.terminal.canAccess(session.player)
                || session.target.isRemoved()
                || !(player.level() instanceof ServerLevel level)
                || session.target.getLevel() != level) {
            SESSIONS.remove(menu);
            return false;
        }
        BlockPos pos = session.target.getBlockPos();
        if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != session.target) {
            SESSIONS.remove(menu);
            return false;
        }
        if (session.pairedPos != null) {
            if (session.pairedTarget == null || session.pairedTarget.isRemoved()
                    || !level.hasChunkAt(session.pairedPos)
                    || level.getBlockEntity(session.pairedPos) != session.pairedTarget) {
                SESSIONS.remove(menu);
                return false;
            }
        }
        long gen = ClusterManager.generation(level);
        if (session.validatedGeneration != gen) {
            var cluster = ClusterManager.clusterFor(level, session.terminal.getBlockPos());
            session.cachedConnected = cluster != null && DeviceRegistry.positions(level, cluster).containsValue(pos);
            session.validatedGeneration = gen;
        }
        if (!session.cachedConnected) {
            SESSIONS.remove(menu);
            return false;
        }
        return true;
    }

    private RemoteMenus() {}
}
