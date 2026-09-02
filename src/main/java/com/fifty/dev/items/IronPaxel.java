package com.fifty.dev.items;

import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.CustomItemAdvancement;
import com.fifty.dev.api.enums.CustomItemTrigger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

public final class IronPaxel extends CustomItem {
    @Override
    public String getItemId() {
        return "IRON_PAXEL";
    }

    @Override
    public ItemStack getItemStack() {
        var stack = new ItemStack(Material.IRON_AXE);
        stack.editMeta(meta -> {
            meta.itemName(Component.text("鉄のパクセル"));
            meta.lore(List.of(
                    Component.text("掘れないものなどあんまりない")
            ));
            meta.setRarity(ItemRarity.UNCOMMON);

            var tool = meta.getTool();
            var speed = 6.0f;
            tool.setRules(List.of());
            tool.addRule(Tag.INCORRECT_FOR_IRON_TOOL, null, false);
            tool.addRule(Tag.MINEABLE_PICKAXE, speed, true);
            tool.addRule(Tag.MINEABLE_PICKAXE, speed, true);
            tool.addRule(Tag.MINEABLE_PICKAXE, speed, true);

            tool.setDefaultMiningSpeed(1f);
            tool.setDamagePerBlock(1);

            meta.setTool(tool);
        });
        return stack;
    }

    @Override
    public Collection<CustomItemAdvancement> getAdvancements() {
        return List.of(CustomItemAdvancement.of(
                "fifty:iron_paxel",
                CustomItemTrigger.ACQUIRE
        ));
    }
}
