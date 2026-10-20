package com.sergicraftero.escaperoom.command;

import com.sergicraftero.escaperoom.util.BlockPos;
import com.sergicraftero.escaperoom.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-admin two-corner selections (wand or /escape pos1|pos2), with WorldEdit fallback. */
public final class Selections {

    private final Map<UUID, Location[]> selections = new HashMap<>();

    public void pos1(Player player, Location loc) {
        selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2])[0] = loc.clone();
    }

    public void pos2(Player player, Location loc) {
        selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2])[1] = loc.clone();
    }

    /** Returns the selected cuboid, or null if incomplete or in different worlds. */
    public Cuboid get(Player player) {
        Location[] sel = selections.get(player.getUniqueId());
        if (sel != null && sel[0] != null && sel[1] != null && sel[0].getWorld().equals(sel[1].getWorld())) {
            return Cuboid.of(BlockPos.of(sel[0]), BlockPos.of(sel[1]));
        }
        return worldEditSelection(player);
    }

    private static Cuboid worldEditSelection(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                && !Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            return null;
        }
        try {
            return WorldEditSelection.of(player);
        } catch (Throwable t) {
            return null;
        }
    }
}
