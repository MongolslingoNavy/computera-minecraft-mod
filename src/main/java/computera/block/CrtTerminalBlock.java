package computera.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class CrtTerminalBlock extends TerminalBlock {
    public static final MapCodec<CrtTerminalBlock> CODEC = simpleCodec(CrtTerminalBlock::new);

    public CrtTerminalBlock(Properties properties) {
        super(properties, TerminalKind.CRT);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
