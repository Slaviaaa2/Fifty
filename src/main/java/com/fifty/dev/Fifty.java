package com.fifty.dev;

import com.fifty.dev.advancements.AdvancementPositionManager;
import com.fifty.dev.advancements.AdvancementsTriggerManager;
import com.fifty.dev.blocks.CustomBlockManager;
import com.fifty.dev.api.CustomItemFactory;
import com.fifty.dev.api.CustomRecipeFactory;
import com.fifty.dev.api.VaultEconomy;
import com.fifty.dev.config.ConfigValidator;
import com.fifty.dev.items.CustomItemEventManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Fifty extends JavaPlugin {
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

        CustomItemFactory.Initialize(this, "com.fifty.dev.items");
        CustomRecipeFactory.Initialize(this, "com.fifty.dev.recipes");

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

        this.advancementPositions = new AdvancementPositionManager(this);
        this.advancementPositions.initialize();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (this.vaultEconomy != null) {
            this.vaultEconomy.shutdown();
        }
        CustomRecipeFactory.UnregisterAll();
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
