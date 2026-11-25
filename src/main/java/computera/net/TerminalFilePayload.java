package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TerminalFilePayload(BlockPos pos, String name, String source) implements CustomPacketPayload {
    public static final Type<TerminalFilePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "terminal_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalFilePayload::pos,
                    ByteBufCodecs.STRING_UTF8, TerminalFilePayload::name,
                    ByteBufCodecs.STRING_UTF8, TerminalFilePayload::source,
                    TerminalFilePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
