package com.fifty.dev.blocks;

import com.fifty.dev.api.CustomBlock;
import com.fifty.dev.api.CustomItem;
import com.fifty.dev.api.CustomItemFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CustomBlockStorage {
    private static final byte FORMAT_VERSION = 1;
    private static final byte PRIMARY = 1;
    private static final byte REFERENCE = 2;
    private static final int MAX_GROUP_SIZE = 64;
    private static final int MAX_ITEM_BYTES = 1_048_576;

    private final JavaPlugin plugin;

    CustomBlockStorage(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    void storePlacement(Collection<Block> placedBlocks, Block primary, ItemStack source) {
        Map<String, Block> unique = new LinkedHashMap<>();
        unique.put(blockIdentity(primary), primary);
        for (Block block : placedBlocks) {
            unique.put(blockIdentity(block), block);
        }

        if (unique.size() > MAX_GROUP_SIZE) {
            throw new IllegalArgumentException("A custom block group cannot exceed "
                    + MAX_GROUP_SIZE + " blocks");
        }

        List<Block> members = List.copyOf(unique.values());
        for (Block member : members) {
            find(member).ifPresent(this::remove);
        }

        ItemStack item = source.asOne();
        write(primary, encodePrimary(item, members));
        for (Block member : members) {
            if (!sameBlock(member, primary)) {
                write(member, encodeReference(primary));
            }
        }
    }

    java.util.Optional<PlacedCustomBlock> find(Block block) {
        byte[] raw = read(block);
        if (raw == null) {
            return java.util.Optional.empty();
        }

        try {
            Decoded decoded = decode(raw);
            if (decoded.reference() != null) {
                Block primary = block.getWorld().getBlockAt(
                        decoded.reference().x(),
                        decoded.reference().y(),
                        decoded.reference().z()
                );
                byte[] primaryRaw = read(primary);
                if (primaryRaw == null) {
                    removeDirect(block);
                    return java.util.Optional.empty();
                }
                decoded = decode(primaryRaw);
                if (decoded.reference() != null) {
                    throw new IOException("Reference points to another reference");
                }
            }

            ItemStack item = Objects.requireNonNull(decoded.item());
            CustomItem registered = CustomItemFactory.Provide(item);
            if (!(registered instanceof CustomBlock customBlock)) {
                return java.util.Optional.empty();
            }

            List<Block> members = decoded.positions().stream()
                    .map(position -> block.getWorld().getBlockAt(
                            position.x(), position.y(), position.z()))
                    .toList();
            Block primary = members.getFirst();
            return java.util.Optional.of(
                    new PlacedCustomBlock(primary, members, item, customBlock)
            );
        } catch (IOException | RuntimeException exception) {
            this.plugin.getLogger().warning(
                    "Discarding invalid custom block data at "
                            + describe(block) + ": " + exception.getMessage()
            );
            removeDirect(block);
            return java.util.Optional.empty();
        }
    }

    void remove(PlacedCustomBlock placed) {
        for (Block member : placed.members()) {
            removeDirect(member);
        }
    }

    void removeAt(Block block) {
        find(block).ifPresentOrElse(this::remove, () -> removeDirect(block));
    }

    void move(Collection<PlacedCustomBlock> groups, int x, int y, int z) {
        List<Move> moves = groups.stream()
                .map(group -> new Move(
                        group,
                        group.members().stream()
                                .map(block -> block.getRelative(x, y, z))
                                .toList(),
                        group.primary().getRelative(x, y, z)
                ))
                .toList();

        for (Move move : moves) {
            remove(move.source());
        }
        for (Move move : moves) {
            storePlacement(move.destinations(), move.primary(), move.source().item());
        }
    }

    private byte[] read(Block block) {
        return block.getChunk().getPersistentDataContainer().get(
                key(block), PersistentDataType.BYTE_ARRAY
        );
    }

    private void write(Block block, byte[] value) {
        block.getChunk().getPersistentDataContainer().set(
                key(block), PersistentDataType.BYTE_ARRAY, value
        );
    }

    private void removeDirect(Block block) {
        block.getChunk().getPersistentDataContainer().remove(key(block));
    }

    private NamespacedKey key(Block block) {
        int localX = Math.floorMod(block.getX(), 16);
        int localZ = Math.floorMod(block.getZ(), 16);
        return new NamespacedKey(
                this.plugin,
                "custom_blocks/" + localX + "/" + block.getY() + "/" + localZ
        );
    }

    private static byte[] encodePrimary(ItemStack item, List<Block> members) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(FORMAT_VERSION);
                output.writeByte(PRIMARY);
                byte[] itemBytes = item.serializeAsBytes();
                output.writeInt(itemBytes.length);
                output.write(itemBytes);
                output.writeInt(members.size());
                for (Block member : members) {
                    output.writeInt(member.getX());
                    output.writeInt(member.getY());
                    output.writeInt(member.getZ());
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not encode custom block", impossible);
        }
    }

    private static byte[] encodeReference(Block primary) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(FORMAT_VERSION);
                output.writeByte(REFERENCE);
                output.writeInt(primary.getX());
                output.writeInt(primary.getY());
                output.writeInt(primary.getZ());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not encode custom block reference", impossible);
        }
    }

    private static Decoded decode(byte[] raw) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (input.readByte() != FORMAT_VERSION) {
                throw new IOException("Unsupported storage version");
            }
            byte kind = input.readByte();
            if (kind == REFERENCE) {
                return new Decoded(
                        null,
                        List.of(),
                        new Position(input.readInt(), input.readInt(), input.readInt())
                );
            }
            if (kind != PRIMARY) {
                throw new IOException("Unknown storage record type");
            }

            int itemLength = input.readInt();
            if (itemLength <= 0 || itemLength > MAX_ITEM_BYTES) {
                throw new IOException("Invalid stored item length");
            }
            byte[] itemBytes = input.readNBytes(itemLength);
            if (itemBytes.length != itemLength) {
                throw new IOException("Stored item data is truncated");
            }
            ItemStack item = ItemStack.deserializeBytes(itemBytes);
            int memberCount = input.readInt();
            if (memberCount <= 0 || memberCount > MAX_GROUP_SIZE) {
                throw new IOException("Invalid custom block group size");
            }
            List<Position> positions = new ArrayList<>(memberCount);
            for (int index = 0; index < memberCount; index++) {
                positions.add(new Position(
                        input.readInt(), input.readInt(), input.readInt()
                ));
            }
            return new Decoded(item, List.copyOf(positions), null);
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

    private static String describe(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }

    private record Position(int x, int y, int z) {
    }

    private record Decoded(
            ItemStack item,
            List<Position> positions,
            Position reference
    ) {
    }

    private record Move(
            PlacedCustomBlock source,
            List<Block> destinations,
            Block primary
    ) {
    }
}
