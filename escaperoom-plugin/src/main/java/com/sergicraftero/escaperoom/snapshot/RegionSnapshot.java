package com.sergicraftero.escaperoom.snapshot;

import com.sergicraftero.escaperoom.util.Cuboid;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Copy of every block of a region plus the contents of containers, lecterns and signs.
 * Capturing and restoring are spread over several ticks so the server never freezes; files are
 * read and written asynchronously.
 */
public final class RegionSnapshot {

    private static final int VERSION = 1;

    /** Extra data for block entities. */
    record Tile(ItemStack[] items, int page, String[] lines, boolean waxed, String frontColor, String backColor,
                boolean frontGlow, boolean backGlow) {
    }

    private final String worldName;
    private final Cuboid region;
    private final List<BlockData> palette;
    private final int[] blocks;
    private final Map<Integer, Tile> tiles;

    private RegionSnapshot(String worldName, Cuboid region, List<BlockData> palette, int[] blocks, Map<Integer, Tile> tiles) {
        this.worldName = worldName;
        this.region = region;
        this.palette = palette;
        this.blocks = blocks;
        this.tiles = tiles;
    }

    public Cuboid region() {
        return region;
    }

    public String worldName() {
        return worldName;
    }

    private int index(int x, int y, int z) {
        return ((y - region.minY()) * region.sizeZ() + (z - region.minZ())) * region.sizeX() + (x - region.minX());
    }

    // ───────────────────────── capture ─────────────────────────

