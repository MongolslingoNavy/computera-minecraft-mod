package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TerminalActionPayload(BlockPos pos, String action, String name, String data)
        implements CustomPacketPayload {
    public static final Type<TerminalActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "terminal_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalActionPayload::pos,
                    ByteBufCodecs.STRING_UTF8, TerminalActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, TerminalActionPayload::name,
                    ByteBufCodecs.STRING_UTF8, TerminalActionPayload::data,
                    TerminalActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
