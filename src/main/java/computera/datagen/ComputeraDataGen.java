package computera.datagen;

import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Registers Computera's NeoForge data providers for both client and server data generation runs.
 */
public final class ComputeraDataGen {

    private ComputeraDataGen() {
    }

    public static void gatherClient(GatherDataEvent.Client event) {
        registerProviders(event);
    }

    public static void gatherServer(GatherDataEvent.Server event) {
        registerProviders(event);
    }

    private static void registerProviders(GatherDataEvent event) {
        event.createProvider(ComputeraBlockTagsProvider::new);
        event.createProvider(ComputeraItemTagsProvider::new);
    }
}
