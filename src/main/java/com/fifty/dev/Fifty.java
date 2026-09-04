package com.fifty.dev;

import com.fifty.dev.advancements.AdvancementPositionManager;
import com.fifty.dev.advancements.AdvancementsTriggerManager;
import com.fifty.dev.blocks.CustomBlockManager;
import com.fifty.dev.api.CustomItemFactory;
import com.fifty.dev.api.CustomRecipeFactory;
import com.fifty.dev.api.VaultEconomy;
import com.fifty.dev.config.ConfigValidator;
import com.fifty.dev.items.CustomItemEventManager;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class Fifty extends JavaPlugin implements Listener {
    private static final String CUSTOM_ITEM_PACKAGE = "com.fifty.dev.items";
    private static final String CUSTOM_RECIPE_PACKAGE = "com.fifty.dev.recipes";

    private AdvancementPositionManager advancementPositions;
    private AdvancementsTriggerManager advancementsTriggers;
    private CustomBlockManager customBlocks;
    private CustomItemEventManager customItemEvents;
    private VaultEconomy vaultEconomy;

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.saveDefaultConfig();
        ConfigValidator.validateAndUpdate(this);

        this.reloadCustomDefinitions();

        this.vaultEconomy = VaultEconomy.create(this);

        this.advancementsTriggers = new AdvancementsTriggerManager(this);
        this.advancementsTriggers.initialize();

        this.customItemEvents = new CustomItemEventManager(
                this,
                this.advancementsTriggers
        );
        this.customItemEvents.initialize();

        this.customBlocks = new CustomBlockManager(
                this,
                this.advancementsTriggers
        );
        this.customBlocks.initialize();

        this.getServer().getPluginManager().registerEvents(this, this);
        this.updateExistingCustomItems();

        this.advancementPositions = new AdvancementPositionManager(this);
        this.advancementPositions.initialize();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (this.advancementsTriggers != null) {
            this.advancementsTriggers.shutdown();
        }
        if (this.vaultEconomy != null) {
            this.vaultEconomy.shutdown();
        }
        CustomRecipeFactory.UnregisterAll();
    }

    /**
     * Rebuilds custom-item instances and recipes after Paper reloads server
     * resources. Recipes must be rebuilt as well because their results and
     * exact ingredient choices contain snapshots of the item definitions.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerResourcesReloaded(
            ServerResourcesReloadedEvent event
    ) {
        this.reloadCustomDefinitions();

        if (this.customBlocks != null) {
            this.customBlocks.reloadDefinitions();
        }

        this.updateExistingCustomItems();
    }

    private void reloadCustomDefinitions() {
        CustomItemFactory.Initialize(this, CUSTOM_ITEM_PACKAGE);
        CustomRecipeFactory.Initialize(this, CUSTOM_RECIPE_PACKAGE);
    }

    private void updateExistingCustomItems() {
        int updated = 0;

        for (Player player : this.getServer().getOnlinePlayers()) {
            int playerUpdates = updateInventory(player.getInventory())
                    + updateInventory(player.getEnderChest());

            ItemStack replacement = CustomItemFactory.Update(
                    player.getItemOnCursor()
            );
            if (replacement != null) {
                player.setItemOnCursor(replacement);
                playerUpdates++;
            }

            if (playerUpdates > 0) {
                player.updateInventory();
                updated += playerUpdates;
            }
        }

        this.getLogger().info(
                "Updated " + updated + " existing custom item stack(s)."
        );
    }

    private static int updateInventory(Inventory inventory) {
        int updated = 0;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack replacement = CustomItemFactory.Update(
                    inventory.getItem(slot)
            );
            if (replacement == null) {
                continue;
            }

            inventory.setItem(slot, replacement);
            updated++;
        }

        return updated;
    }

    /**
     * Returns this plugin's optional Vault Economy integration.
     */
    public VaultEconomy getVaultEconomy() {
        return this.vaultEconomy;
    }

    /** Returns the placed custom-block service. */
    public CustomBlockManager getCustomBlockManager() {
        return this.customBlocks;
    }
}
