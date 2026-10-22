package com.sergicraftero.escapes.lobby;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Keys;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Lobby hotbar (menu compass, profile, shop...) and the in-game notebook, defined in {@code menus.yml}:
 * <pre>
 * hotbar:
 *   menu: {slot: 0, action: "menu:main", item: {material: COMPASS, name: "..."}}
 * notebook: {material: WRITABLE_BOOK, name: "..."}
 * </pre>
 */
public final class Hotbar {

    private final EscapePlugin plugin;

    public Hotbar(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void giveLobby(Player p) {
        p.getInventory().clear();
        ConfigurationSection hotbar = plugin.menus().getConfigurationSection("hotbar");
        if (hotbar != null) {
            for (String key : hotbar.getKeys(false)) {
                ConfigurationSection s = hotbar.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                ItemStack it = ItemSpec.parse(null, s.getConfigurationSection("item")).create("player", p.getName());
                ItemMeta meta = it.getItemMeta();
                Keys.set(meta, Keys.HOTBAR, s.getString("action", "menu:main"));
                if (meta instanceof SkullMeta sm && it.getType() == Material.PLAYER_HEAD) {
                    sm.setOwningPlayer(p);
                }
                it.setItemMeta(meta);
                p.getInventory().setItem(s.getInt("slot", 0), it);
            }
        }
        plugin.cosmetics().applyHat(p);
    }

    public ItemStack gameNotebook() {
        ItemStack it = ItemSpec.parse(null, plugin.menus().getConfigurationSection("notebook")).create();
        ItemMeta meta = it.getItemMeta();
        Keys.set(meta, Keys.HOTBAR, "notebook");
        it.setItemMeta(meta);
        return it;
    }

    public static String action(ItemStack item) {
        return Keys.get(item, Keys.HOTBAR);
    }
}
