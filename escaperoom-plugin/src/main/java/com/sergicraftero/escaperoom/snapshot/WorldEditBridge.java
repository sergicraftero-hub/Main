package com.sergicraftero.escaperoom.snapshot;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.util.Cuboid;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.function.Consumer;

/** Room snapshots through WorldEdit schematics (asynchronous when FAWE is installed). */
final class WorldEditBridge {

    private final EscapeRoomPlugin plugin;
    private final File folder;
    private final boolean async;

    WorldEditBridge(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "snapshots");
        this.async = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        // Touch a WorldEdit class so a broken install fails here, not later.
        WorldEdit.getInstance();
    }

    private File file(String name) {
        return new File(folder, name + ".schem");
    }

    boolean has(String name) {
        return file(name).exists();
    }

    void delete(String name) {
        if (file(name).exists() && !file(name).delete()) {
            plugin.getLogger().warning("Could not delete schematic " + name);
        }
    }

    private static ClipboardFormat format() throws IllegalStateException {
        ClipboardFormat format = ClipboardFormats.findByAlias("sponge");
        if (format == null) {
            format = ClipboardFormats.findByAlias("schem");
        }
        if (format == null) {
            throw new IllegalStateException("No schematic format available in this WorldEdit version");
        }
        return format;
    }

    void capture(String name, World world, Cuboid region, Consumer<Boolean> done) {
        run(() -> {
            CuboidRegion weRegion = new CuboidRegion(BukkitAdapter.adapt(world),
                    BlockVector3.at(region.minX(), region.minY(), region.minZ()),
                    BlockVector3.at(region.maxX(), region.maxY(), region.maxZ()));
            BlockArrayClipboard clipboard = new BlockArrayClipboard(weRegion);
            try (EditSession session = WorldEdit.getInstance().newEditSession(weRegion.getWorld())) {
                ForwardExtentCopy copy = new ForwardExtentCopy(session, weRegion, clipboard, weRegion.getMinimumPoint());
                copy.setCopyingEntities(false);
                Operations.complete(copy);
            }
            try (ClipboardWriter writer = format().getWriter(new FileOutputStream(file(name)))) {
                writer.write(clipboard);
            }
        }, done);
    }

    void restore(String name, World world, Consumer<Boolean> done) {
        run(() -> {
            File file = file(name);
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                format = format();
            }
            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                clipboard = reader.read();
            }
            try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operation operation = new ClipboardHolder(clipboard).createPaste(session)
                        .to(clipboard.getRegion().getMinimumPoint()).ignoreAirBlocks(false).build();
                Operations.complete(operation);
            }
        }, done);
    }

    private interface Task {
        void run() throws Exception;
    }

    private void run(Task task, Consumer<Boolean> done) {
        Runnable body = () -> {
            boolean ok = true;
            try {
                task.run();
            } catch (Exception e) {
                ok = false;
                plugin.getLogger().severe("WorldEdit snapshot operation failed: " + e.getMessage());
            }
            boolean result = ok;
            if (Bukkit.isPrimaryThread()) {
                done.accept(result);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> done.accept(result));
            }
        };
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, body);
        } else {
            body.run();
        }
    }
}
