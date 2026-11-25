package computera.blockentity;

import computera.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class LogisticsPortBlockEntity extends BlockEntity {
    @Nullable
    private String filter;

    public LogisticsPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_PORT.get(), pos, state);
    }

    @Nullable
    public String filter() {
        return filter;
    }

    public void setFilter(@Nullable String filter) {
        this.filter = filter;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (filter != null) {
            output.putString("Filter", filter);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String value = input.getStringOr("Filter", "");
        filter = value.isEmpty() ? null : value;
    }
}
