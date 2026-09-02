package com.fifty.dev.api;

/**
 * Controls the built-in behaviour of a {@link CustomBlock}.
 *
 * @param dropInCreative whether a player in creative mode receives the block
 * @param requireCorrectTool whether survival drops require a valid tool
 * @param dropFromExplosions whether explosions drop the custom item
 * @param dropFromNaturalDestruction whether non-player destruction drops the item
 * @param moveWithPistons whether piston movement is allowed and tracked
 * @param protectFromEntities whether entities such as endermen may change the block
 * @param experience experience dropped when a player breaks the block
 */
public record CustomBlockSettings(
        boolean dropInCreative,
        boolean requireCorrectTool,
        boolean dropFromExplosions,
        boolean dropFromNaturalDestruction,
        boolean moveWithPistons,
        boolean protectFromEntities,
        int experience
) {
    public CustomBlockSettings {
        if (experience < 0) {
            throw new IllegalArgumentException("experience cannot be negative");
        }
    }

    public static CustomBlockSettings defaults() {
        return new CustomBlockSettings(
                false,
                false,
                true,
                true,
                false,
                true,
                0
        );
    }
}
