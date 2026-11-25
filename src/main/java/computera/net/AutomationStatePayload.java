package computera.net;

import computera.Computera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record AutomationStatePayload(BlockPos pos, List<String> rules, List<String> items)
        implements CustomPacketPayload {
    public static final Type<AutomationStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Computera.MODID, "automation_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutomationStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AutomationStatePayload::pos,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AutomationStatePayload::rules,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AutomationStatePayload::items,
                    AutomationStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
