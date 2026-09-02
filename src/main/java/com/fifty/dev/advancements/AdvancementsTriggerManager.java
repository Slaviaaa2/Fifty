package com.fifty.dev.advancements;

import com.fifty.dev.Fifty;
import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.CustomItemAdvancement;
import com.fifty.dev.api.enums.CustomItemTrigger;
import com.fifty.dev.api.VaultEconomy;
import com.fifty.dev.api.events.CurrencyGainEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class AdvancementsTriggerManager implements Listener {
    private static final long BALANCE_RECONCILIATION_INTERVAL_TICKS = 100L;
    private static final NamespacedKey ROOT_ADVANCEMENT =
            NamespacedKey.fromString("fifty:root");
    private final Fifty plugin;
    private final Set<NamespacedKey> reportedMissingAdvancements = new HashSet<>();
    private final Set<String> reportedMissingCriteria = new HashSet<>();

    /*
     * Vault integration sample:
     *
     * Use vaultEconomy.isAvailable() when only an availability check is
     * needed. Event handlers can obtain the current proxy-backed Economy
     * through vaultEconomy.getProvider().
     */
    private final VaultEconomy vaultEconomy;

    public AdvancementsTriggerManager(Fifty plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");

        this.vaultEconomy = plugin.getVaultEconomy();
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);

        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            Bukkit.getScheduler().runTaskTimer(
                    this.plugin,
                    this::reconcileBalances,
                    BALANCE_RECONCILIATION_INTERVAL_TICKS,
                    BALANCE_RECONCILIATION_INTERVAL_TICKS
            );
        }
    }

    @EventHandler
    public void onCurrencyGain(CurrencyGainEvent event) {
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(
                    this.plugin,
                    () -> this.awardAdvancement(
                            event.getPlayer(),
                            ROOT_ADVANCEMENT
                    )
            );
            return;
        }

        this.awardAdvancement(event.getPlayer(), ROOT_ADVANCEMENT);
    }

    /** Completes advancement rules declared by an item for this trigger. */
    public void triggerCustomItem(
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
            this.plugin.getLogger().warning(
                    "CustomItem '" + item.getItemId()
                            + "' returned null from getAdvancements()."
            );
            return;
        }

        for (CustomItemAdvancement rule : rules) {
            if (rule == null) {
                this.plugin.getLogger().warning(
                        "CustomItem '" + item.getItemId()
                                + "' contains a null advancement rule."
                );
                continue;
            }
            if (rule.trigger() == trigger
                    && item.shouldTriggerAdvancement(
                            player, rule, sourceEvent)) {
                this.awardAdvancement(player, rule);
            }
        }
    }

    private void awardAdvancement(
            Player player,
            CustomItemAdvancement rule
    ) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(
                    this.plugin,
                    () -> this.awardAdvancement(player, rule)
            );
            return;
        }
        if (rule.criteria().isEmpty()) {
            this.awardAdvancement(player, rule.advancementKey());
            return;
        }

        Advancement advancement = Bukkit.getAdvancement(rule.advancementKey());
        if (advancement == null) {
            this.awardAdvancement(player, rule.advancementKey());
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        Collection<String> knownCriteria = advancement.getCriteria();
        for (String criterion : rule.criteria()) {
            if (!knownCriteria.contains(criterion)) {
                String identity = rule.advancementKey() + "/" + criterion;
                if (this.reportedMissingCriteria.add(identity)) {
                    this.plugin.getLogger().warning(
                            "Could not award missing criterion '" + criterion
                                    + "' in advancement '"
                                    + rule.advancementKey() + "'."
                    );
                }
                continue;
            }
            progress.awardCriteria(criterion);
        }
    }

    public void awardAdvancement(
            Player player,
            NamespacedKey advancementKey
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(advancementKey, "advancementKey");

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(
                    this.plugin,
                    () -> this.awardAdvancement(player, advancementKey)
            );
            return;
        }

        Advancement advancement = Bukkit.getAdvancement(advancementKey);

        if (advancement == null) {
            if (this.reportedMissingAdvancements.add(advancementKey)) {
                this.plugin.getLogger().warning(
                        "Could not award missing advancement '"
                                + advancementKey
                                + "'."
                );
            }
            return;
        }

        AdvancementProgress progress =
                player.getAdvancementProgress(advancement);

        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            progress.awardCriteria(criterion);
        }
    }

    /**
     * Fallback for plugins that cached the original Economy provider before
     * Fifty installed its proxy, or that update balances internally.
     */
    private void reconcileBalances() {
        Economy currentEconomy = this.vaultEconomy
                .getProvider()
                .orElse(null);

        if (currentEconomy == null) {
            return;
        }

        Advancement root = Bukkit.getAdvancement(ROOT_ADVANCEMENT);
        if (root == null) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            AdvancementProgress progress =
                    player.getAdvancementProgress(root);

            if (!progress.isDone()
                    && currentEconomy.getBalance(player) > 0.0D) {
                this.awardAdvancement(player, ROOT_ADVANCEMENT);
            }
        }
    }
}
