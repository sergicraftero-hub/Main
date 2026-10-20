package com.sergicraftero.escaperoom.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Immutable integer block coordinate, serialized as "x,y,z". */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPos parse(String raw) {
        String[] parts = raw.replace(" ", "").split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid block position: " + raw);
        }
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public Block block(World world) {
        return world.getBlockAt(x, y, z);
    }

    public Location center(World world) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    public String serialize() {
        return x + "," + y + "," + z;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
