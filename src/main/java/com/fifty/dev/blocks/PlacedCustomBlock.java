package com.fifty.dev.blocks;

import com.fifty.dev.api.CustomBlock;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.List;

record PlacedCustomBlock(
        Block primary,
        List<Block> members,
        ItemStack item,
        CustomBlock customBlock
) {
    String identity() {
        return primary.getWorld().getUID() + ":"
                + primary.getX() + ":" + primary.getY() + ":" + primary.getZ();
    }
}
