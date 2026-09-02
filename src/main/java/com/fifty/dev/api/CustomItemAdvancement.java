package com.fifty.dev.api;

import com.fifty.dev.api.enums.CustomItemTrigger;
import org.bukkit.NamespacedKey;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Declares advancement progress caused by a custom-item lifecycle trigger.
 * An empty criteria set means that every remaining criterion is awarded.
 */
public record CustomItemAdvancement(
        NamespacedKey advancementKey,
        CustomItemTrigger trigger,
        Set<String> criteria
) {
    public CustomItemAdvancement {
        Objects.requireNonNull(advancementKey, "advancementKey");
        Objects.requireNonNull(trigger, "trigger");
        criteria = Set.copyOf(Objects.requireNonNull(criteria, "criteria"));
        if (criteria.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Advancement criteria cannot be blank");
        }
    }

    public static CustomItemAdvancement of(
            String advancementKey,
            CustomItemTrigger trigger
    ) {
        NamespacedKey parsed = NamespacedKey.fromString(
                Objects.requireNonNull(advancementKey, "advancementKey")
        );
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "Invalid advancement key: " + advancementKey
            );
        }
        return new CustomItemAdvancement(parsed, trigger, Set.of());
    }

    public static CustomItemAdvancement criteria(
            String advancementKey,
            CustomItemTrigger trigger,
            String... criteria
    ) {
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.length == 0) {
            throw new IllegalArgumentException(
                    "At least one advancement criterion is required"
            );
        }
        CustomItemAdvancement complete = of(advancementKey, trigger);
        Set<String> selected = Arrays.stream(criteria)
                .map(criterion -> Objects.requireNonNull(
                        criterion, "criterion"))
                .collect(Collectors.toUnmodifiableSet());
        return new CustomItemAdvancement(
                complete.advancementKey(), trigger, selected
        );
    }
}
