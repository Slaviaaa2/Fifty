package com.fifty.dev.advancements;

import com.fifty.dev.api.CustomItemAdvancement;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves Bukkit advancements and awards progress on the server thread. */
final class AdvancementAwarder {
    private final JavaPlugin plugin;
    private final Set<NamespacedKey> reportedMissingAdvancements = new HashSet<>();
    private final Set<String> reportedMissingCriteria = new HashSet<>();

    AdvancementAwarder(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    void award(Player player, CustomItemAdvancement rule) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(rule, "rule");

        if (rule.criteria().isEmpty()) {
            this.award(player, rule.advancementKey());
            return;
        }

        this.awardCriteria(player, rule.advancementKey(), rule.criteria());
    }

    void awardCriteria(
            Player player,
            NamespacedKey advancementKey,
            Collection<String> criteria
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(advancementKey, "advancementKey");
        Objects.requireNonNull(criteria, "criteria");

        if (criteria.isEmpty()) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Collection<String> criteriaSnapshot = List.copyOf(criteria);
            Bukkit.getScheduler().runTask(
                    this.plugin,
                    () -> this.awardCriteria(
                            player,
                            advancementKey,
                            criteriaSnapshot
                    )
            );
            return;
        }

        Advancement advancement = Bukkit.getAdvancement(advancementKey);
        if (advancement == null) {
            this.reportMissingAdvancement(advancementKey);
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        Collection<String> knownCriteria = advancement.getCriteria();
        for (String criterion : criteria) {
            if (!knownCriteria.contains(criterion)) {
                this.reportMissingCriterion(advancementKey, criterion);
                continue;
            }
            progress.awardCriteria(criterion);
        }
    }

    void award(Player player, NamespacedKey advancementKey) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(advancementKey, "advancementKey");

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(
                    this.plugin,
                    () -> this.award(player, advancementKey)
            );
            return;
        }

        Advancement advancement = Bukkit.getAdvancement(advancementKey);
        if (advancement == null) {
            this.reportMissingAdvancement(advancementKey);
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            progress.awardCriteria(criterion);
        }
    }

    private void reportMissingAdvancement(NamespacedKey advancementKey) {
        if (this.reportedMissingAdvancements.add(advancementKey)) {
            this.plugin.getLogger().warning(
                    "Could not award missing advancement '"
                            + advancementKey + "'."
            );
        }
    }

    private void reportMissingCriterion(
            NamespacedKey advancementKey,
            String criterion
    ) {
        String identity = advancementKey + "/" + criterion;
        if (this.reportedMissingCriteria.add(identity)) {
            this.plugin.getLogger().warning(
                    "Could not award missing criterion '" + criterion
                            + "' in advancement '" + advancementKey + "'."
            );
        }
    }
}
