package com.sergicraftero.escaperoom.command;

import com.sergicraftero.escaperoom.util.Cuboid;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;

/** Isolated so WorldEdit classes are only loaded when WorldEdit is installed. */
final class WorldEditSelection {

    private WorldEditSelection() {
    }

    static Cuboid of(Player player) throws Exception {
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
        Region region = WorldEdit.getInstance().getSessionManager().get(wePlayer).getSelection(wePlayer.getWorld());
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return new Cuboid(min.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockX(), max.getBlockY(), max.getBlockZ());
    }
}
