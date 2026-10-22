package com.sergicraftero.escapes.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A room (or lobby) as raw block data: a box of {@code sizeX × sizeY × sizeZ} blocks stored as palette indices,
 * plus named markers and per-block tile data (sign text, chest contents…).
 * <p>
 * Pure Java on purpose: builders and tests use it without a server. Block data are vanilla strings such as
 * {@code minecraft:oak_stairs[facing=north]}; index 0 of the palette is always air.
 */
public final class Template {

    public static final String AIR = "minecraft:air";

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] blocks;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private final Map<String, Marker> markers = new LinkedHashMap<>();
    private final Map<Long, Map<String, Object>> tiles = new LinkedHashMap<>();

    public Template(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Invalid template size " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new int[sizeX * sizeY * sizeZ];
        paletteId(AIR);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int volume() {
        return blocks.length;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public int paletteId(String data) {
        String normalized = normalize(data);
        Integer id = paletteIndex.get(normalized);
        if (id == null) {
            id = palette.size();
            palette.add(normalized);
            paletteIndex.put(normalized, id);
        }
        return id;
    }

    /** Adds the {@code minecraft:} namespace when missing so equal blocks share one palette entry. */
    public static String normalize(String data) {
        String d = data.trim();
        int bracket = d.indexOf('[');
        String id = bracket < 0 ? d : d.substring(0, bracket);
        if (!id.contains(":")) {
            d = "minecraft:" + d;
        }
        return d;
    }

    public void set(int x, int y, int z, String data) {
        if (!inside(x, y, z)) {
            return; // builders may sketch past the border; clipping keeps them simple
        }
        blocks[index(x, y, z)] = paletteId(data);
    }

    public void setId(int x, int y, int z, int paletteId) {
        if (inside(x, y, z)) {
            blocks[index(x, y, z)] = paletteId;
        }
    }

    public String get(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return AIR;
        }
        return palette.get(blocks[index(x, y, z)]);
    }

    public int getId(int x, int y, int z) {
        return inside(x, y, z) ? blocks[index(x, y, z)] : 0;
    }

    /** Block id without state, e.g. {@code minecraft:oak_door}. */
    public String material(int x, int y, int z) {
        String d = get(x, y, z);
        int bracket = d.indexOf('[');
        return bracket < 0 ? d : d.substring(0, bracket);
    }

    public List<String> palette() {
        return Collections.unmodifiableList(palette);
    }

    public int[] rawBlocks() {
        return blocks;
    }

    // ───────────────────────── markers ─────────────────────────

    public Map<String, Marker> markers() {
        return markers;
    }

    public Marker marker(String name) {
        return markers.get(name);
    }

    public void mark(String name, int x, int y, int z) {
        markers.put(name, Marker.point(x, y, z, null));
    }

    public void mark(String name, int x, int y, int z, String facing) {
        markers.put(name, Marker.point(x, y, z, facing));
    }

    public void markBox(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        markers.put(name, Marker.box(x1, y1, z1, x2, y2, z2));
    }

    // ───────────────────────── tiles ─────────────────────────

    public static long key(int x, int y, int z) {
        return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
    }

    public static int keyX(long key) {
        return (int) (key & 0x1FFFFF);
    }

    public static int keyY(long key) {
        return (int) ((key >>> 21) & 0x1FFFFF);
    }

    public static int keyZ(long key) {
        return (int) ((key >>> 42) & 0x1FFFFF);
    }

    public Map<Long, Map<String, Object>> tiles() {
        return tiles;
    }

    public void tile(int x, int y, int z, Map<String, Object> data) {
        if (inside(x, y, z)) {
            tiles.put(key(x, y, z), data);
        }
    }

    public Map<String, Object> tile(int x, int y, int z) {
        return tiles.get(key(x, y, z));
    }

    /** Sign text as MiniMessage lines (front side, up to four). */
    public void signText(int x, int y, int z, String... lines) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "sign");
        data.put("lines", List.of(lines));
        tile(x, y, z, data);
    }

    /** Container contents as item specs ({@code material} or {@code material:amount}). */
    public void containerItems(int x, int y, int z, String... items) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "container");
        data.put("items", List.of(items));
        tile(x, y, z, data);
    }

    // ───────────────────────── marker type ─────────────────────────

    /** A named point or box inside the template, in template-relative coordinates. */
    public record Marker(int x, int y, int z, int x2, int y2, int z2, String facing) {

        public static Marker point(int x, int y, int z, String facing) {
            return new Marker(x, y, z, x, y, z, facing);
        }

        public static Marker box(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Marker(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), null);
        }

        public boolean isBox() {
            return x != x2 || y != y2 || z != z2;
        }

        public boolean contains(int px, int py, int pz) {
            return px >= x && px <= x2 && py >= y && py <= y2 && pz >= z && pz <= z2;
        }
    }
}
