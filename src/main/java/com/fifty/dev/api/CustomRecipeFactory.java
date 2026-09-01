package com.fifty.dev.api;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class CustomRecipeFactory {
    private static final Map<String, CustomRecipe> RECIPES = new LinkedHashMap<>();
    private static final Map<Class<? extends CustomRecipe>, CustomRecipe> RECIPES_BY_TYPE = new HashMap<>();

    private CustomRecipeFactory() {
    }

    public static void Initialize(JavaPlugin plugin, String packageName) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(packageName, "packageName");

        UnregisterAll();

        try {
            URI location = plugin.getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();

            Path path = Paths.get(location);

            if (Files.isDirectory(path)) {
                ScanDirectory(plugin, path, packageName);
            } else {
                ScanJar(plugin, path, packageName);
            }

            plugin.getLogger().info(
                    "Registered " + RECIPES.size() + " custom recipes."
            );
        } catch (Exception e) {
            UnregisterAll();
            throw new IllegalStateException(
                    "Failed to scan CustomRecipes",
                    e
            );
        }
    }

    public static void UnregisterAll() {
        for (CustomRecipe customRecipe : RECIPES.values()) {
            Bukkit.removeRecipe(customRecipe.getKey());
        }

        RECIPES.clear();
        RECIPES_BY_TYPE.clear();
    }

    private static void ScanJar(
            JavaPlugin plugin,
            Path jarPath,
            String packageName
    ) throws IOException {
        String packagePath = packageName.replace('.', '/') + "/";

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory())
                    continue;

                String name = entry.getName();

                if (!name.startsWith(packagePath))
                    continue;

                if (!name.endsWith(".class"))
                    continue;

                if (name.contains("$"))
                    continue;

                String className = name
                        .substring(0, name.length() - ".class".length())
                        .replace('/', '.');

                TryRegister(plugin, className);
            }
        }
    }

    private static void ScanDirectory(
            JavaPlugin plugin,
            Path root,
            String packageName
    ) throws IOException {
        Path packagePath = root.resolve(
                packageName.replace('.', '/')
        );

        if (!Files.exists(packagePath))
            return;

        try (var stream = Files.walk(packagePath)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().contains("$"))
                    .forEach(path -> {
                        Path relative = root.relativize(path);

                        String className = relative
                                .toString()
                                .replace(FileSystems.getDefault().getSeparator(), ".");

                        className = className.substring(
                                0,
                                className.length() - ".class".length()
                        );

                        TryRegister(plugin, className);
                    });
        }
    }

    private static void TryRegister(
            JavaPlugin plugin,
            String className
    ) {
        try {
            Class<?> clazz = Class.forName(
                    className,
                    false,
                    plugin.getClass().getClassLoader()
            );

            if (!CustomRecipe.class.isAssignableFrom(clazz))
                return;

            if (clazz == CustomRecipe.class)
                return;

            if (clazz.isInterface())
                return;

            if (Modifier.isAbstract(clazz.getModifiers()))
                return;

            @SuppressWarnings("unchecked")
            Class<? extends CustomRecipe> recipeClass =
                    (Class<? extends CustomRecipe>) clazz;

            var constructor = recipeClass.getDeclaredConstructor();
            constructor.setAccessible(true);

            CustomRecipe customRecipe = constructor.newInstance();

            Register(plugin, customRecipe);

            plugin.getLogger().info(
                    "Registered CustomRecipe: " +
                            customRecipe.getKey() +
                            " (" + className + ")"
            );
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "CustomRecipe must have a no-argument constructor: " +
                            className,
                    e
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate CustomRecipe: " +
                            className,
                    e
            );
        }
    }

    private static void Register(JavaPlugin plugin, CustomRecipe customRecipe) {
        Objects.requireNonNull(customRecipe, "customRecipe");

        String rawId = customRecipe.getRecipeId();

        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException(
                    "CustomRecipe ID cannot be null or blank: " +
                            customRecipe.getClass().getName()
            );
        }

        String id = NormalizeId(rawId);

        if (RECIPES.containsKey(id)) {
            throw new IllegalStateException(
                    "Duplicate CustomRecipe ID '" + id + "': " +
                            RECIPES.get(id).getClass().getName() +
                            " and " +
                            customRecipe.getClass().getName()
            );
        }

        if (RECIPES_BY_TYPE.containsKey(customRecipe.getClass())) {
            throw new IllegalStateException(
                    "Duplicate CustomRecipe type: " +
                            customRecipe.getClass().getName()
            );
        }

        NamespacedKey key = new NamespacedKey(plugin, id);
        Recipe recipe = customRecipe.Build(key);

        if (!(recipe instanceof Keyed keyedRecipe)) {
            throw new IllegalStateException(
                    "CustomRecipe must create a keyed Bukkit recipe: " +
                            customRecipe.getClass().getName()
            );
        }

        if (!key.equals(keyedRecipe.getKey())) {
            throw new IllegalStateException(
                    "CustomRecipe used an unexpected key. Expected '" + key +
                            "' but got '" + keyedRecipe.getKey() + "': " +
                            customRecipe.getClass().getName()
            );
        }

        if (!Bukkit.addRecipe(recipe)) {
            throw new IllegalStateException(
                    "Bukkit rejected CustomRecipe '" + key + "': " +
                            customRecipe.getClass().getName()
            );
        }

        RECIPES.put(id, customRecipe);
        RECIPES_BY_TYPE.put(customRecipe.getClass(), customRecipe);
    }

    public static CustomRecipe Provide(String id) {
        if (id == null)
            return null;

        return RECIPES.get(NormalizeId(id));
    }

    public static <T extends CustomRecipe> T Provide(Class<T> type) {
        if (type == null)
            return null;

        CustomRecipe recipe = RECIPES_BY_TYPE.get(type);

        if (recipe == null)
            return null;

        return type.cast(recipe);
    }

    public static Recipe ProvideRecipe(String id) {
        CustomRecipe customRecipe = Provide(id);
        return customRecipe == null ? null : customRecipe.getRecipe();
    }

    public static Recipe ProvideRecipe(Class<? extends CustomRecipe> type) {
        CustomRecipe customRecipe = Provide(type);
        return customRecipe == null ? null : customRecipe.getRecipe();
    }

    public static <T extends Recipe> T ProvideRecipe(String id, Class<T> recipeType) {
        Recipe recipe = ProvideRecipe(id);
        if (recipe == null || !recipeType.isInstance(recipe))
            return null;

        return recipeType.cast(recipe);
    }

    public static boolean Exists(String id) {
        return id != null &&
                RECIPES.containsKey(NormalizeId(id));
    }

    public static boolean Exists(Class<? extends CustomRecipe> type) {
        return type != null &&
                RECIPES_BY_TYPE.containsKey(type);
    }

    public static Collection<CustomRecipe> GetAll() {
        return Collections.unmodifiableCollection(RECIPES.values());
    }

    public static Set<String> GetAllIds() {
        return Collections.unmodifiableSet(RECIPES.keySet());
    }

    private static String NormalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
