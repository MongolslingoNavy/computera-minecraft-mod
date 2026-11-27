package computera.blockentity;

import computera.registry.ModBlockEntities;
import computera.util.ItemFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
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
        if (filter == null || filter.isBlank()) {
            this.filter = null;
        } else {
            String normalized = ItemFilter.normalize(filter);
            this.filter = normalized != null ? normalized : filter.trim();
        }
        setChanged();
    }

    public boolean matches(ItemResource resource) {
        return ItemFilter.matches(filter, resource);
    }

    public boolean matches(ItemStack stack) {
        return ItemFilter.matches(filter, stack);
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
