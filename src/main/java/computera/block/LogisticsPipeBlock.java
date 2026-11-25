package computera.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class LogisticsPipeBlock extends CordBlock {
    public static final MapCodec<LogisticsPipeBlock> CODEC = simpleCodec(LogisticsPipeBlock::new);

    public LogisticsPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
