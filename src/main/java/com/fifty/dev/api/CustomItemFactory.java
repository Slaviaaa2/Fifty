package com.fifty.dev.api;

import com.fifty.dev.api.enums.NamespacedKeyFactoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class CustomItemFactory {
    private static final Map<String, CustomItem> ITEMS = new LinkedHashMap<>();
    private static final Map<Class<? extends CustomItem>, CustomItem> ITEMS_BY_TYPE = new HashMap<>();

    private CustomItemFactory() {
    }

    public static void Initialize(JavaPlugin plugin, String packageName) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(packageName, "packageName");

        ITEMS.clear();
        ITEMS_BY_TYPE.clear();

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
                    "Registered " + ITEMS.size() + " custom items."
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to scan CustomItems",
                    e
            );
        }
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

                // 内部クラス・匿名クラスを除外
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

            if (!CustomItem.class.isAssignableFrom(clazz))
                return;

            if (clazz == CustomItem.class)
                return;

            if (clazz.isInterface())
                return;

            if (Modifier.isAbstract(clazz.getModifiers()))
                return;

            @SuppressWarnings("unchecked")
            Class<? extends CustomItem> itemClass =
                    (Class<? extends CustomItem>) clazz;

            var constructor = itemClass.getDeclaredConstructor();
            constructor.setAccessible(true);

            CustomItem item = constructor.newInstance();

            Register(item);

            plugin.getLogger().info(
                    "Registered CustomItem: " +
                            item.getItemId() +
                            " (" + className + ")"
            );

        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "CustomItem must have a no-argument constructor: " +
                            className,
                    e
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate CustomItem: " +
                            className,
                    e
            );
        }
    }

    private static void Register(CustomItem item) {
        Objects.requireNonNull(item, "item");

        String rawId = item.getItemId();

        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException(
                    "CustomItem ID cannot be null or blank: " +
                            item.getClass().getName()
            );
        }

        String id = NormalizeId(rawId);

        CustomItem previous = ITEMS.putIfAbsent(id, item);

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate CustomItem ID '" + id + "': " +
                            previous.getClass().getName() +
                            " and " +
                            item.getClass().getName()
            );
        }

        CustomItem previousType =
                ITEMS_BY_TYPE.putIfAbsent(item.getClass(), item);

        if (previousType != null) {
            ITEMS.remove(id);

            throw new IllegalStateException(
                    "Duplicate CustomItem type: " +
                            item.getClass().getName()
            );
        }
    }

    public static CustomItem Provide(String id) {
        if (id == null)
            return null;

        return ITEMS.get(NormalizeId(id));
    }

    public static <T extends CustomItem> T Provide(Class<T> type) {
        if (type == null)
            return null;

        CustomItem item = ITEMS_BY_TYPE.get(type);

        if (item == null)
            return null;

        return type.cast(item);
    }

    public static CustomItem Provide(ItemStack stack) {
        String id = GetItemId(stack);
        return id == null ? null : Provide(id);
    }

    public static String GetItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir())
            return null;

        var key = NamespacedKeyFactory.ProvideKey(
                NamespacedKeyFactoryType.ITEM_ID
        );
        String id = stack.getPersistentDataContainer().get(
                key,
                PersistentDataType.STRING
        );

        return id == null ? null : NormalizeId(id);
    }

    public static ItemStack Create(String id) {
        CustomItem item = Provide(id);
        return item == null ? null : item.createItemStack();
    }

    public static ItemStack Create(String id, int amount) {
        CustomItem item = Provide(id);
        return item == null ? null : item.createItemStack(amount);
    }

    public static <T extends CustomItem> ItemStack Create(Class<T> type) {
        T item = Provide(type);
        return item == null ? null : item.createItemStack();
    }

    public static <T extends CustomItem> ItemStack Create(Class<T> type, int amount) {
        T item = Provide(type);
        return item == null ? null : item.createItemStack(amount);
    }

    /**
     * Updates a registered custom-item stack to its current definition.
     *
     * @return the updated stack, or {@code null} when the stack is not a
     * registered custom item
     */
    public static ItemStack Update(ItemStack stack) {
        CustomItem item = Provide(stack);
        return item == null ? null : item.updateItemStack(stack);
    }

    public static boolean Exists(String id) {
        return id != null &&
                ITEMS.containsKey(NormalizeId(id));
    }

    public static boolean Exists(Class<? extends CustomItem> type) {
        return type != null &&
                ITEMS_BY_TYPE.containsKey(type);
    }

    public static boolean Exists(ItemStack stack) {
        return Provide(stack) != null;
    }

    public static boolean Matches(ItemStack stack, String id) {
        if (id == null)
            return false;

        String stackId = GetItemId(stack);
        return stackId != null && stackId.equals(NormalizeId(id));
    }

    public static boolean Matches(
            ItemStack stack,
            Class<? extends CustomItem> type
    ) {
        if (type == null)
            return false;

        CustomItem item = Provide(stack);
        return item != null && item.getClass() == type;
    }

    public static Collection<CustomItem> GetAll() {
        return Collections.unmodifiableCollection(ITEMS.values());
    }

    public static Set<String> GetAllIds() {
        return Collections.unmodifiableSet(ITEMS.keySet());
    }

    private static String NormalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
