package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record TerminalLinesPayload(BlockPos pos, List<String> lines) implements CustomPacketPayload {
    public static final Type<TerminalLinesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "terminal_lines"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLinesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalLinesPayload::pos,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TerminalLinesPayload::lines,
                    TerminalLinesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
