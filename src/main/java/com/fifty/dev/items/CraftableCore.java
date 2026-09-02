package com.fifty.dev.items;

import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.CustomItemAdvancement;
import com.fifty.dev.api.enums.CustomItemTrigger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CraftableCore extends CustomItem {
    @Override
    public String getItemId() {
        return "CRAFTABLE_CORE";
    }

    @Override
    public ItemStack getItemStack() {
        var stack = new ItemStack(Material.IRON_INGOT);
        stack.editMeta(meta -> {
           meta.itemName(Component.text("クラフタブルコア"));
           meta.lore(List.of(
                   Component.text("様々なカスタムアイテム等の元となる汎用素材。")
           ));
           meta.setRarity(ItemRarity.UNCOMMON);
        });
        return stack;
    }

    @Override
    public List<CustomItemAdvancement> getAdvancements() {
        return List.of(CustomItemAdvancement.of(
                "fifty:craftable_core",
                CustomItemTrigger.ACQUIRE
        ));
    }
}
