package com.fifty.dev.items;

import com.fifty.dev.advancements.AdvancementsTriggerManager;
import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.CustomItemFactory;
import com.fifty.dev.api.enums.CustomItemTrigger;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Routes Bukkit item lifecycle events to their registered {@link CustomItem}. */
public final class CustomItemEventManager implements Listener {
    private final JavaPlugin plugin;
    private final AdvancementsTriggerManager advancements;

    public CustomItemEventManager(
            JavaPlugin plugin,
            AdvancementsTriggerManager advancements
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.advancements = Objects.requireNonNull(advancements, "advancements");
    }

    public void initialize() {
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        this.plugin.getLogger().info(
                "Custom item event hooks enabled for "
                        + CustomItemFactory.GetAll().size() + " registered item(s)."
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        customItem(event.getNewItemStack()).ifPresent(item -> {
            item.onInventorySlotChange(event);
            if (event.shouldTriggerAdvancements()) {
                trigger(event.getPlayer(), item, CustomItemTrigger.ACQUIRE, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        customItem(event.getRecipe().getResult()).ifPresent(item -> {
            item.onCraft(event);
            if (!event.isCancelled()) {
                trigger(player, item, CustomItemTrigger.CRAFT, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        customItem(event.getItem().getItemStack()).ifPresent(item -> {
            item.onPickup(event);
            if (!event.isCancelled()) {
                trigger(player, item, CustomItemTrigger.PICKUP, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        customItem(event.getItem()).ifPresent(item -> {
            item.onInteract(event);
            if (event.useItemInHand() != Event.Result.DENY) {
                trigger(event.getPlayer(), item, CustomItemTrigger.INTERACT, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        customItem(event.getItem()).ifPresent(item -> {
            item.onConsume(event);
            if (!event.isCancelled()) {
                trigger(event.getPlayer(), item, CustomItemTrigger.CONSUME, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        customItem(event.getItemDrop().getItemStack()).ifPresent(item -> {
            item.onDrop(event);
            if (!event.isCancelled()) {
                trigger(event.getPlayer(), item, CustomItemTrigger.DROP, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent event) {
        customItem(event.getItem()).ifPresent(item -> {
            item.onDamage(event);
            if (!event.isCancelled() && event.getDamage() > 0) {
                trigger(event.getPlayer(), item, CustomItemTrigger.DAMAGE, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(PlayerItemBreakEvent event) {
        customItem(event.getBrokenItem()).ifPresent(item -> {
            item.onBreak(event);
            trigger(event.getPlayer(), item, CustomItemTrigger.BREAK, event);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelect(PlayerItemHeldEvent event) {
        ItemStack selected = event.getPlayer().getInventory()
                .getItem(event.getNewSlot());
        customItem(selected).ifPresent(item -> {
            item.onSelect(event);
            if (!event.isCancelled()) {
                trigger(event.getPlayer(), item, CustomItemTrigger.SELECT, event);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Set<CustomItem> items = new LinkedHashSet<>();
        customItem(event.getMainHandItem()).ifPresent(items::add);
        customItem(event.getOffHandItem()).ifPresent(items::add);

        for (CustomItem item : items) {
            item.onSwapHands(event);
        }
        if (!event.isCancelled()) {
            for (CustomItem item : items) {
                trigger(event.getPlayer(), item, CustomItemTrigger.SWAP_HANDS, event);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent) {
            return;
        }
        handlePlace(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        handlePlace(event);
    }

    private void handlePlace(BlockPlaceEvent event) {
        customItem(event.getItemInHand()).ifPresent(item -> {
            item.onPlace(event);
            if (!event.isCancelled() && event.canBuild()) {
                trigger(event.getPlayer(), item, CustomItemTrigger.PLACE, event);
            }
        });
    }

    private void trigger(
            Player player,
            CustomItem item,
            CustomItemTrigger trigger,
            Event sourceEvent
    ) {
        this.advancements.triggerCustomItem(
                player, item, trigger, sourceEvent
        );
    }

    private static Optional<CustomItem> customItem(ItemStack stack) {
        return Optional.ofNullable(CustomItemFactory.Provide(stack));
    }
}
