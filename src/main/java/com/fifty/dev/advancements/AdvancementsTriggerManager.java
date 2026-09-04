package com.fifty.dev.advancements;

import com.fifty.dev.Fifty;
import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.enums.CustomItemTrigger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Objects;

/**
 * Composition root for advancement triggers.
 *
 * <p>Event-specific behavior belongs in an {@link AdvancementTrigger}; this
 * class only wires those triggers to the shared award service and keeps the
 * public entry point used by the item and block managers.</p>
 */
public final class AdvancementsTriggerManager {
    private final AdvancementAwarder awarder;
    private final CustomItemAdvancementTrigger customItems;
    private final List<AdvancementTrigger> eventTriggers;

    public AdvancementsTriggerManager(Fifty plugin) {
        Objects.requireNonNull(plugin, "plugin");

        this.awarder = new AdvancementAwarder(plugin);
        this.customItems = new CustomItemAdvancementTrigger(
                plugin.getLogger(),
                this.awarder
        );
        this.eventTriggers = List.of(
                new CurrencyBalanceAdvancementTrigger(
                        plugin,
                        plugin.getVaultEconomy(),
                        this.awarder
                )
        );
    }

    public void initialize() {
        this.eventTriggers.forEach(AdvancementTrigger::initialize);
    }

    public void shutdown() {
        this.eventTriggers.forEach(AdvancementTrigger::shutdown);
    }

    /** Completes advancement rules declared by an item for this trigger. */
    public void triggerCustomItem(
            Player player,
            CustomItem item,
            CustomItemTrigger trigger,
            Event sourceEvent
    ) {
        this.customItems.trigger(player, item, trigger, sourceEvent);
    }

    /** Completes every remaining criterion of an advancement. */
    public void awardAdvancement(
            Player player,
            NamespacedKey advancementKey
    ) {
        this.awarder.award(player, advancementKey);
    }
}
