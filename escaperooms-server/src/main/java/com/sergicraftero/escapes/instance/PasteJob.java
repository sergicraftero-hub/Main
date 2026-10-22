package com.sergicraftero.escapes.instance;

import com.sergicraftero.escapes.template.Template;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Writes a template into the world a slice at a time (so a 500k-block room never freezes the server),
 * then applies tile data (sign text, container contents, lectern books).
 */
public final class PasteJob {

    private final Template template;
    private final BlockData[] palette;
    private final World world;
    private final int ox;
    private final int oy;
    private final int oz;
    private final boolean skipAir;
    private final Function<String, ItemStack> itemResolver;
    private final Runnable onDone;
    private int cursor;
    private boolean tilesDone;

    /**
     * @param skipAir true when the target area is known to be empty (first paste in a fresh world)
     */
    public PasteJob(Template template, BlockData[] palette, World world, int ox, int oy, int oz, boolean skipAir,
                    Function<String, ItemStack> itemResolver, Runnable onDone) {
        this.template = template;
        this.palette = palette;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.skipAir = skipAir;
        this.itemResolver = itemResolver;
        this.onDone = onDone;
    }

    public double progress() {
        return tilesDone ? 1.0 : (double) cursor / template.volume();
    }

    /** Places up to {@code budget} blocks. Returns true when finished. */
    public boolean step(int budget) {
        int[] blocks = template.rawBlocks();
        int sx = template.sizeX();
        int sz = template.sizeZ();
        int end = Math.min(blocks.length, cursor + budget);
        while (cursor < end) {
            int id = blocks[cursor];
            int idx = cursor++;
            if (id == 0 && skipAir) {
                continue;
            }
            int x = idx % sx;
            int rest = idx / sx;
            int z = rest % sz;
            int y = rest / sz;
            Block b = world.getBlockAt(ox + x, oy + y, oz + z);
            BlockData data = palette[id];
            if (id == 0) {
                if (b.getType() != Material.AIR) {
                    b.setType(Material.AIR, false);
                }
                continue;
            }
            if (!b.getBlockData().equals(data)) {
                b.setBlockData(data, false);
            }
        }
        if (cursor >= blocks.length && !tilesDone) {
            applyTiles();
            tilesDone = true;
            if (onDone != null) {
                onDone.run();
            }
        }
        return tilesDone;
    }

    private void applyTiles() {
        for (Map.Entry<Long, Map<String, Object>> e : template.tiles().entrySet()) {
            long key = e.getKey();
            Block b = world.getBlockAt(ox + Template.keyX(key), oy + Template.keyY(key), oz + Template.keyZ(key));
            try {
                applyTile(b, e.getValue(), itemResolver);
            } catch (RuntimeException ex) {
                // A broken tile must never stop the room from loading.
                org.bukkit.Bukkit.getLogger().warning("[EscapeRooms] Tile at " + b.getX() + "," + b.getY() + ","
                        + b.getZ() + " failed: " + ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void applyTile(Block b, Map<String, Object> data, Function<String, ItemStack> itemResolver) {
        String type = String.valueOf(data.get("type"));
        BlockState state = b.getState();
        switch (type) {
            case "sign" -> {
                if (state instanceof Sign sign) {
                    List<Object> lines = (List<Object>) data.getOrDefault("lines", List.of());
                    for (int i = 0; i < 4; i++) {
                        Component c = i < lines.size() ? MiniMessage.miniMessage().deserialize(String.valueOf(lines.get(i)))
                                : Component.empty();
                        sign.getSide(Side.FRONT).line(i, c);
                    }
                    List<Object> back = (List<Object>) data.get("back");
                    if (back != null) {
                        for (int i = 0; i < 4; i++) {
                            Component c = i < back.size() ? MiniMessage.miniMessage().deserialize(String.valueOf(back.get(i)))
                                    : Component.empty();
                            sign.getSide(Side.BACK).line(i, c);
                        }
                    }
                    if (Boolean.TRUE.equals(data.get("glow"))) {
                        sign.getSide(Side.FRONT).setGlowingText(true);
                    }
                    com.sergicraftero.escapes.util.Cosmetic.apply(() -> sign.setWaxed(true)); // players can't edit it
                    sign.update(true, false);
                }
            }
            case "container" -> {
                if (state instanceof Container container) {
                    Inventory inv = container.getSnapshotInventory();
                    inv.clear();
                    Object bytes = data.get("bytes");
                    if (bytes instanceof List<?> encoded) {
                        for (int i = 0; i < encoded.size() && i < inv.getSize(); i++) {
                            String s = String.valueOf(encoded.get(i));
                            if (!s.isEmpty()) {
                                inv.setItem(i, ItemStack.deserializeBytes(Base64.getDecoder().decode(s)));
                            }
                        }
                    }
                    Object items = data.get("items");
                    if (items instanceof List<?> specs) {
                        int slot = 0;
                        for (Object spec : specs) {
                            ItemStack item = itemResolver.apply(String.valueOf(spec));
                            if (item != null && slot < inv.getSize()) {
                                inv.setItem(slot, item);
                            }
                            slot += (inv.getSize() >= 27 ? 2 : 1); // spread items so chests look natural
                            if (slot >= inv.getSize()) {
                                slot = 1;
                            }
                        }
                    }
                    container.update(true, false);
                }
            }
            case "lectern" -> {
                if (state instanceof Lectern lectern) {
                    Object book = data.get("book");
                    ItemStack item = null;
                    if (book != null && String.valueOf(book).startsWith("item:")) {
                        item = itemResolver.apply(String.valueOf(book));
                    } else if (data.get("bytes") instanceof String s && !s.isEmpty()) {
                        item = ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
                    }
                    if (item != null) {
                        setLecternBook(b, item);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Puts a book on a lectern and flips its has_book state so the book is visible and readable. */
    public static void setLecternBook(Block b, ItemStack book) {
        String data = b.getBlockData().getAsString();
        if (data.contains("has_book=false")) {
            try {
                b.setBlockData(org.bukkit.Bukkit.createBlockData(data.replace("has_book=false", "has_book=true")), false);
            } catch (IllegalArgumentException ignored) {
                // the server can't parse its own state string (test servers): the book still goes in
            }
        }
        if (b.getState() instanceof Lectern lectern) {
            lectern.getInventory().setItem(0, book);
        }
    }
}
