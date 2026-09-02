package com.fifty.dev.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired after Fifty's Vault Economy proxy observes a successful player deposit.
 */
public final class CurrencyGainEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final double amount;
    private final double balance;

    public CurrencyGainEvent(
            Player player,
            double amount,
            double balance,
            boolean asynchronous
    ) {
        super(asynchronous);
        this.player = Objects.requireNonNull(player, "player");
        this.amount = amount;
        this.balance = balance;
    }

    public Player getPlayer() {
        return this.player;
    }

    public double getAmount() {
        return this.amount;
    }

    public double getBalance() {
        return this.balance;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
