package com.fifty.dev.api;

import com.fifty.dev.api.enums.NamespacedKeyFactoryType;
import org.bukkit.NamespacedKey;

public final class NamespacedKeyFactory {
    private static final String NAMESPACE = "fifty";

    private NamespacedKeyFactory() {
    }

    public static NamespacedKey ProvideKey(NamespacedKeyFactoryType type) {
        var key = switch (type){
            case NamespacedKeyFactoryType.ITEM_ID -> "item_id";
            default -> "unknown_key";
        };
        return new NamespacedKey(NAMESPACE, key);
    }

    public static NamespacedKeyFactoryType ResolveKey(NamespacedKey key){
        if (key == null || !key.getNamespace().equals(NAMESPACE))
            return NamespacedKeyFactoryType.UNKNOWN_KEY;

        return switch (key.getKey()){
            case "item_id" -> NamespacedKeyFactoryType.ITEM_ID;
            default -> NamespacedKeyFactoryType.UNKNOWN_KEY;
        };
    }
}
