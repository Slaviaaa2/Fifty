package com.fifty.dev.advancements;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Moves advancement trees or forces individual advancement coordinates
 * after Minecraft has calculated its vanilla advancement layout.
 *
 * <p>Individual advancement x/y values define the tree-local absolute
 * coordinates and take priority over a configured tree offset.</p>
 */
public final class AdvancementPositionManager implements Listener {
    private static final String TREE_OFFSETS_CONFIG_PATH =
            "advancement-tree-offsets";
    private static final String FORCED_POSITIONS_CONFIG_PATH =
            "advancement-positions";

    private final JavaPlugin plugin;

    /**
     * Root advancement -> tree offset.
     */
    private Map<NamespacedKey, Position> treeOffsets = Map.of();

    /**
     * Advancement -> forced absolute position.
     */
    private Map<NamespacedKey, Position> forcedPositions = Map.of();

    /**
     * Advancement -> vanilla-calculated position.
     *
     * These coordinates are captured before any custom offset is applied,
     * making applyPositions() idempotent.
     */
    private final Map<NamespacedKey, Position> vanillaPositions =
            new HashMap<>();

    public AdvancementPositionManager(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Initializes the manager.
     */
    public void initialize() {
        this.plugin.saveDefaultConfig();

        this.reloadOffsets();

        Bukkit.getPluginManager().registerEvents(
                this,
                this.plugin
        );

        /*
         * At this point Minecraft has already performed its normal
         * advancement-tree layout.
         */
        this.captureVanillaPositions();

        this.applyPositions();
        this.scheduleApplyAndSynchronize();
    }

    /**
     * Reloads tree offsets and forced advancement positions from config.
     *
     * <p>This deliberately does NOT clear vanillaPositions because
     * changing this plugin's config should not cause the currently
     * offset coordinates to become the new baseline.</p>
     */
    public void reloadOffsets() {
        this.plugin.reloadConfig();

        this.treeOffsets = this.loadPositions(
                TREE_OFFSETS_CONFIG_PATH,
                "advancement tree offset"
        );
        this.forcedPositions = this.loadPositions(
                FORCED_POSITIONS_CONFIG_PATH,
                "forced advancement position"
        );

        this.logLoadedRules();
    }

    private void logLoadedRules() {
        this.plugin.getLogger().info(
                "Loaded " + this.treeOffsets.size()
                        + " advancement tree offset(s) and "
                        + this.forcedPositions.size()
                        + " forced advancement position(s)."
        );

        this.treeOffsets.forEach((key, position) ->
                this.plugin.getLogger().info(
                        "Tree offset " + key
                                + " = (" + position.x()
                                + ", " + position.y() + ")"
                )
        );
        this.forcedPositions.forEach((key, position) ->
                this.plugin.getLogger().info(
                        "Forced advancement position " + key
                                + " = (" + position.x()
                                + ", " + position.y() + ")"
                )
        );
    }

    private Map<NamespacedKey, Position> loadPositions(
            final String configPath,
            final String description
    ) {
        ConfigurationSection section = this.plugin
                .getConfig()
                .getConfigurationSection(configPath);

        if (section == null) {
            return Map.of();
        }

        Map<NamespacedKey, Position> loaded =
                new LinkedHashMap<>();

        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection positionSection =
                    section.getConfigurationSection(rawKey);

            if (positionSection == null) {
                this.warnInvalid(
                        description,
                        rawKey,
                        "an x/y section is required"
                );
                continue;
            }

            NamespacedKey key =
                    NamespacedKey.fromString(
                            rawKey,
                            this.plugin
                    );

            if (key == null) {
                this.warnInvalid(
                        description,
                        rawKey,
                        "invalid namespaced key"
                );
                continue;
            }

            Number x = asNumber(
                    positionSection.get("x")
            );

            Number y = asNumber(
                    positionSection.get("y")
            );

            if (x == null || y == null) {
                this.warnInvalid(
                        description,
                        rawKey,
                        "x and y must both be numbers"
                );
                continue;
            }

            float xValue = x.floatValue();
            float yValue = y.floatValue();

            if (!Float.isFinite(xValue)
                    || !Float.isFinite(yValue)) {
                this.warnInvalid(
                        description,
                        rawKey,
                        "x and y must both be finite"
                );
                continue;
            }

            loaded.put(
                    key,
                    new Position(
                            xValue,
                            yValue
                    )
            );
        }

        return Map.copyOf(loaded);
    }

