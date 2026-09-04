package com.fifty.dev.advancements;

import com.fifty.dev.api.VaultEconomy;
import com.fifty.dev.api.events.CurrencyGainEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;

/** Awards advancements when a player's Vault balance reaches a threshold. */
final class CurrencyBalanceAdvancementTrigger
        implements AdvancementTrigger, Listener {
    private static final long RECONCILIATION_INTERVAL_TICKS = 100L;
    private static final NamespacedKey ROOT_ADVANCEMENT =
            requireKey("fifty:root");

    /*
     * Add balance-based advancements here. The event handler and fallback
     * reconciliation automatically evaluate every rule in this list.
     */
    private static final List<BalanceAdvancement> ADVANCEMENTS = List.of(
            BalanceAdvancement.withTenSteps(
                    "fifty:millionaire",
                    1_000_000.0D
            ),
            BalanceAdvancement.withTenSteps(
                    "fifty:billionaire",
                    1_000_000_000.0D
            )
    );

    private final JavaPlugin plugin;
    private final VaultEconomy vaultEconomy;
    private final AdvancementAwarder awarder;
    private BukkitTask reconciliationTask;

    CurrencyBalanceAdvancementTrigger(
            JavaPlugin plugin,
            VaultEconomy vaultEconomy,
            AdvancementAwarder awarder
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.vaultEconomy = Objects.requireNonNull(
                vaultEconomy,
                "vaultEconomy"
        );
        this.awarder = Objects.requireNonNull(awarder, "awarder");
    }

    @Override
    public void initialize() {
        this.plugin.getServer().getPluginManager().registerEvents(
                this,
                this.plugin
        );

        if (this.plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            this.reconciliationTask = this.plugin.getServer().getScheduler()
                    .runTaskTimer(
                            this.plugin,
                            this::reconcileBalances,
                            RECONCILIATION_INTERVAL_TICKS,
                            RECONCILIATION_INTERVAL_TICKS
                    );
        }
    }

    @Override
    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (this.reconciliationTask != null) {
            this.reconciliationTask.cancel();
            this.reconciliationTask = null;
        }
    }

    @EventHandler
    public void onCurrencyGain(CurrencyGainEvent event) {
        this.evaluate(event.getPlayer(), event.getBalance());
    }

    private void reconcileBalances() {
        Economy economy = this.vaultEconomy.getProvider().orElse(null);
        if (economy == null) {
            return;
        }

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.evaluate(player, economy.getBalance(player));
        }
    }

    private void evaluate(Player player, double balance) {
        if (!Double.isFinite(balance)) {
            return;
        }
        if (balance > 0.0D) {
            this.awarder.award(player, ROOT_ADVANCEMENT);
        }

        for (BalanceAdvancement advancement : ADVANCEMENTS) {
            this.awarder.awardCriteria(
                    player,
                    advancement.key(),
                    advancement.completedCriteria(balance)
            );
        }
    }

    private record BalanceAdvancement(
            NamespacedKey key,
            double target,
            List<String> criteria
    ) {
        private BalanceAdvancement {
            Objects.requireNonNull(key, "key");
            requirePositiveFinite(target);
            criteria = List.copyOf(criteria);
            if (criteria.isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one progress criterion is required"
                );
            }
        }

        static BalanceAdvancement withTenSteps(String key, double target) {
            return new BalanceAdvancement(
                    requireKey(key),
                    target,
                    List.of(
                            "progress_1", "progress_2", "progress_3",
                            "progress_4", "progress_5", "progress_6",
                            "progress_7", "progress_8", "progress_9",
                            "progress_10"
                    )
            );
        }

        List<String> completedCriteria(double balance) {
            if (balance < this.target / this.criteria.size()) {
                return List.of();
            }

            int completed = Math.min(
                    this.criteria.size(),
                    (int) Math.floor(
                            balance / this.target * this.criteria.size()
                    )
            );
            return this.criteria.subList(0, completed);
        }
    }

    private static NamespacedKey requireKey(String key) {
        NamespacedKey parsed = NamespacedKey.fromString(
                Objects.requireNonNull(key, "key")
        );
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "Invalid advancement key: " + key
            );
        }
        return parsed;
    }

    private static void requirePositiveFinite(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(
                    "Balance target must be positive and finite"
            );
        }
    }
}
