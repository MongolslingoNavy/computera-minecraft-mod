package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record LogisticsStatePayload(BlockPos pos, List<String> devices, String selectedKey,
                                    int page, List<String> slots, String carried)
        implements CustomPacketPayload {
    public static final Type<LogisticsStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "logistics_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, LogisticsStatePayload::pos,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LogisticsStatePayload::devices,
                    ByteBufCodecs.STRING_UTF8, LogisticsStatePayload::selectedKey,
                    ByteBufCodecs.VAR_INT, LogisticsStatePayload::page,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LogisticsStatePayload::slots,
                    ByteBufCodecs.STRING_UTF8, LogisticsStatePayload::carried,
                    LogisticsStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
