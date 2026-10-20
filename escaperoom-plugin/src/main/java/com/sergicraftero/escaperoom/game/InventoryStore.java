package com.sergicraftero.escaperoom.game;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.util.LocUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Saves a player's inventory, XP, food and game mode to disk before a game and restores it
 * afterwards. Because the data lives in a file it survives disconnects and server crashes:
 * pending data is restored on the player's next join.
 */
public final class InventoryStore {

    private final EscapeRoomPlugin plugin;
    private final File folder;

    public InventoryStore(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "inventories");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create inventories folder");
        }
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    public boolean has(UUID uuid) {
        return file(uuid).exists();
    }

    public void save(Player player, Location returnLocation) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", player.getName());
        yaml.set("contents", encode(player.getInventory().getContents()));
        yaml.set("ender", encode(player.getEnderChest().getContents()));
        yaml.set("gamemode", player.getGameMode().name());
        yaml.set("level", player.getLevel());
        yaml.set("exp", (double) player.getExp());
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", (double) player.getSaturation());
        yaml.set("allow-flight", player.getAllowFlight());
        if (returnLocation != null && returnLocation.getWorld() != null) {
            yaml.set("return.world", returnLocation.getWorld().getName());
            yaml.set("return.location", LocUtil.serialize(returnLocation));
        }
        try {
            yaml.save(file(player.getUniqueId()));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save inventory of " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Restores saved data and deletes the file.
     *
     * @return the stored return location, or null if nothing was stored
     */
    public Location restore(Player player) {
        File file = file(player.getUniqueId());
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        player.getInventory().setContents(decode(yaml.getStringList("contents"), player.getInventory().getSize()));
        if (yaml.contains("ender")) {
            player.getEnderChest().setContents(decode(yaml.getStringList("ender"), player.getEnderChest().getSize()));
        }
        player.setItemOnCursor(null);
        try {
            player.setGameMode(GameMode.valueOf(yaml.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setLevel(yaml.getInt("level"));
        player.setExp((float) yaml.getDouble("exp"));
        player.setFoodLevel(yaml.getInt("food", 20));
        player.setSaturation((float) yaml.getDouble("saturation", 5));
        player.setAllowFlight(yaml.getBoolean("allow-flight", player.getAllowFlight()));
        player.updateInventory();
        Location back = null;
        World world = Bukkit.getWorld(yaml.getString("return.world", ""));
        if (world != null) {
            back = LocUtil.parse(world, yaml.getString("return.location"));
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete inventory file of " + player.getName());
        }
        return back;
    }

    private static List<String> encode(ItemStack[] items) {
        List<String> out = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            out.add(item == null || item.getType().isAir() ? ""
                    : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        return out;
    }

    private static ItemStack[] decode(List<String> encoded, int size) {
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < Math.min(size, encoded.size()); i++) {
            String raw = encoded.get(i);
            if (raw != null && !raw.isEmpty()) {
                items[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
            }
        }
        return items;
    }
}
