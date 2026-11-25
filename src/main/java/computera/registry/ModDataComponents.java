package computera.registry;

import com.mojang.serialization.Codec;
import computera.Computera;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Computera.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, String>>> FILES =
            DATA_COMPONENTS.registerComponentType("files",
                    builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

    private ModDataComponents() {
    }
}
