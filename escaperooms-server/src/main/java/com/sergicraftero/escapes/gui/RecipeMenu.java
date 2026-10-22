package com.sergicraftero.escapes.gui;

import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * A room workbench: lists the room recipes (YAML {@code recipes:}) the player can make with what they carry.
 * No free item slots, so nothing can be lost or duplicated.
 * <pre>
 * recipes:
 *   - {id: lens, inputs: [glass_shard, brass_ring], output: magnifier, message: "..."}
 * </pre>
 */
public final class RecipeMenu extends Menu {

    public RecipeMenu(Game game, Player p, List<String> allowed, String title) {
        super(3, MenuStyle.title(p, "generic", Msg.parse(title == null ? Msg.raw("recipes.title") : title)));
        List<ConfigurationSection> recipes = new ArrayList<>();
        for (ConfigurationSection r : game.room().recipes) {
            if (allowed.isEmpty() || allowed.contains(r.getString("id"))) {
                recipes.add(r);
            }
        }
        int slot = 10;
        boolean any = false;
        for (ConfigurationSection r : recipes) {
            List<String> inputs = Yml.strings(r, "inputs");
            boolean canMake = !inputs.isEmpty();
            for (String in : inputs) {
                if (!Game.has(p, in)) {
                    canMake = false;
                }
            }
            if (!canMake) {
                continue;
            }
            any = true;
            ItemStack icon = game.item(r.getString("output", ""), 1);
            if (icon == null) {
                continue;
            }
            ItemMeta meta = icon.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(Msg.item(Msg.raw("recipes.click")));
            meta.lore(lore);
            icon.setItemMeta(meta);
            set(slot++, icon, c -> craft(game, c.player(), r));
            if (slot == 17) {
                slot = 19;
            }
        }
        if (!any) {
            set(13, MenuStyle.button(Material.BARRIER, Msg.raw("recipes.nothing")));
        }
        fill(MenuStyle.filler());
    }

    private void craft(Game game, Player p, ConfigurationSection r) {
        List<String> inputs = Yml.strings(r, "inputs");
        for (String in : inputs) {
            if (!Game.has(p, in)) {
                p.closeInventory();
                return;
            }
        }
        if (r.getBoolean("consume", true)) {
            for (String in : inputs) {
                game.consume(p, in);
            }
        }
        p.closeInventory();
        game.giveItem(p, r.getString("output", ""), r.getInt("amount", 1));
        String msg = r.getString("message");
        if (msg != null) {
            game.broadcast(Msg.parse(msg, "player", p.getName()));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.1f);
        game.runActions(com.sergicraftero.escapes.action.Actions.parseList(r, "actions", game.plugin().getLogger(),
                game.room().id + "/recipe"), p);
    }
}
