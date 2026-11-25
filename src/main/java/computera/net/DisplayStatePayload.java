package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server -> client: everything the display status screen needs, including the chosen mode. */
public record DisplayStatePayload(BlockPos pos, int wallW, int wallH, String mode,
                                  List<String> lines, List<String> items, List<String> devices)
        implements CustomPacketPayload {
    public static final Type<DisplayStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "display_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DisplayStatePayload::pos,
                    ByteBufCodecs.VAR_INT, DisplayStatePayload::wallW,
                    ByteBufCodecs.VAR_INT, DisplayStatePayload::wallH,
                    ByteBufCodecs.STRING_UTF8, DisplayStatePayload::mode,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DisplayStatePayload::lines,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DisplayStatePayload::items,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DisplayStatePayload::devices,
                    DisplayStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
