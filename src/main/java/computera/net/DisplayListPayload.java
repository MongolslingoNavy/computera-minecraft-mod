package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server -> client: the display walls linked to a terminal, one "x,y,z|w|h|MODE" per wall. */
public record DisplayListPayload(BlockPos pos, List<String> walls) implements CustomPacketPayload {
    public static final Type<DisplayListPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "display_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DisplayListPayload::pos,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DisplayListPayload::walls,
                    DisplayListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
