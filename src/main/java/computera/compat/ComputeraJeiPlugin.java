package computera.compat;

import computera.Computera;
import computera.compat.ComputeraInfoCatalog.InfoEntry;
import computera.compat.ComputeraInfoCatalog.PunchRecipe;
import computera.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Optional JEI plugin exposing Computera hardware documentation pages and the Card Punch
 * program-writing recipe category. Loaded exclusively by JEI when JEI is installed.
 */
@JeiPlugin
public final class ComputeraJeiPlugin implements IModPlugin {

    private static final Identifier PLUGIN_UID = Identifier.fromNamespaceAndPath(Computera.MODID, "jei_plugin");
    private static final IRecipeType<PunchRecipe> CARD_PUNCH_TYPE =
            IRecipeType.create(Computera.MODID, "card_punch", PunchRecipe.class);

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CardPunchCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CARD_PUNCH_TYPE, ComputeraInfoCatalog.punchRecipes());
        for (InfoEntry entry : ComputeraInfoCatalog.infoEntries()) {
            registration.addIngredientInfo(
                    entry.stacks(),
                    VanillaTypes.ITEM_STACK,
                    Component.translatable(entry.translationKey())
            );
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CARD_PUNCH_TYPE, new ItemStack(ModItems.PUNCH_MACHINE.get()));
    }

    private static final class CardPunchCategory implements IRecipeCategory<PunchRecipe> {
        private static final int WIDTH = 160;
        private static final int HEIGHT = 48;

        private final IDrawable icon;

        CardPunchCategory(IGuiHelper gui) {
            this.icon = gui.createDrawableItemStack(new ItemStack(ModItems.PUNCH_MACHINE.get()));
        }

        @Override
        public IRecipeType<PunchRecipe> getRecipeType() {
            return CARD_PUNCH_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.computera.category.card_punch");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, PunchRecipe recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 28, 8)
                    .setStandardSlotBackground()
                    .add(recipe.input());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 114, 8)
                    .setStandardSlotBackground()
                    .add(recipe.output());
        }

        @Override
        public void draw(
                PunchRecipe recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY
        ) {
            var font = Minecraft.getInstance().font;
            graphics.text(font, "->", 74, 12, 0xFF404040, false);
            Component note = Component.translatable(recipe.noteKey());
            int x = Math.max(4, (WIDTH - font.width(note)) / 2);
            graphics.text(font, note, x, 34, 0xFF555555, false);
        }
    }
}
