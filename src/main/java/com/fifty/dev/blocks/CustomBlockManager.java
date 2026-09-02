package com.fifty.dev.blocks;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.fifty.dev.advancements.AdvancementsTriggerManager;
import com.fifty.dev.api.CustomBlock;
import com.fifty.dev.api.CustomBlockSettings;
import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.CustomItemFactory;
import com.fifty.dev.api.enums.CustomItemTrigger;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Coordinates placement, persistence and destruction of {@link CustomBlock}s. */
public final class CustomBlockManager implements Listener {
    private final JavaPlugin plugin;
    private final AdvancementsTriggerManager advancements;
    private final CustomBlockStorage storage;
    private final Map<String, CustomBlockSettings> settings = new LinkedHashMap<>();
    private boolean enabled;

    public CustomBlockManager(
            JavaPlugin plugin,
            AdvancementsTriggerManager advancements
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.advancements = Objects.requireNonNull(advancements, "advancements");
        this.storage = new CustomBlockStorage(plugin);
    }

    public void initialize() {
        this.enabled = this.plugin.getConfig().getBoolean("custom-blocks.enabled", true);
        this.settings.clear();

        int registered = 0;
        for (CustomItem item : CustomItemFactory.GetAll()) {
            if (!(item instanceof CustomBlock block)) {
                continue;
            }
            Material material = block.getItemStack().getType();
            if (!material.isBlock()) {
                throw new IllegalStateException(
                        "CustomBlock '" + block.getItemId()
                                + "' must use a placeable block material, got " + material
                );
            }
            this.settings.put(
                    normalizedId(block),
                    readSettings(normalizedId(block))
            );
            registered++;
        }

        if (this.enabled) {
            this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        }
        this.plugin.getLogger().info(
                "Custom block support " + (this.enabled ? "enabled" : "disabled")
                        + " with " + registered + " registered block(s)."
        );
    }

    /** Returns the custom block registered at a placed block location. */
    public Optional<CustomBlock> getCustomBlock(Block block) {
        Objects.requireNonNull(block, "block");
        return this.storage.find(block).map(PlacedCustomBlock::customBlock);
    }

    /** Returns a defensive copy of the exact item stored for a placed block. */
    public Optional<ItemStack> getPlacedItem(Block block) {
        Objects.requireNonNull(block, "block");
        return this.storage.find(block).map(placed -> placed.item().clone());
    }

