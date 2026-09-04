package com.fifty.dev.advancements;

/** A self-contained source of advancement progress. */
interface AdvancementTrigger {
    void initialize();

    default void shutdown() {
    }
}
