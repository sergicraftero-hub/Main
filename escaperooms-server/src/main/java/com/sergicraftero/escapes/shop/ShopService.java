package com.sergicraftero.escapes.shop;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.profile.PlayerProfile;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * In-game purchases with the two currencies: keys (earned by playing) and gems (bought on the web store
 * through Tebex, which runs {@code /eradmin give <player> gems <n>}). Only rooms and cosmetics are sold —
 * nothing that helps solve a room.
 */
public final class ShopService {

    public enum Currency { KEYS, GEMS }

    private final EscapePlugin plugin;

    public ShopService(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean pay(Player p, Currency c, long amount) {
        PlayerProfile prof = plugin.profiles().get(p);
        long balance = c == Currency.KEYS ? prof.keys : prof.gems;
        if (balance < amount) {
            Msg.send(p, c == Currency.KEYS ? "shop.not-enough-keys" : "shop.not-enough-gems",
                    "price", amount, "balance", balance);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (c == Currency.KEYS) {
            prof.keys -= amount;
        } else {
            prof.gems -= amount;
        }
        prof.dirty = true;
        plugin.storage().saveAsync(prof); // purchases are saved at once
        return true;
    }

    public void buyRoom(Player p, RoomDef room, Currency c) {
        if (plugin.access().owns(p, room)) {
            Msg.send(p, "shop.already-owned");
            return;
        }
        long price = c == Currency.KEYS ? room.priceKeys : room.priceGems;
        if (price <= 0) {
            Msg.send(p, "shop.not-for-sale");
            return;
        }
        if (!pay(p, c, price)) {
            return;
        }
        PlayerProfile prof = plugin.profiles().get(p);
        prof.ownedRooms.add(room.id);
        prof.dirty = true;
        plugin.storage().saveAsync(prof);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Msg.send(p, "shop.bought-room", "room", Msg.parse(room.name));
        plugin.lobby().refresh(p);
    }

    public void buyCosmetic(Player p, String type, String id, Currency c) {
        ConfigurationSection def = plugin.cosmetics().get(type, id);
        if (def == null) {
            return;
        }
        if (plugin.cosmetics().owns(p, type, id)) {
            Msg.send(p, "shop.already-owned");
            return;
        }
        long[] price = plugin.cosmetics().price(type, id);
        long amount = c == Currency.KEYS ? price[0] : price[1];
        if (amount <= 0) {
            Msg.send(p, "shop.not-for-sale");
            return;
        }
        if (!pay(p, c, amount)) {
            return;
        }
        PlayerProfile prof = plugin.profiles().get(p);
        prof.ownedCosmetics.add(type + ":" + id);
        prof.dirty = true;
        plugin.storage().saveAsync(prof);
        plugin.cosmetics().equip(p, type, id);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Msg.send(p, "shop.bought-cosmetic", "item", Msg.parse(def.getString("name", id)));
        plugin.lobby().refresh(p);
    }

    public String storeUrl() {
        return plugin.getConfig().getString("store.url", "https://tienda.tuservidor.com");
    }
}
