package computera.registry;

import computera.Computera;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Computera.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.computera"))
                    .icon(() -> new ItemStack(ModItems.CABINET.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CABINET.get());
                        output.accept(ModItems.CABLE.get());
                        output.accept(ModItems.TERMINAL_TELETYPE.get());
                        output.accept(ModItems.TERMINAL_CRT.get());
                        output.accept(ModItems.PUNCH_MACHINE.get());
                        output.accept(ModItems.LOGISTICS_PORT.get());
                        output.accept(ModItems.LOGISTICS_PIPE.get());
                        output.accept(ModItems.EU_ADAPTER.get());
                        output.accept(ModItems.DISPLAY_PANEL.get());
                        output.accept(ModItems.CRATE.get());
                        output.accept(ModItems.COMPUTE_CARD_1.get());
                        output.accept(ModItems.COMPUTE_CARD_2.get());
                        output.accept(ModItems.COMPUTE_CARD_3.get());
                        output.accept(ModItems.COMPUTE_CARD_4.get());
                        output.accept(ModItems.COMPUTE_CARD_5.get());
                        output.accept(ModItems.HARD_DRIVE.get());
                        output.accept(ModItems.COPPER_WIRE.get());
                        output.accept(ModItems.BLANK_CARD.get());
                        output.accept(ModItems.PUNCHED_CARD.get());
                        output.accept(ModItems.PAPER_TAPE.get());
                        output.accept(ModItems.FLASH_DRIVE.get());
                        output.accept(ModItems.NETWORK_CONNECTOR.get());
                        output.accept(ModItems.GUIDE_BOOK.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
