package computera.registry;

import computera.Computera;
import computera.menu.CabinetMenu;
import computera.menu.CrateMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Computera.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CabinetMenu>> CABINET = MENUS.register("cabinet",
            () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new CabinetMenu(id, inventory, buffer.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<CrateMenu>> CRATE = MENUS.register("crate",
            () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new CrateMenu(id, inventory, buffer.readBlockPos())));

    private ModMenus() {
    }
}
