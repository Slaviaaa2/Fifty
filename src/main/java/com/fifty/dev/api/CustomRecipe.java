package com.fifty.dev.api;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for recipes that are discovered and registered automatically.
 */
public abstract class CustomRecipe {
    private NamespacedKey key;
    private Recipe recipe;

    /**
     * Returns the ID used for this recipe's namespaced key.
     */
    public abstract String getRecipeId();

    /**
     * Creates the Bukkit recipe with the key assigned by the factory.
     */
    protected abstract Recipe createRecipe(NamespacedKey key);

    public final NamespacedKey getKey() {
        EnsureRegistered();
        return key;
    }

    public final Recipe getRecipe() {
        EnsureRegistered();
        return recipe;
    }

    public final <T extends Recipe> T getRecipe(Class<T> type) {
        EnsureRegistered();
        return type.cast(recipe);
    }

    protected final NamespacedKey recipeKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "Recipe key is only available while building or after registration: " +
                            getClass().getName()
            );
        }
        return key;
    }

    protected final CustomItem customItem(String id) {
        CustomItem item = CustomItemFactory.Provide(id);
        if (item == null) {
            throw new IllegalStateException(
                    "Unknown CustomItem ID '" + id + "' used by " +
                            getClass().getName()
            );
        }
        return item;
    }

    protected final <T extends CustomItem> T customItem(Class<T> type) {
        T item = CustomItemFactory.Provide(type);
        if (item == null) {
            throw new IllegalStateException(
                    "Unregistered CustomItem type '" + type.getName() +
                            "' used by " + getClass().getName()
            );
        }
        return item;
    }

    protected final ItemStack customItemStack(String id) {
        return customItem(id).createItemStack();
    }

    protected final ItemStack customItemStack(String id, int amount) {
        return customItem(id).createItemStack(amount);
    }

    protected final <T extends CustomItem> ItemStack customItemStack(Class<T> type) {
        return customItem(type).createItemStack();
    }

    protected final <T extends CustomItem> ItemStack customItemStack(Class<T> type, int amount) {
        return customItem(type).createItemStack(amount);
    }

    protected final RecipeChoice.ExactChoice customItemChoice(String... ids) {
        if (ids == null || ids.length == 0) {
            throw new IllegalArgumentException("At least one CustomItem ID is required");
        }

        List<ItemStack> choices = new ArrayList<>(ids.length);
        for (String id : ids) {
            choices.add(customItemStack(id));
        }
        return RecipeChoice.exactChoice(choices);
    }

    @SafeVarargs
    protected final RecipeChoice.ExactChoice customItemChoice(
            Class<? extends CustomItem>... types
    ) {
        if (types == null || types.length == 0) {
            throw new IllegalArgumentException("At least one CustomItem type is required");
        }

        List<ItemStack> choices = new ArrayList<>(types.length);
        for (Class<? extends CustomItem> type : types) {
            choices.add(customItemStack(type));
        }
        return RecipeChoice.exactChoice(choices);
    }

    protected final RecipeChoice materialChoice(
            Material first,
            Material... others
    ) {
        if (first == null || first.asItemType() == null) {
            throw new IllegalArgumentException("Material must be a valid item");
        }

        var otherTypes = new org.bukkit.inventory.ItemType[others.length];
        for (int i = 0; i < others.length; i++) {
            if (others[i] == null || others[i].asItemType() == null) {
                throw new IllegalArgumentException("Material must be a valid item");
            }
            otherTypes[i] = others[i].asItemType();
        }
        return RecipeChoice.itemType(first.asItemType(), otherTypes);
    }

    protected final ShapedRecipe shapedRecipe(ItemStack result) {
        return new ShapedRecipe(recipeKey(), result);
    }

    protected final ShapedRecipe shapedRecipe(String resultId) {
        return shapedRecipe(customItemStack(resultId));
    }

    protected final ShapedRecipe shapedRecipe(String resultId, int amount) {
        return shapedRecipe(customItemStack(resultId, amount));
    }

    protected final <T extends CustomItem> ShapedRecipe shapedRecipe(Class<T> resultType) {
        return shapedRecipe(customItemStack(resultType));
    }

    protected final <T extends CustomItem> ShapedRecipe shapedRecipe(
            Class<T> resultType,
            int amount
    ) {
        return shapedRecipe(customItemStack(resultType, amount));
    }

    protected final ShapelessRecipe shapelessRecipe(ItemStack result) {
        return new ShapelessRecipe(recipeKey(), result);
    }

    protected final ShapelessRecipe shapelessRecipe(String resultId) {
        return shapelessRecipe(customItemStack(resultId));
    }

    protected final ShapelessRecipe shapelessRecipe(String resultId, int amount) {
        return shapelessRecipe(customItemStack(resultId, amount));
    }

    protected final <T extends CustomItem> ShapelessRecipe shapelessRecipe(Class<T> resultType) {
        return shapelessRecipe(customItemStack(resultType));
    }

    protected final <T extends CustomItem> ShapelessRecipe shapelessRecipe(
            Class<T> resultType,
            int amount
    ) {
        return shapelessRecipe(customItemStack(resultType, amount));
    }

    final Recipe Build(NamespacedKey key) {
        if (this.recipe != null) {
            throw new IllegalStateException(
                    "CustomRecipe has already been built: " + getClass().getName()
            );
        }

        this.key = key;

        Recipe recipe = createRecipe(key);
        if (recipe == null) {
            throw new IllegalStateException(
                    "CustomRecipe returned null: " + getClass().getName()
            );
        }

        this.recipe = recipe;
        return recipe;
    }

    private void EnsureRegistered() {
        if (recipe == null) {
            throw new IllegalStateException(
                    "CustomRecipe has not been registered yet: " + getClass().getName()
            );
        }
    }
}
