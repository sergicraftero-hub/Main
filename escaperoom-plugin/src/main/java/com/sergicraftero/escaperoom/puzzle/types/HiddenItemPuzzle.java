package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.room.ItemSpec;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.logging.Logger;

/**
 * An item hidden in a container (solved when a player picks it up) or found by right-clicking a
 * "search" block.
 *
 * <pre>
 * type: hidden-item
 * item: sal
 * container: "1,2,3"   # barrel / chest that will hold the item
 * slot: 13
 * search-block: "4,5,6" # alternative: right-click to find it
 * </pre>
 */
public final class HiddenItemPuzzle extends AbstractPuzzle {

    private final String item;
    private final BlockPos container;
    private final int slot;
    private final BlockPos searchBlock;

    public HiddenItemPuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "hidden-item", s, logger);
        this.item = s.getString("item", "");
        this.container = s.contains("container") ? BlockPos.parse(s.getString("container")) : null;
        this.slot = s.getInt("slot", -1);
        this.searchBlock = s.contains("search-block") ? BlockPos.parse(s.getString("search-block")) : null;
    }

    @Override
    public Set<BlockPos> interactables() {
        return searchBlock == null ? Set.of() : Set.of(searchBlock);
    }

    @Override
    public void reset(Game game) {
        ItemSpec spec = game.room().items().get(item);
        if (container == null || spec == null) {
            return;
        }
        Block block = container.block(game.world());
        if (!(block.getState() instanceof Container c)) {
            return;
        }
        Inventory inv = c.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (spec.matches(stack)) {
                return;
            }
        }
        if (slot >= 0 && slot < inv.getSize() && (inv.getItem(slot) == null || inv.getItem(slot).getType().isAir())) {
            inv.setItem(slot, spec.create(1));
        } else {
            inv.addItem(spec.create(1));
        }
    }

    @Override
    public void onInteract(Game game, Player player, PlayerInteractEvent event) {
        if (searchBlock == null) {
            return;
        }
        event.setCancelled(true);
        ItemSpec spec = game.room().items().get(item);
        if (spec != null) {
            game.give(player, spec.create(1));
        }
        sound(game, event.getClickedBlock().getLocation(), "item.armor.equip_leather", 1f, 0.7f);
        solve(game, player);
    }

    @Override
    public void tick(Game game, long tick) {
        if (tick % 10 != 0) {
            return;
        }
        ItemSpec spec = game.room().items().get(item);
        if (spec == null) {
            return;
        }
        for (Player p : game.participants()) {
            for (ItemStack stack : p.getInventory().getContents()) {
                if (spec.matches(stack)) {
                    solve(game, p);
                    return;
                }
            }
            if (spec.matches(p.getItemOnCursor())) {
                solve(game, p);
                return;
            }
        }
    }
}
