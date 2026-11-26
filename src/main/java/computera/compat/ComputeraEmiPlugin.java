package computera.compat;

import computera.Computera;
import computera.compat.ComputeraInfoCatalog.InfoEntry;
import computera.compat.ComputeraInfoCatalog.PunchRecipe;
import computera.registry.ModItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Optional EMI plugin exposing Computera hardware info pages and the Card Punch recipe category.
 * Loaded exclusively by EMI when EMI is installed.
 */
@EmiEntrypoint
public final class ComputeraEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory cardPunchCategory = new EmiRecipeCategory(
                Identifier.fromNamespaceAndPath(Computera.MODID, "card_punch"),
                EmiStack.of(ModItems.PUNCH_MACHINE.get())
        );
        registry.addCategory(cardPunchCategory);
        registry.addWorkstation(cardPunchCategory, EmiStack.of(ModItems.PUNCH_MACHINE.get()));
        for (PunchRecipe recipe : ComputeraInfoCatalog.punchRecipes()) {
            registry.addRecipe(new CardPunchEmiRecipe(cardPunchCategory, recipe));
        }

        for (InfoEntry entry : ComputeraInfoCatalog.infoEntries()) {
            List<EmiStack> stacks = entry.stacks().stream().map(EmiStack::of).toList();
            registry.addRecipe(new EmiInfoRecipe(
                    List.copyOf(stacks),
                    List.of(Component.translatable(entry.translationKey())),
                    Identifier.fromNamespaceAndPath(Computera.MODID, "/info/" + entry.id())
            ));
        }
    }

    private static final class CardPunchEmiRecipe extends BasicEmiRecipe {
        private final String noteKey;

        CardPunchEmiRecipe(EmiRecipeCategory category, PunchRecipe recipe) {
            super(
                    category,
                    Identifier.fromNamespaceAndPath(Computera.MODID, "/card_punch/" + recipe.id()),
                    160,
                    48
            );
            this.inputs.add(EmiStack.of(recipe.input()));
            this.outputs.add(EmiStack.of(recipe.output()));
            this.noteKey = recipe.noteKey();
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 28, 8);
            widgets.addFillingArrow(68, 8, 3000);
            widgets.addSlot(outputs.get(0), 114, 8).recipeContext(this);
            widgets.addText(Component.translatable(noteKey), 8, 34, 0xFF555555, false);
        }
    }
}
