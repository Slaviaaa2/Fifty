package com.fifty.dev;

import com.fifty.dev.api.CustomItemFactory;
import com.fifty.dev.api.CustomRecipeFactory;
import org.bukkit.plugin.java.JavaPlugin;

public final class Fifty extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        CustomItemFactory.Initialize(this, "com.fifty.dev.items");
        CustomRecipeFactory.Initialize(this, "com.fifty.dev.recipes");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        CustomRecipeFactory.UnregisterAll();
    }
}
