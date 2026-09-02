package com.fifty.dev.api;

import com.fifty.dev.api.events.CurrencyGainEvent;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides access to the Economy implementation registered through Vault.
 * Fifty can still run when Vault or an Economy provider is not installed.
 */
public final class VaultEconomy implements Listener {
    private final JavaPlugin plugin;
    private final ServicesManager services;
    private final Economy proxy;

    private volatile Economy provider;
    private boolean proxyRegistered;
    private boolean updatingRegistration;

    private VaultEconomy(JavaPlugin plugin, boolean vaultEnabled) {
        this.plugin = plugin;
        this.services = plugin.getServer().getServicesManager();
        this.proxy = vaultEnabled ? this.createProxy() : null;
    }

    public static VaultEconomy create(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        boolean vaultEnabled = plugin.getServer()
                .getPluginManager()
                .isPluginEnabled("Vault");
        VaultEconomy integration = new VaultEconomy(plugin, vaultEnabled);

        if (!vaultEnabled) {
            plugin.getLogger().info("Vault was not found; economy integration is disabled.");
            return integration;
        }

        Bukkit.getPluginManager().registerEvents(integration, plugin);
        integration.refreshProvider();

        if (!integration.isAvailable()) {
            plugin.getLogger().warning(
                    "Vault is installed, but no Economy provider is registered; " +
                            "waiting for one to become available."
            );
            return integration;
        }

        plugin.getLogger().info(
                "Using " + integration.provider.getName() +
                        " as the Vault Economy provider through Fifty's proxy."
        );
        return integration;
    }

    private Economy createProxy() {
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(),
                new Class<?>[]{Economy.class},
                this::invoke
        );
    }

    public boolean isAvailable() {
        return this.provider != null;
    }

    public Optional<Economy> getProvider() {
        return this.isAvailable()
                ? Optional.of(this.proxy)
                : Optional.empty();
    }

    /**
     * Deposits currency through Vault and publishes a {@link CurrencyGainEvent}
     * when the transaction succeeds.
     *
     * <p>Currency-granting code in Fifty should use this method instead of
     * calling {@link Economy#depositPlayer(org.bukkit.OfflinePlayer, double)}
     * directly so advancement triggers and other listeners are notified.</p>
     *
     * @param player recipient of the currency
     * @param amount positive, finite amount to deposit
     * @return the Vault response, or empty when no Economy provider is available
     */
    public Optional<EconomyResponse> deposit(Player player, double amount) {
        Objects.requireNonNull(player, "player");

        if (!Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("amount must be positive and finite");
        }

        if (!this.isAvailable()) {
            return Optional.empty();
        }

        EconomyResponse response = this.proxy.depositPlayer(player, amount);
        return Optional.of(response);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);

        if (this.proxyRegistered) {
            this.services.unregister(Economy.class, this.proxy);
            this.proxyRegistered = false;
        }
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (this.isRelevantEconomyChange(event.getProvider())) {
            this.refreshProvider();
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (this.isRelevantEconomyChange(event.getProvider())) {
            this.refreshProvider();
        }
    }

    private boolean isRelevantEconomyChange(
            RegisteredServiceProvider<?> registration
    ) {
        return !this.updatingRegistration
                && registration.getService() == Economy.class
                && registration.getProvider() != this.proxy;
    }

    /**
     * Selects the highest-priority real provider, then re-registers the proxy
     * so it remains first even when another Highest provider appears later.
     */
    private void refreshProvider() {
        this.updatingRegistration = true;

        try {
            if (this.proxyRegistered) {
                this.services.unregister(Economy.class, this.proxy);
                this.proxyRegistered = false;
            }

            this.provider = this.services
                    .getRegistrations(Economy.class)
                    .stream()
                    .map(RegisteredServiceProvider::getProvider)
                    .filter(candidate -> candidate != this.proxy)
                    .findFirst()
                    .orElse(null);

            if (this.provider != null) {
                this.services.register(
                        Economy.class,
                        this.proxy,
                        this.plugin,
                        ServicePriority.Highest
                );
                this.proxyRegistered = true;
            }
        } finally {
            this.updatingRegistration = false;
        }
    }

    private Object invoke(
            Object proxyInstance,
            Method method,
            Object[] arguments
    ) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxyInstance == arguments[0];
                case "hashCode" -> System.identityHashCode(proxyInstance);
                case "toString" -> "FiftyVaultEconomyProxy[" + this.provider + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        Economy currentProvider = this.provider;
        if (currentProvider == null) {
            throw new IllegalStateException("No Vault Economy provider is available");
        }

        Object result;
        try {
            result = method.invoke(currentProvider, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }

        if (method.getName().equals("depositPlayer")
                && result instanceof EconomyResponse response
                && response.transactionSuccess()
                && response.amount > 0.0D) {
            Player player = resolveOnlinePlayer(arguments);

            if (player != null) {
                Bukkit.getPluginManager().callEvent(
                        new CurrencyGainEvent(
                                player,
                                response.amount,
                                response.balance,
                                !Bukkit.isPrimaryThread()
                        )
                );
            }
        }

        return result;
    }

    private static Player resolveOnlinePlayer(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return null;
        }

        Object account = arguments[0];
        if (account instanceof Player player) {
            return player;
        }
        if (account instanceof OfflinePlayer offlinePlayer) {
            return offlinePlayer.getPlayer();
        }
        if (account instanceof String playerName) {
            return Bukkit.getPlayerExact(playerName);
        }
        return null;
    }
}