    /**
     * Captures Minecraft's currently-calculated advancement positions.
     *
     * <p>This must only be called while the advancements are still at
     * their vanilla positions. Resource reload creates new advancement
     * instances and recalculates the layout, so the cache is cleared and
     * recaptured when that occurs.</p>
     */
    private void captureVanillaPositions() {
        this.vanillaPositions.clear();

        var iterator =
                Bukkit.advancementIterator();

        while (iterator.hasNext()) {
            Advancement advancement =
                    iterator.next();

            AdvancementDisplay display =
                    advancement.getDisplay();

            if (display == null)
                continue;

            try {
                Position position =
                        getDisplayLocation(display);

                this.vanillaPositions.put(
                        advancement.getKey(),
                        position
                );

            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Failed to read vanilla position for advancement '"
                                + advancement.getKey()
                                + "'. This Paper version may be incompatible.",
                        unwrap(exception)
                );
            }
        }

        this.plugin.getLogger().info(
                "Captured vanilla positions for "
                        + this.vanillaPositions.size()
                        + " advancement(s)."
        );
    }

    /**
     * Applies all configured tree offsets.
     *
     * <p>The final position is calculated as:</p>
     *
     * <pre>
     * forced advancement position
     * OR
     * (vanilla position + configured tree offset)
     * </pre>
     *
     * <p>A forced position is absolute and is never modified by a tree
     * offset. This preserves the original force-only behavior. Repeated
     * calls do not accumulate offsets.</p>
     *
     * @return number of advancement displays affected by either rule
     */
    public int applyPositions() {
        int applied = 0;

        var iterator =
                Bukkit.advancementIterator();

        while (iterator.hasNext()) {
            Advancement advancement =
                    iterator.next();

            AdvancementDisplay display =
                    advancement.getDisplay();

            if (display == null)
                continue;

            NamespacedKey advancementKey =
                    advancement.getKey();

            /*
             * Normally this will already exist.
             *
             * This fallback is useful if an advancement appeared after
             * initialization without a complete server-resource reload.
             */
            Position vanillaPosition =
                    this.vanillaPositions.get(
                            advancementKey
                    );

            if (vanillaPosition == null) {
                try {
                    vanillaPosition =
                            getDisplayLocation(display);

                    this.vanillaPositions.put(
                            advancementKey,
                            vanillaPosition
                    );

                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(
                            "Failed to read position for advancement '"
                                    + advancementKey
                                    + "'.",
                            unwrap(exception)
                    );
                }
            }

            Advancement root =
                    advancement.getRoot();

            Position offset =
                    this.treeOffsets.get(
                            root.getKey()
                    );

            Position forcedPosition =
                    this.forcedPositions.get(
                            advancementKey
                    );

            float finalX;
            float finalY;

            if (forcedPosition != null) {
                finalX = forcedPosition.x();
                finalY = forcedPosition.y();
            } else {
                finalX = vanillaPosition.x();
                finalY = vanillaPosition.y();
            }

            if (forcedPosition == null && offset != null) {
                finalX += offset.x();
                finalY += offset.y();
            }

            if (offset != null || forcedPosition != null) {
                applied++;
            }

            try {
                setDisplayLocation(
                        display,
                        finalX,
                        finalY
                );

            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Failed to set position for advancement '"
                                + advancementKey
                                + "'. This Paper version may be incompatible.",
                        unwrap(exception)
                );
            }
        }

        if (!this.treeOffsets.isEmpty()
                || !this.forcedPositions.isEmpty()) {
            this.plugin.getLogger().info(
                    "Applied custom positions to "
                            + applied
                            + " advancement(s) using "
                            + this.treeOffsets.size()
                            + " tree offset(s) and "
                            + this.forcedPositions.size()
                            + " forced position(s)."
            );
        }

        return applied;
    }

    /**
     * Reloads only this manager's config values and reapplies them
     * relative to the previously captured vanilla positions.
     *
     * <p>Safe to call multiple times.</p>
     */
    public int reloadAndApply() {
        this.reloadOffsets();

        int applied =
                this.applyPositions();

        this.scheduleApplyAndSynchronize();

        return applied;
    }

    /**
     * Minecraft recreates advancement objects and recalculates their
     * vanilla layout during a resource reload.
     *
     * <p>Therefore the old baseline must be discarded and captured
     * again before applying our offsets.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerResourcesReloaded(
            final ServerResourcesReloadedEvent event
    ) {
        this.reloadOffsets();

        /*
         * IMPORTANT:
         * These are now newly-created DisplayInfo instances with
         * Minecraft's newly-calculated vanilla coordinates.
         */
        this.captureVanillaPositions();

        this.applyPositions();

        /*
         * Do not resend while Paper is still completing its resource-reload
         * callback. Reapply and resend on the next tick so our packet is
         * deterministically the final advancement state seen by clients.
         */
        this.scheduleApplyAndSynchronize();
    }

    private void scheduleApplyAndSynchronize() {
        Bukkit.getScheduler().runTask(
                this.plugin,
                () -> {
                    this.applyPositions();

                    if (!Bukkit.getOnlinePlayers().isEmpty()) {
                        Bukkit.getServer().updateResources();
                    }
                }
        );
    }

    /**
     * Reads the calculated NMS DisplayInfo position.
     */
    private static Position getDisplayLocation(
            final AdvancementDisplay display
    ) throws ReflectiveOperationException {
        Object displayInfo =
                getDisplayInfo(display);

        Method getX =
                displayInfo
                        .getClass()
                        .getMethod("getX");

        Method getY =
                displayInfo
                        .getClass()
                        .getMethod("getY");

        float x =
                ((Number) getX.invoke(displayInfo))
                        .floatValue();

        float y =
                ((Number) getY.invoke(displayInfo))
                        .floatValue();

        return new Position(x, y);
    }

    /**
     * Sets the NMS DisplayInfo position.
     */
    private static void setDisplayLocation(
            final AdvancementDisplay display,
            final float x,
            final float y
    ) throws ReflectiveOperationException {
        Object displayInfo =
                getDisplayInfo(display);

        Method setLocation =
                displayInfo
                        .getClass()
                        .getMethod(
                                "setLocation",
                                float.class,
                                float.class
                        );

        setLocation.invoke(
                displayInfo,
                x,
                y
        );
    }

    /**
     * Unwraps Paper's AdvancementDisplay implementation to its
     * net.minecraft.advancements.DisplayInfo handle.
     */
    private static Object getDisplayInfo(
            final AdvancementDisplay display
    ) throws ReflectiveOperationException {
        Method handleMethod =
                display
                        .getClass()
                        .getMethod("handle");

        return handleMethod.invoke(display);
    }

    private static Number asNumber(
            final Object value
    ) {
        return value instanceof Number number
                ? number
                : null;
    }

    private static Throwable unwrap(
            final ReflectiveOperationException exception
    ) {
        if (exception
                instanceof InvocationTargetException invocation
                && invocation.getCause() != null) {
            return invocation.getCause();
        }

        return exception;
    }

    private void warnInvalid(
            final String description,
            final String key,
            final String reason
    ) {
        this.plugin.getLogger().warning(
                "Ignoring invalid "
                        + description
                        + " '"
                        + key
                        + "': "
                        + reason
                        + "."
        );
    }

    private record Position(
            float x,
            float y
    ) {
    }
}
