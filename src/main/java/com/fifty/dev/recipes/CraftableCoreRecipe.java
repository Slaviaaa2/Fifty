package com.fifty.dev.recipes;

import com.fifty.dev.api.CustomRecipe;
import com.fifty.dev.items.CraftableCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;

public final class CraftableCoreRecipe extends CustomRecipe {
    @Override
    public String getRecipeId() {
        return "CRAFTABLE_CORE";
    }

    @Override
    protected Recipe createRecipe(NamespacedKey key) {
        var recipe = shapedRecipe(CraftableCore.class);

        recipe.shape(
                "ISI",
                "SGS",
                "ISI"
        );

        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('G', Material.GOLD_INGOT);

        return recipe;
    }
}
