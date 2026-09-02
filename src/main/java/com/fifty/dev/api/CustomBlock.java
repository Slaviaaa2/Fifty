package com.fifty.dev.api;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * A custom item that keeps its identity when placed as a block.
 *
 * <p>The placed one-item stack is persisted in the chunk and restored when
 * the block drops. Behaviour can be configured under {@code custom-blocks}
 * in config.yml. Subclasses may override the hooks for block-specific logic.</p>
 */
public abstract class CustomBlock extends CustomItem {
    /** Called before a player break is processed. The event may be cancelled. */
    public void onBlockBreak(BlockBreakEvent event) {
    }

    /** Called when a player interacts with an instance of this custom block. */
    public void onBlockInteract(PlayerInteractEvent event) {
    }
}
