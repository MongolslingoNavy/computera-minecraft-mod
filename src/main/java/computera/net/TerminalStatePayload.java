package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record TerminalStatePayload(BlockPos pos, String kind, int threads, int used,
                                   boolean running, String runningFile,
                                   List<String> output, List<String> files) implements CustomPacketPayload {
    public static final Type<TerminalStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "terminal_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalStatePayload::pos,
                    ByteBufCodecs.STRING_UTF8, TerminalStatePayload::kind,
                    ByteBufCodecs.VAR_INT, TerminalStatePayload::threads,
                    ByteBufCodecs.VAR_INT, TerminalStatePayload::used,
                    ByteBufCodecs.BOOL, TerminalStatePayload::running,
                    ByteBufCodecs.STRING_UTF8, TerminalStatePayload::runningFile,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TerminalStatePayload::output,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TerminalStatePayload::files,
                    TerminalStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
