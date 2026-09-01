package com.fifty.dev.api;

import com.fifty.dev.api.enums.NamespacedKeyFactoryType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public abstract class CustomItem {
    public abstract String getItemId();
    public abstract ItemStack getItemStack();

    public final ItemStack createItemStack(){
        var stack = getItemStack().clone();

        var namespacedKey = NamespacedKeyFactory.ProvideKey(NamespacedKeyFactoryType.ITEM_ID);
        stack.editPersistentDataContainer(pdc -> {
            pdc.set(
                    namespacedKey,
                    PersistentDataType.STRING,
                    getItemId().toLowerCase(Locale.ROOT)
            );
        });
        return stack;
    }

    public final ItemStack createItemStack(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        ItemStack stack = createItemStack();
        stack.setAmount(amount);
        return stack;
    }

    public final boolean matches(ItemStack stack) {
        String itemId = CustomItemFactory.GetItemId(stack);
        return itemId != null &&
                itemId.equals(getItemId().toLowerCase(Locale.ROOT));
    }

    public final RecipeChoice.ExactChoice asRecipeChoice() {
        return RecipeChoice.exactChoice(createItemStack());
    }

    public boolean giveItem(Player player) {
        if (player == null)
            return false;

        return player.getInventory().addItem(createItemStack()).isEmpty();
    }

    public boolean giveItem(Player player, int count) {
        if (player == null || count <= 0)
            return false;

        return player.getInventory().addItem(createItemStack(count)).isEmpty();
    }
}
