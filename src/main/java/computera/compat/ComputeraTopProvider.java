package computera.compat;

import computera.Computera;
import java.util.function.Function;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Optional The One Probe provider displaying live telemetry for Computera hardware blocks.
 */
public final class ComputeraTopProvider implements IProbeInfoProvider {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(Computera.MODID, "device");

    /** IMC callback supplier consumed by The One Probe when {@code theoneprobe} is loaded. */
    public static Function<ITheOneProbe, Void> createCallback() {
        return probe -> {
            probe.registerProvider(new ComputeraTopProvider());
            return null;
        };
    }

    @Override
    public Identifier getID() {
        return ID;
    }

    @Override
    public void addProbeInfo(
            ProbeMode mode,
            IProbeInfo info,
            Player player,
            Level level,
            BlockState state,
            IProbeHitData data
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var blockEntity = serverLevel.getBlockEntity(data.getPos());
        if (blockEntity == null) {
            return;
        }
        for (Component line : ComputeraProbeHelper.buildDirectLines(serverLevel, blockEntity)) {
            info.mcText(line);
        }
    }
}
