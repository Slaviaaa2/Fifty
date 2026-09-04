package com.fifty.dev.api;

import com.fifty.dev.api.enums.NamespacedKeyFactoryType;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public abstract class CustomItem {
    public abstract String getItemId();
    public abstract ItemStack getItemStack();

    /**
     * Advancement rules owned by this item definition.
     * Each matching trigger awards the configured criteria, or every
     * remaining criterion when none are specified.
     */
    public Collection<CustomItemAdvancement> getAdvancements() {
        return List.of();
    }

    /**
     * Allows an item to add event-specific conditions to a declared rule.
     * Returning false leaves the advancement unchanged for this event.
     */
    public boolean shouldTriggerAdvancement(
            Player player,
            CustomItemAdvancement advancement,
            Event sourceEvent
    ) {
        return true;
    }

    /** Called whenever a player inventory slot changes to this custom item. */
    public void onInventorySlotChange(PlayerInventorySlotChangeEvent event) {
    }

    /** Called when this custom item is produced by a crafting recipe. */
    public void onCraft(CraftItemEvent event) {
    }

    /** Called before a player picks this custom item up. */
    public void onPickup(EntityPickupItemEvent event) {
    }

    /** Called when this custom item is used in a player interaction. */
    public void onInteract(PlayerInteractEvent event) {
    }

    /** Called before this custom item is consumed. */
    public void onConsume(PlayerItemConsumeEvent event) {
    }

    /** Called before this custom item is dropped. */
    public void onDrop(PlayerDropItemEvent event) {
    }

    /** Called before durability damage is applied to this custom item. */
    public void onDamage(PlayerItemDamageEvent event) {
    }

    /** Called after this custom item breaks because its durability ran out. */
    public void onBreak(PlayerItemBreakEvent event) {
    }

    /** Called before the player selects an inventory slot containing this item. */
    public void onSelect(PlayerItemHeldEvent event) {
    }

    /** Called when this item participates in a main/off-hand swap. */
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
    }

    /** Called before this item places a block. */
    public void onPlace(BlockPlaceEvent event) {
    }

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

    /**
     * Recreates an existing stack from this definition while retaining state
     * acquired through normal play.
     */
    public final ItemStack updateItemStack(ItemStack existing) {
        Objects.requireNonNull(existing, "existing");

        if (!matches(existing)) {
            throw new IllegalArgumentException(
                    "ItemStack does not belong to CustomItem '" + getItemId() + "'"
            );
        }

        ItemStack updated = createItemStack(existing.getAmount());
        ItemMeta previousMeta = existing.getItemMeta();
        ItemMeta updatedMeta = updated.getItemMeta();

        updatedMeta.removeEnchantments();
        previousMeta.getEnchants().forEach(
                (enchantment, level) -> updatedMeta.addEnchant(
                        enchantment, level, true
                )
        );

        if (previousMeta instanceof EnchantmentStorageMeta previousStorage
                && updatedMeta instanceof EnchantmentStorageMeta updatedStorage) {
            for (var enchantment : updatedStorage.getStoredEnchants().keySet()) {
                updatedStorage.removeStoredEnchant(enchantment);
            }
            previousStorage.getStoredEnchants().forEach(
                    (enchantment, level) -> updatedStorage.addStoredEnchant(
                            enchantment, level, true
                    )
            );
        }

        if (previousMeta instanceof Damageable previousDamage
                && updatedMeta instanceof Damageable updatedDamage) {
            if (previousDamage.hasDamage()) {
                updatedDamage.setDamage(previousDamage.getDamage());
            } else {
                updatedDamage.resetDamage();
            }
        }

        if (previousMeta instanceof Repairable previousRepair
                && updatedMeta instanceof Repairable updatedRepair
                && previousRepair.hasRepairCost()) {
            updatedRepair.setRepairCost(previousRepair.getRepairCost());
        }

        if (previousMeta.hasCustomName()) {
            updatedMeta.customName(previousMeta.customName());
        }

        previousMeta.getPersistentDataContainer().copyTo(
                updatedMeta.getPersistentDataContainer(),
                false
        );

        updated.setItemMeta(updatedMeta);
        return updated;
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
