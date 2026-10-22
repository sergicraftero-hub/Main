package com.sergicraftero.escapes.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Persistent data keys used to tag items and entities created by the plugin. */
public final class Keys {

    /** Room item id (red_key, uv_lamp...). */
    public static NamespacedKey ITEM_ID;
    /** Comma separated item tags (uv, tool...). */
    public static NamespacedKey ITEM_TAGS;
    /** Hotbar item action in the lobby / game (menu, hints, leave...). */
    public static NamespacedKey HOTBAR;
    /** Marks entities spawned for an instance so they can be cleaned up. */
    public static NamespacedKey INSTANCE;
    /** Prop id on an entity. */
    public static NamespacedKey PROP;
    /** Lobby NPC / hologram id. */
    public static NamespacedKey LOBBY;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM_ID = new NamespacedKey(plugin, "item_id");
        ITEM_TAGS = new NamespacedKey(plugin, "item_tags");
        HOTBAR = new NamespacedKey(plugin, "hotbar");
        INSTANCE = new NamespacedKey(plugin, "instance");
        PROP = new NamespacedKey(plugin, "prop");
        LOBBY = new NamespacedKey(plugin, "lobby");
    }

    public static String get(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static String get(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.get(key, PersistentDataType.STRING);
    }

    public static void set(ItemMeta meta, NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    }

    public static void set(PersistentDataContainer pdc, NamespacedKey key, String value) {
        pdc.set(key, PersistentDataType.STRING, value);
    }

    public static String itemId(ItemStack item) {
        return get(item, ITEM_ID);
    }

    public static boolean hasTag(ItemStack item, String tag) {
        String tags = get(item, ITEM_TAGS);
        if (tags == null) {
            return false;
        }
        for (String t : tags.split(",")) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }
}
