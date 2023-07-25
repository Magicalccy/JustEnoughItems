package mezz.jei.library.util;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformIngredientHelper;
import mezz.jei.common.platform.IPlatformRegistry;
import mezz.jei.common.platform.Services;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.library.ingredients.IngredientSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class BrewingRecipeMakerCommon {
    private static final Logger LOGGER = LogManager.getLogger();

    public static Set<IJeiBrewingRecipe> getVanillaBrewingRecipes(
        IVanillaRecipeFactory recipeFactory,
        IIngredientManager ingredientManager,
        IVanillaPotionOutputSupplier vanillaOutputSupplier
    ) {
        Set<IJeiBrewingRecipe> recipes = new HashSet<>();
        IPlatformRegistry<Potion> potionRegistry = Services.PLATFORM.getRegistry(Registries.POTION);
        IngredientSet<ItemStack> knownPotions = getBaseKnownPotions(ingredientManager, potionRegistry);

        List<ItemStack> potionReagents = ingredientManager.getAllItemStacks().stream()
            .filter(BrewingRecipeMakerCommon::isIngredient)
            .toList();

        boolean foundNewPotions;
        do {
            NewPotionsParams params = new NewPotionsParams(
                    recipeFactory,
                    potionRegistry,
                    knownPotions,
                    potionReagents,
                    vanillaOutputSupplier,
                    recipes
            );
            List<ItemStack> newPotions = getNewPotions(params);
            foundNewPotions = !newPotions.isEmpty();
            knownPotions.addAll(newPotions);
        } while (foundNewPotions);

        return recipes;
    }

    private static boolean isIngredient(ItemStack itemStack) {
        try {
            return PotionBrewing.isIngredient(itemStack);
        } catch (RuntimeException | LinkageError e) {
            String itemStackInfo = ErrorUtil.getItemStackInfo(itemStack);
            LOGGER.error("Failed to check if item is a potion reagent {}.", itemStackInfo, e);
            return false;
        }
    }

    private static IngredientSet<ItemStack> getBaseKnownPotions(IIngredientManager ingredientManager, IPlatformRegistry<Potion> potionRegistry) {
        IPlatformIngredientHelper ingredientHelper = Services.PLATFORM.getIngredientHelper();
        List<ItemStack> potionContainers = ingredientHelper.getPotionContainers().stream()
            .flatMap(potionItem -> Arrays.stream(potionItem.getItems()))
            .toList();

        IIngredientHelper<ItemStack> itemStackHelper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        IngredientSet<ItemStack> knownPotions = IngredientSet.create(itemStackHelper, UidContext.Ingredient);

        potionRegistry.getValues()
            .filter(potion -> potion != Potions.EMPTY) // skip the "un-craft-able" vanilla potions
            .forEach(potion -> {
                for (ItemStack potionContainer : potionContainers) {
                    ItemStack result = PotionUtils.setPotion(potionContainer.copy(), potion);
                    knownPotions.add(result);
                }
            });
        return knownPotions;
    }

    private static List<ItemStack> getNewPotions(NewPotionsParams params) {
        List<ItemStack> newPotions = new ArrayList<>();
        for (ItemStack potionInput : params.knownPotions) {
            for (ItemStack potionReagent : params.potionReagents) {
                ItemStack potionOutput = params.vanillaOutputSupplier.getOutput(potionInput.copy(), potionReagent);
                if (potionOutput.isEmpty()) {
                    continue;
                }

                if (potionInput.getItem() == potionOutput.getItem()) {
                    Potion potionOutputType = PotionUtils.getPotion(potionOutput);
                    if (potionOutputType == Potions.WATER) {
                        continue;
                    }

                    Potion potionInputType = PotionUtils.getPotion(potionInput);
                    Optional<ResourceLocation> inputId = params.potionRegistry.getRegistryName(potionInputType);
                    Optional<ResourceLocation> outputId = params.potionRegistry.getRegistryName(potionOutputType);

                    if (!Objects.equals(inputId, outputId)) {
                        if (params.knownPotions.contains(potionOutput)) {
                            continue;
                        }
                        newPotions.add(potionOutput);
                        params.recipes.add(params.recipeFactory.createBrewingRecipe(Collections.singletonList(potionInput), potionReagent, potionOutput));
                    }
                }
            }
        }
        return newPotions;
    }

    @FunctionalInterface
    public interface IVanillaPotionOutputSupplier {
        ItemStack getOutput(ItemStack input, ItemStack ingredient);
    }
}


class NewPotionsParams {
    public IVanillaRecipeFactory recipeFactory;
    public IPlatformRegistry<Potion> potionRegistry;
    public IngredientSet<ItemStack> knownPotions;
    public List<ItemStack> potionReagents;
    public BrewingRecipeMakerCommon.IVanillaPotionOutputSupplier vanillaOutputSupplier;
    public Set<IJeiBrewingRecipe> recipes;

    public NewPotionsParams(
            IVanillaRecipeFactory recipeFactory,
            IPlatformRegistry<Potion> potionRegistry,
            IngredientSet<ItemStack> knownPotions,
            List<ItemStack> potionReagents,
            BrewingRecipeMakerCommon.IVanillaPotionOutputSupplier vanillaOutputSupplier,
            Set<IJeiBrewingRecipe> recipes
    ) {
        this.recipeFactory = recipeFactory;
        this.potionRegistry = potionRegistry;
        this.knownPotions = knownPotions;
        this.potionReagents = potionReagents;
        this.vanillaOutputSupplier = vanillaOutputSupplier;
        this.recipes = recipes;
    }
}