    /** Returns the effective global/per-item settings for a custom block. */
    public CustomBlockSettings getSettings(CustomBlock block) {
        Objects.requireNonNull(block, "block");
        return settingsFor(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent || !event.canBuild()) {
            return;
        }
        persistPlacement(event, List.of(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }
        List<Block> blocks = event.getReplacedBlockStates().stream()
                .map(BlockState::getBlock)
                .toList();
        persistPlacement(event, blocks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<PlacedCustomBlock> found = this.storage.find(event.getBlock());
        if (found.isEmpty()) {
            return;
        }

        PlacedCustomBlock placed = found.get();
        placed.customBlock().onBlockBreak(event);
        if (event.isCancelled()) {
            return;
        }

        CustomBlockSettings blockSettings = settingsFor(placed.customBlock());
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        boolean creative = event.getPlayer().getGameMode() == GameMode.CREATIVE;
        boolean validTool = !blockSettings.requireCorrectTool()
                || event.getBlock().isPreferredTool(tool);
        boolean shouldDrop = (creative && blockSettings.dropInCreative())
                || (!creative && validTool);

        event.setDropItems(false);
        event.setExpToDrop(blockSettings.experience());
        this.storage.remove(placed);
        clearOtherMembers(placed, event.getBlock());
        if (shouldDrop) {
            drop(event.getBlock(), placed.item());
        }
        this.advancements.triggerCustomItem(
                event.getPlayer(),
                placed.customBlock(),
                CustomItemTrigger.BLOCK_BREAK,
                event
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        handleExplosion(event.blockList(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePiston(event.getBlocks(), event.getDirection().getModX(),
                event.getDirection().getModY(), event.getDirection().getModZ(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePiston(event.getBlocks(), -event.getDirection().getModX(),
                -event.getDirection().getModY(), -event.getDirection().getModZ(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNaturalBreak(BlockBreakBlockEvent event) {
        Optional<PlacedCustomBlock> found = this.storage.find(event.getBlock());
        if (found.isEmpty()) {
            return;
        }
        PlacedCustomBlock placed = found.get();
        event.getDrops().clear();
        if (settingsFor(placed.customBlock()).dropFromNaturalDestruction()) {
            event.getDrops().add(placed.item().clone());
        }
        event.setExpToDrop(settingsFor(placed.customBlock()).experience());
        this.storage.remove(placed);
        clearOtherMembers(placed, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerDestroy(BlockDestroyEvent event) {
        Optional<PlacedCustomBlock> found = this.storage.find(event.getBlock());
        if (found.isEmpty()) {
            return;
        }
        PlacedCustomBlock placed = found.get();
        event.setWillDrop(false);
        this.storage.remove(placed);
        clearOtherMembers(placed, event.getBlock());
        if (settingsFor(placed.customBlock()).dropFromNaturalDestruction()) {
            drop(event.getBlock(), placed.item());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        handleNaturalDestruction(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        handleNaturalDestruction(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Optional<PlacedCustomBlock> found = this.storage.find(event.getBlock());
        if (found.isEmpty()) {
            return;
        }
        PlacedCustomBlock placed = found.get();
        if (settingsFor(placed.customBlock()).protectFromEntities()) {
            event.setCancelled(true);
            return;
        }
        this.storage.remove(placed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        this.storage.find(clicked).ifPresent(placed -> {
            placed.customBlock().onBlockInteract(event);
            if (event.useInteractedBlock() != Event.Result.DENY) {
                this.advancements.triggerCustomItem(
                        event.getPlayer(),
                        placed.customBlock(),
                        CustomItemTrigger.BLOCK_INTERACT,
                        event
                );
            }
        });
    }

    private void persistPlacement(BlockPlaceEvent event, Collection<Block> blocks) {
        Optional<CustomBlock> custom = customBlock(event.getItemInHand());
        if (custom.isEmpty()) {
            for (Block block : blocks) {
                this.storage.removeAt(block);
            }
            return;
        }
        this.storage.storePlacement(
                blocks, event.getBlockPlaced(), event.getItemInHand()
        );
    }

    private void handleExplosion(List<Block> affectedBlocks) {
        handleExplosion(affectedBlocks, null);
    }

    private void handleExplosion(List<Block> affectedBlocks, Block source) {
        List<Block> candidates = new ArrayList<>(affectedBlocks);
        if (source != null) {
            candidates.add(source);
        }
        Map<String, PlacedCustomBlock> groups = findGroups(candidates);
        if (groups.isEmpty()) {
            return;
        }

        affectedBlocks.removeIf(block -> this.storage.find(block).isPresent());
        for (PlacedCustomBlock placed : groups.values()) {
            this.storage.remove(placed);
            clearMembers(placed);
            if (settingsFor(placed.customBlock()).dropFromExplosions()) {
                drop(placed.primary(), placed.item());
            }
        }
    }

    private void handlePiston(
            List<Block> movingBlocks,
            int moveX,
            int moveY,
            int moveZ,
            org.bukkit.event.Cancellable event
    ) {
        Map<String, PlacedCustomBlock> groups = findGroups(movingBlocks);
        if (groups.isEmpty()) {
            return;
        }
        if (groups.values().stream().anyMatch(
                placed -> !settingsFor(placed.customBlock()).moveWithPistons())) {
            event.setCancelled(true);
            return;
        }

        java.util.Set<String> movingLocations = movingBlocks.stream()
                .map(CustomBlockManager::blockIdentity)
                .collect(java.util.stream.Collectors.toSet());
        boolean partialGroup = groups.values().stream()
                .flatMap(placed -> placed.members().stream())
                .map(CustomBlockManager::blockIdentity)
                .anyMatch(location -> !movingLocations.contains(location));
        if (partialGroup) {
            event.setCancelled(true);
            return;
        }

        List<PlacedCustomBlock> moving = List.copyOf(groups.values());
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (!event.isCancelled()) {
                this.storage.move(moving, moveX, moveY, moveZ);
            }
        });
    }

    private void handleNaturalDestruction(Block block) {
        this.storage.find(block).ifPresent(placed -> {
            this.storage.remove(placed);
            clearOtherMembers(placed, block);
            if (settingsFor(placed.customBlock()).dropFromNaturalDestruction()) {
                drop(block, placed.item());
            }
        });
    }

    private Map<String, PlacedCustomBlock> findGroups(Collection<Block> blocks) {
        Map<String, PlacedCustomBlock> groups = new LinkedHashMap<>();
        for (Block block : blocks) {
            this.storage.find(block).ifPresent(
                    placed -> groups.putIfAbsent(placed.identity(), placed)
            );
        }
        return groups;
    }

    private CustomBlockSettings settingsFor(CustomBlock block) {
        return this.settings.getOrDefault(normalizedId(block), CustomBlockSettings.defaults());
    }

    private CustomBlockSettings readSettings(String itemId) {
        CustomBlockSettings fallback = CustomBlockSettings.defaults();
        ConfigurationSection defaults = this.plugin.getConfig()
                .getConfigurationSection("custom-blocks.defaults");
        CustomBlockSettings base = readSettings(defaults, fallback);
        ConfigurationSection override = this.plugin.getConfig()
                .getConfigurationSection("custom-blocks.overrides." + itemId);
        return readSettings(override, base);
    }

    private static CustomBlockSettings readSettings(
            ConfigurationSection section,
            CustomBlockSettings fallback
    ) {
        if (section == null) {
            return fallback;
        }
        return new CustomBlockSettings(
                getBoolean(section, "drop-in-creative", fallback.dropInCreative()),
                getBoolean(section, "require-correct-tool", fallback.requireCorrectTool()),
                getBoolean(section, "drop-from-explosions", fallback.dropFromExplosions()),
                getBoolean(section, "drop-from-natural-destruction",
                        fallback.dropFromNaturalDestruction()),
                getBoolean(section, "move-with-pistons", fallback.moveWithPistons()),
                getBoolean(section, "protect-from-entities", fallback.protectFromEntities()),
                Math.max(0, section.getInt("experience", fallback.experience()))
        );
    }

    private static boolean getBoolean(
            ConfigurationSection section,
            String path,
            boolean fallback
    ) {
        return section.isBoolean(path) ? section.getBoolean(path) : fallback;
    }

    private static Optional<CustomBlock> customBlock(ItemStack item) {
        CustomItem custom = CustomItemFactory.Provide(item);
        return custom instanceof CustomBlock block
                ? Optional.of(block)
                : Optional.empty();
    }

    private static String normalizedId(CustomBlock block) {
        return block.getItemId().toLowerCase(java.util.Locale.ROOT);
    }

    private static void clearOtherMembers(PlacedCustomBlock placed, Block excluded) {
        for (Block member : placed.members()) {
            if (!sameBlock(member, excluded)) {
                member.setType(Material.AIR, false);
            }
        }
    }

    private static void clearMembers(PlacedCustomBlock placed) {
        for (Block member : placed.members()) {
            member.setType(Material.AIR, false);
        }
    }

    private static boolean sameBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }

    private static String blockIdentity(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private static Item drop(Block block, ItemStack item) {
        return block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5), item.clone()
        );
    }
}
