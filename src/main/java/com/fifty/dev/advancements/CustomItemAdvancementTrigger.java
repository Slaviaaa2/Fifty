package com.fifty.dev.advancements;

import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.CustomItemAdvancement;
import com.fifty.dev.api.enums.CustomItemTrigger;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Logger;

/** Evaluates the advancement rules owned by a custom-item definition. */
final class CustomItemAdvancementTrigger {
    private final Logger logger;
    private final AdvancementAwarder awarder;

    CustomItemAdvancementTrigger(
            Logger logger,
            AdvancementAwarder awarder
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.awarder = Objects.requireNonNull(awarder, "awarder");
    }

    void trigger(
            Player player,
            CustomItem item,
            CustomItemTrigger trigger,
            Event sourceEvent
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(sourceEvent, "sourceEvent");

        Collection<CustomItemAdvancement> rules = item.getAdvancements();
        if (rules == null) {
            this.logger.warning(
                    "CustomItem '" + item.getItemId()
                            + "' returned null from getAdvancements()."
            );
            return;
        }

        for (CustomItemAdvancement rule : rules) {
            if (rule == null) {
                this.logger.warning(
                        "CustomItem '" + item.getItemId()
                                + "' contains a null advancement rule."
                );
                continue;
            }
            if (rule.trigger() == trigger
                    && item.shouldTriggerAdvancement(
                            player,
                            rule,
                            sourceEvent
                    )) {
                this.awarder.award(player, rule);
            }
        }
    }
}
