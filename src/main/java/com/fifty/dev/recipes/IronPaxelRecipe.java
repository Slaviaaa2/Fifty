package com.fifty.dev.recipes;

import com.fifty.dev.api.CustomRecipe;
import com.fifty.dev.items.CraftableCore;
import com.fifty.dev.items.IronPaxel;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;

public final class IronPaxelRecipe extends CustomRecipe {
    @Override
    public String getRecipeId() {
        return "IRON_PAXEL";
    }

    @Override
    protected Recipe createRecipe(NamespacedKey key) {
        var recipe = shapedRecipe(IronPaxel.class);

        recipe.shape(
                "PAS",
                " C ",
                " I "
        );

        recipe.setIngredient('P', Material.IRON_PICKAXE);
        recipe.setIngredient('A', Material.IRON_AXE);
        recipe.setIngredient('S', Material.IRON_SHOVEL);
        recipe.setIngredient('C', customItemStack(CraftableCore.class));
        recipe.setIngredient('I', Material.STICK);

        return recipe;
    }
}
