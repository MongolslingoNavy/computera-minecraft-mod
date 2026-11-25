package computera.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class TeletypeTerminalBlock extends TerminalBlock {
    public static final MapCodec<TeletypeTerminalBlock> CODEC = simpleCodec(TeletypeTerminalBlock::new);

    public TeletypeTerminalBlock(Properties properties) {
        super(properties, TerminalKind.TELETYPE);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
