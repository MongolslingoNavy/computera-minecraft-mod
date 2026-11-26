package computera;

import computera.net.TerminalFilePayload;
import computera.net.TerminalLinesPayload;
import computera.net.TerminalStatePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@Mod(value = Computera.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Computera.MODID, value = Dist.CLIENT)
public class ComputeraModClient {
    public ComputeraModClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        computera.item.GuideBookItem.setClientOpener(computera.client.GuideBookScreen::open);
        Computera.LOGGER.info("Computera client setup");
    }

    @SubscribeEvent
    static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TerminalStatePayload.TYPE, computera.client.TerminalClientHandler::handleState);
        event.register(TerminalLinesPayload.TYPE, computera.client.TerminalClientHandler::handleLines);
        event.register(TerminalFilePayload.TYPE, computera.client.TerminalClientHandler::handleFile);
        event.register(computera.net.LogisticsStatePayload.TYPE,
                computera.client.TerminalClientHandler::handleLogistics);
        event.register(computera.net.AutomationStatePayload.TYPE,
                computera.client.TerminalClientHandler::handleAutomation);
        event.register(computera.net.DisplayStatePayload.TYPE,
                computera.client.TerminalClientHandler::handleDisplay);
        event.register(computera.net.DisplayListPayload.TYPE,
                computera.client.TerminalClientHandler::handleDisplayList);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(computera.registry.ModBlockEntities.DISPLAY_PANEL.get(),
                computera.client.DisplayPanelRenderer::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(computera.registry.ModMenus.CABINET.get(), computera.client.CabinetScreen::new);
        event.register(computera.registry.ModMenus.CRATE.get(), computera.client.CrateScreen::new);
    }
}
