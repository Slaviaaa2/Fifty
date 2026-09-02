package com.fifty.dev.config;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps config.yml aligned with the configuration bundled in the plugin.
 *
 * <p>Missing values are copied from the bundled defaults, obsolete values are
 * removed, and values with an incompatible type are reset. Advancement keys
 * are intentionally dynamic, while the shape of each tree offset remains
 * validated against the bundled x/y template.</p>
 */
public final class ConfigValidator {
    private static final String ADVANCEMENT_OFFSETS_PATH =
            "advancement-tree-offsets";
    private static final String ADVANCEMENT_POSITIONS_PATH =
            "advancement-positions";
    private static final String CUSTOM_BLOCK_OVERRIDES_PATH =
            "custom-blocks.overrides";

    private final JavaPlugin plugin;
    private final List<String> changes = new ArrayList<>();

    private ConfigValidator(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Validates the active configuration and saves it only when corrections
     * were necessary.
     *
     * @return number of properties added, removed, or reset
     */
    public static int validateAndUpdate(JavaPlugin plugin) {
        return new ConfigValidator(plugin).validateAndUpdate();
    }

    private int validateAndUpdate() {
        this.plugin.reloadConfig();

        FileConfiguration config = this.plugin.getConfig();
        Configuration defaults = config.getDefaults();
        if (defaults == null) {
            throw new IllegalStateException(
                    "Bundled config.yml defaults could not be loaded"
            );
        }

        config.options().copyDefaults(false);
        this.validateSection(config, defaults, "");

        if (this.changes.isEmpty()) {
            this.plugin.getLogger().info("config.yml validation passed.");
            return 0;
        }

        this.plugin.saveConfig();
        this.plugin.reloadConfig();
        this.plugin.getLogger().info(
                "Updated config.yml with " + this.changes.size()
                        + " correction(s): " + String.join(", ", this.changes)
        );
        return this.changes.size();
    }

    private void validateSection(
            ConfigurationSection current,
            ConfigurationSection defaults,
            String currentPath
    ) {
        Set<String> currentKeys = new LinkedHashSet<>(current.getKeys(false));
        Set<String> defaultKeys = new LinkedHashSet<>(defaults.getKeys(false));

        for (String key : currentKeys) {
            if (!defaultKeys.contains(key)) {
                current.set(key, null);
                this.changes.add("removed " + joinPath(currentPath, key));
            }
        }

        for (String key : defaultKeys) {
            String path = joinPath(currentPath, key);
            ConfigurationSection defaultChild =
                    defaults.getConfigurationSection(key);

            /*
             * Paper may expose an empty bundled section as a temporary child
             * section even when the active file has no value for it. Handle
             * that case before ordinary section validation; otherwise no
             * correction is recorded and only its comments may be saved.
             */
            if ((ADVANCEMENT_OFFSETS_PATH.equals(path)
                    || ADVANCEMENT_POSITIONS_PATH.equals(path))
                    && (defaultChild == null
                    || defaultChild.getKeys(false).isEmpty())) {
                Object configuredValue = current.get(key, null);
                if (configuredValue == null) {
                    current.set(key, new LinkedHashMap<>());
                    copyComments(current, defaults, key);
                    this.changes.add("added " + path);
                    continue;
                }

                ConfigurationSection currentChild =
                        current.getConfigurationSection(key);
                if (currentChild == null) {
                    current.set(key, new LinkedHashMap<>());
                    copyComments(current, defaults, key);
                    this.changes.add("reset " + path);
                    continue;
                }

                this.validateAdvancementCoordinates(
                        currentChild,
                        null,
                        path
                );
                continue;
            }

            if (defaultChild == null) {
                this.validateValue(current, defaults, key, path);
                continue;
            }

            ConfigurationSection currentChild =
                    current.getConfigurationSection(key);
            if (currentChild == null) {
                current.set(key, null);
                currentChild = current.createSection(key);
                copyComments(current, defaults, key);
                this.changes.add("added " + path);
            }

            if (ADVANCEMENT_OFFSETS_PATH.equals(path)
                    || ADVANCEMENT_POSITIONS_PATH.equals(path)) {
                this.validateAdvancementCoordinates(
                        currentChild,
                        defaultChild,
                        path
                );
            } else if (CUSTOM_BLOCK_OVERRIDES_PATH.equals(path)) {
                // Custom block IDs are dynamic. Values are read defensively by
                // CustomBlockManager and unknown IDs are kept for future blocks.
                continue;
            } else {
                this.validateSection(currentChild, defaultChild, path);
            }
        }
    }

    private void validateAdvancementCoordinates(
            ConfigurationSection current,
            ConfigurationSection defaults,
            String configPath
    ) {
        if (defaults != null) {
            for (String defaultKey : defaults.getKeys(false)) {
                ConfigurationSection defaultEntry =
                        defaults.getConfigurationSection(defaultKey);
                if (defaultEntry == null) {
                    continue;
                }

                ConfigurationSection currentEntry =
                        current.getConfigurationSection(defaultKey);
                if (currentEntry == null) {
                    current.set(defaultKey, null);
                    currentEntry = current.createSection(defaultKey);
                    copyComments(current, defaults, defaultKey);
                    this.changes.add(
                            "added " + configPath + "." + defaultKey
                    );
                }
            }
        }

        for (String rootKey : new LinkedHashSet<>(current.getKeys(false))) {
            String path = configPath + "." + rootKey;
            if (NamespacedKey.fromString(rootKey, this.plugin) == null) {
                current.set(rootKey, null);
                this.changes.add("removed " + path);
                continue;
            }

            ConfigurationSection offset =
                    current.getConfigurationSection(rootKey);
            if (offset == null) {
                current.set(rootKey, null);
                this.changes.add("removed " + path);
                continue;
            }

            ConfigurationSection defaultEntry = defaults == null
                    ? null
                    : defaults.getConfigurationSection(rootKey);
            this.validateCoordinateSection(
                    offset,
                    defaultEntry,
                    path
            );
        }
    }

    private void validateCoordinateSection(
            ConfigurationSection current,
            ConfigurationSection defaults,
            String path
    ) {
        for (String key : new LinkedHashSet<>(current.getKeys(false))) {
            if (!"x".equals(key) && !"y".equals(key)) {
                current.set(key, null);
                this.changes.add("removed " + path + "." + key);
            }
        }

        this.validateCoordinateValue(current, defaults, "x", path + ".x");
        this.validateCoordinateValue(current, defaults, "y", path + ".y");
    }

    private void validateCoordinateValue(
            ConfigurationSection current,
            ConfigurationSection defaults,
            String key,
            String path
    ) {
        Object actual = current.get(key, null);
        if (actual instanceof Number number
                && Double.isFinite(number.doubleValue())) {
            return;
        }

        Object defaultValue = defaults == null
                ? null
                : defaults.get(key);
        Number replacement = defaultValue instanceof Number number
                && Double.isFinite(number.doubleValue())
                ? number
                : 0.0D;

        current.set(key, replacement);
        if (defaults != null) {
            copyComments(current, defaults, key);
        }
        this.changes.add((actual == null ? "added " : "reset ") + path);
    }

    private void validateValue(
            ConfigurationSection current,
            ConfigurationSection defaults,
            String key,
            String path
    ) {
        Object expected = defaults.get(key);
        Object actual = current.get(key, null);

        if (actual != null && isCompatible(actual, expected)) {
            return;
        }

        current.set(key, copyValue(expected));
        copyComments(current, defaults, key);
        this.changes.add((actual == null ? "added " : "reset ") + path);
    }

    private static boolean isCompatible(Object actual, Object expected) {
        if (expected instanceof Number && actual instanceof Number number) {
            return Double.isFinite(number.doubleValue());
        }
        if (expected instanceof List<?>) {
            return actual instanceof List<?>;
        }
        if (expected instanceof Map<?, ?>) {
            return actual instanceof Map<?, ?>;
        }
        return expected != null && expected.getClass().isInstance(actual);
    }

    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(map);
        }
        return value;
    }

    private static void copyComments(
            ConfigurationSection target,
            ConfigurationSection source,
            String key
    ) {
        target.setComments(key, source.getComments(key));
        target.setInlineComments(key, source.getInlineComments(key));
    }

    private static String joinPath(String parent, String child) {
        return parent.isEmpty() ? child : parent + "." + child;
    }
}
