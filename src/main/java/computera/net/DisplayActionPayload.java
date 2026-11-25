package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: refresh one display wall and push a new state payload back. */
public record DisplayActionPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<DisplayActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "display_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayActionPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, DisplayActionPayload::pos, DisplayActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