    public static void capture(Plugin plugin, World world, Cuboid region, int perTick, Consumer<RegionSnapshot> done) {
        int volume = (int) region.volume();
        int[] blocks = new int[volume];
        List<BlockData> palette = new ArrayList<>();
        Map<BlockData, Integer> paletteIndex = new HashMap<>();
        Map<Integer, Tile> tiles = new LinkedHashMap<>();
        RegionSnapshot snapshot = new RegionSnapshot(world.getName(), region, palette, blocks, tiles);

        new BukkitRunnable() {
            int cursor = 0;

            @Override
            public void run() {
                int end = Math.min(volume, cursor + perTick);
                int sx = region.sizeX();
                int sz = region.sizeZ();
                for (; cursor < end; cursor++) {
                    int x = region.minX() + cursor % sx;
                    int z = region.minZ() + (cursor / sx) % sz;
                    int y = region.minY() + cursor / (sx * sz);
                    BlockData data = world.getBlockAt(x, y, z).getBlockData();
                    Integer idx = paletteIndex.get(data);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(data);
                        paletteIndex.put(data, idx);
                    }
                    blocks[cursor] = idx;
                }
                if (cursor >= volume) {
                    cancel();
                    captureTiles(world, snapshot);
                    done.accept(snapshot);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void captureTiles(World world, RegionSnapshot snapshot) {
        Cuboid r = snapshot.region;
        for (int cx = r.minX() >> 4; cx <= r.maxX() >> 4; cx++) {
            for (int cz = r.minZ() >> 4; cz <= r.maxZ() >> 4; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                for (BlockState state : chunk.getTileEntities()) {
                    if (!r.contains(state.getX(), state.getY(), state.getZ())) {
                        continue;
                    }
                    Tile tile = readTile(state);
                    if (tile != null) {
                        snapshot.tiles.put(snapshot.index(state.getX(), state.getY(), state.getZ()), tile);
                    }
                }
            }
        }
    }

    private static Tile readTile(BlockState state) {
        if (state instanceof Container container) {
            return new Tile(container.getSnapshotInventory().getContents(), 0, null, false, null, null, false, false);
        }
        if (state instanceof Lectern lectern) {
            return new Tile(lectern.getSnapshotInventory().getContents(), lectern.getPage(), null, false, null, null, false, false);
        }
        if (state instanceof ChiseledBookshelf shelf) {
            return new Tile(shelf.getSnapshotInventory().getContents(), 0, null, false, null, null, false, false);
        }
        if (state instanceof Sign sign) {
            String[] lines = new String[8];
            SignSide front = sign.getSide(Side.FRONT);
            SignSide back = sign.getSide(Side.BACK);
            for (int i = 0; i < 4; i++) {
                lines[i] = GsonComponentSerializer.gson().serialize(front.line(i));
                lines[4 + i] = GsonComponentSerializer.gson().serialize(back.line(i));
            }
            return new Tile(null, 0, lines, sign.isWaxed(),
                    front.getColor() == null ? null : front.getColor().name(),
                    back.getColor() == null ? null : back.getColor().name(),
                    front.isGlowingText(), back.isGlowingText());
        }
        return null;
    }

    // ───────────────────────── restore ─────────────────────────

    public void restore(Plugin plugin, int perTick, Runnable done) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            done.run();
            return;
        }
        int volume = blocks.length;
        new BukkitRunnable() {
            int cursor = 0;

            @Override
            public void run() {
                int end = Math.min(volume, cursor + perTick);
                int sx = region.sizeX();
                int sz = region.sizeZ();
                for (; cursor < end; cursor++) {
                    int x = region.minX() + cursor % sx;
                    int z = region.minZ() + (cursor / sx) % sz;
                    int y = region.minY() + cursor / (sx * sz);
                    Block block = world.getBlockAt(x, y, z);
                    BlockData saved = palette.get(blocks[cursor]);
                    if (!block.getBlockData().equals(saved)) {
                        block.setBlockData(saved, false);
                    }
                }
                if (cursor >= volume) {
                    cancel();
                    restoreTiles(world);
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void restoreTiles(World world) {
        int sx = region.sizeX();
        int sz = region.sizeZ();
        for (Map.Entry<Integer, Tile> e : tiles.entrySet()) {
            int i = e.getKey();
            Block block = world.getBlockAt(region.minX() + i % sx, region.minY() + i / (sx * sz), region.minZ() + (i / sx) % sz);
            Tile tile = e.getValue();
            BlockState state = block.getState();
            if (state instanceof Container container && tile.items != null) {
                fill(container.getSnapshotInventory(), tile.items);
            } else if (state instanceof Lectern lectern && tile.items != null) {
                fill(lectern.getSnapshotInventory(), tile.items);
                lectern.setPage(tile.page);
            } else if (state instanceof ChiseledBookshelf shelf && tile.items != null) {
                fill(shelf.getSnapshotInventory(), tile.items);
            } else if (state instanceof Sign sign && tile.lines != null) {
                SignSide front = sign.getSide(Side.FRONT);
                SignSide back = sign.getSide(Side.BACK);
                for (int l = 0; l < 4; l++) {
                    front.line(l, parse(tile.lines[l]));
                    back.line(l, parse(tile.lines[4 + l]));
                }
                if (tile.frontColor != null) {
                    front.setColor(DyeColor.valueOf(tile.frontColor));
                }
                if (tile.backColor != null) {
                    back.setColor(DyeColor.valueOf(tile.backColor));
                }
                front.setGlowingText(tile.frontGlow);
                back.setGlowingText(tile.backGlow);
                sign.setWaxed(tile.waxed);
            } else {
                continue;
            }
            state.update(true, false);
        }
    }

    private static Component parse(String json) {
        return json == null ? Component.empty() : GsonComponentSerializer.gson().deserialize(json);
    }

    private static void fill(Inventory inventory, ItemStack[] items) {
        inventory.clear();
        for (int i = 0; i < Math.min(items.length, inventory.getSize()); i++) {
            inventory.setItem(i, items[i] == null ? null : items[i].clone());
        }
    }

    // ───────────────────────── persistence ─────────────────────────

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            out.writeInt(VERSION);
            out.writeUTF(worldName);
            out.writeInt(region.minX());
            out.writeInt(region.minY());
            out.writeInt(region.minZ());
            out.writeInt(region.maxX());
            out.writeInt(region.maxY());
            out.writeInt(region.maxZ());
            out.writeInt(palette.size());
            for (BlockData data : palette) {
                out.writeUTF(data.getAsString());
            }
            out.writeInt(blocks.length);
            for (int b : blocks) {
                out.writeInt(b);
            }
            out.writeInt(tiles.size());
            for (Map.Entry<Integer, Tile> e : tiles.entrySet()) {
                Tile t = e.getValue();
                out.writeInt(e.getKey());
                out.writeInt(t.items == null ? -1 : t.items.length);
                if (t.items != null) {
                    for (ItemStack item : t.items) {
                        byte[] bytes = item == null || item.getType().isAir() ? new byte[0] : item.serializeAsBytes();
                        out.writeInt(bytes.length);
                        out.write(bytes);
                    }
                }
                out.writeInt(t.page);
                out.writeBoolean(t.lines != null);
                if (t.lines != null) {
                    for (String line : t.lines) {
                        out.writeUTF(line == null ? "" : line);
                    }
                    out.writeBoolean(t.waxed);
                    out.writeUTF(t.frontColor == null ? "" : t.frontColor);
                    out.writeUTF(t.backColor == null ? "" : t.backColor);
                    out.writeBoolean(t.frontGlow);
                    out.writeBoolean(t.backGlow);
                }
            }
        }
    }

    /** Reads a snapshot file. Must be called on the main thread (block data parsing). */
    public static RegionSnapshot load(byte[] raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new java.io.ByteArrayInputStream(raw)))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported snapshot version " + version);
            }
            String world = in.readUTF();
            Cuboid region = new Cuboid(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
            int paletteSize = in.readInt();
            List<BlockData> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                String raw2 = in.readUTF();
                BlockData data;
                try {
                    data = Bukkit.createBlockData(raw2);
                } catch (IllegalArgumentException e) {
                    data = Bukkit.createBlockData("minecraft:air");
                }
                palette.add(data);
            }
            int[] blocks = new int[in.readInt()];
            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = in.readInt();
            }
            int tileCount = in.readInt();
            Map<Integer, Tile> tiles = new LinkedHashMap<>();
            for (int i = 0; i < tileCount; i++) {
                int index = in.readInt();
                int itemCount = in.readInt();
                ItemStack[] items = null;
                if (itemCount >= 0) {
                    items = new ItemStack[itemCount];
                    for (int k = 0; k < itemCount; k++) {
                        byte[] bytes = new byte[in.readInt()];
                        in.readFully(bytes);
                        items[k] = bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes);
                    }
                }
                int page = in.readInt();
                String[] lines = null;
                boolean waxed = false;
                String front = null;
                String back = null;
                boolean frontGlow = false;
                boolean backGlow = false;
                if (in.readBoolean()) {
                    lines = new String[8];
                    for (int l = 0; l < 8; l++) {
                        lines[l] = in.readUTF();
                    }
                    waxed = in.readBoolean();
                    front = emptyToNull(in.readUTF());
                    back = emptyToNull(in.readUTF());
                    frontGlow = in.readBoolean();
                    backGlow = in.readBoolean();
                }
                tiles.put(index, new Tile(items, page, lines, waxed, front, back, frontGlow, backGlow));
            }
            return new RegionSnapshot(world, region, palette, blocks, tiles);
        }
    }

    public static byte[] readFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return in.readAllBytes();
        }
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
