package com.sergicraftero.escaperoom.util;

import org.bukkit.Material;

/** Material helpers that only rely on the enum (no registry lookups). */
public final class Blocks {

    private Blocks() {
    }

    public static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}
