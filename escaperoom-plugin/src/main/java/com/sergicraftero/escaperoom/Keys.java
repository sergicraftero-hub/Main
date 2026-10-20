package com.sergicraftero.escaperoom;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent data container keys used across the plugin. */
public final class Keys {

    public final NamespacedKey item;
    public final NamespacedKey decor;
    public final NamespacedKey temp;
    public final NamespacedKey joinSign;
    public final NamespacedKey wand;
    public final NamespacedKey leaderboard;

    public Keys(Plugin plugin) {
        this.item = new NamespacedKey(plugin, "item");
        this.decor = new NamespacedKey(plugin, "decor");
        this.temp = new NamespacedKey(plugin, "temp");
        this.joinSign = new NamespacedKey(plugin, "join_sign");
        this.wand = new NamespacedKey(plugin, "wand");
        this.leaderboard = new NamespacedKey(plugin, "leaderboard");
    }
}
