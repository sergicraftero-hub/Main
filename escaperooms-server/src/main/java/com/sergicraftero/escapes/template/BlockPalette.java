package com.sergicraftero.escapes.template;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Converts a template palette into Bukkit block data, tolerating ids renamed between Minecraft versions. */
public final class BlockPalette {

    /** Old id → new id (and back), tried when an id is rejected by the running server. */
    private static final Map<String, String> ALIASES = Map.of(
            "minecraft:chain", "minecraft:iron_chain",
            "minecraft:iron_chain", "minecraft:chain",
            "minecraft:grass", "minecraft:short_grass",
            "minecraft:short_grass", "minecraft:grass");

    private BlockPalette() {
    }

    public static BlockData[] convert(List<String> palette, Logger log, String context) {
        BlockData[] out = new BlockData[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            out[i] = parse(palette.get(i), log, context);
        }
        return out;
    }

    public static BlockData parse(String data, Logger log, String context) {
        try {
            return Bukkit.createBlockData(data);
        } catch (IllegalArgumentException first) {
            int bracket = data.indexOf('[');
            String id = bracket < 0 ? data : data.substring(0, bracket);
            String state = bracket < 0 ? "" : data.substring(bracket);
            String alias = ALIASES.get(id);
            if (alias != null) {
                try {
                    return Bukkit.createBlockData(alias + state);
                } catch (IllegalArgumentException ignored) {
                    // fall through
                }
            }
            // Same block without the state (a property name changed): keep the shape at least.
            try {
                return Bukkit.createBlockData(alias != null ? alias : id);
            } catch (IllegalArgumentException ignored) {
                log.warning("[" + context + "] Unknown block '" + data + "', using stone instead.");
                return Material.STONE.createBlockData();
            }
        }
    }
}
