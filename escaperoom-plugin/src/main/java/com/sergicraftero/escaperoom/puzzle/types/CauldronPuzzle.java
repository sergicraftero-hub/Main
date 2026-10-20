package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.action.Actions;
import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.room.ItemSpec;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Ingredients thrown into a cauldron (right-click with the item). In ordered mode a wrong
 * ingredient ruins the mixture and every ingredient is returned.
 *
 * <pre>
 * type: cauldron
 * cauldron: "1,2,3"
 * ingredients: [sal, mercurio, azufre]
 * ordered: true
 * </pre>
 */
public final class CauldronPuzzle extends AbstractPuzzle {

    private final String roomId;
    private final BlockPos cauldron;
    private final List<String> ingredients;
    private final boolean ordered;
    private final List<String> added = new ArrayList<>();

    public CauldronPuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "cauldron", s, logger);
        this.roomId = roomId;
        this.cauldron = BlockPos.parse(s.getString("cauldron", "0,0,0"));
        this.ingredients = s.getStringList("ingredients");
        this.ordered = s.getBoolean("ordered", true);
    }

    @Override
    public Set<BlockPos> interactables() {
        return Set.of(cauldron);
    }

    @Override
    public void reset(Game game) {
        added.clear();
    }

    @Override
    public void onInteract(Game game, Player player, PlayerInteractEvent event) {
        event.setCancelled(true);
        ItemStack hand = player.getInventory().getItemInMainHand();
        String tag = ItemSpec.tagOf(hand);
        String id = tag != null && tag.startsWith(roomId + ":") ? tag.substring(roomId.length() + 1) : null;
        Location top = cauldron.center(game.world()).add(0, 0.6, 0);
        if (id == null || !ingredients.contains(id)) {
            if (hand.getType().isAir()) {
                game.actionBar(game.plugin().messages().get("puzzle-cauldron-progress",
                        Messages.ph("added", added.size()), Messages.ph("total", ingredients.size())));
            } else {
                game.plugin().messages().send(player, "puzzle-cauldron-useless");
            }
            return;
        }
        if (added.contains(id)) {
            game.plugin().messages().send(player, "puzzle-cauldron-already");
            return;
        }
        if (ordered && !ingredients.get(added.size()).equals(id)) {
            ruin(game, player, top);
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        added.add(id);
        float hue = (float) added.size() / ingredients.size();
        java.awt.Color awt = java.awt.Color.getHSBColor(0.95f - hue * 0.9f, 0.8f, 1f);
        Object dust = Actions.particleData(Particle.DUST, String.format("#%06X", awt.getRGB() & 0xFFFFFF), 1.6f);
        Actions.spawnParticle(game.world(), Particle.DUST, top, 40, 0.35, 0.05, dust);
        game.world().spawnParticle(Particle.WITCH, top, 25, 0.3, 0.4, 0.3, 0.02);
        sound(game, top, "block.brewing_stand.brew", 1f, 0.7f + 0.15f * added.size());
        sound(game, top, "block.bubble_column.upwards_inside", 1f, 1f);
        game.actionBar(game.plugin().messages().get("puzzle-cauldron-progress",
                Messages.ph("added", added.size()), Messages.ph("total", ingredients.size())));
        if (added.size() == ingredients.size()) {
            solve(game, player);
        }
    }

    private void ruin(Game game, Player player, Location top) {
        for (String id : added) {
            ItemSpec spec = game.room().items().get(id);
            if (spec != null) {
                game.give(player, spec.create(1));
            }
        }
        added.clear();
        game.world().spawnParticle(Particle.LARGE_SMOKE, top, 30, 0.3, 0.5, 0.3, 0.03);
        game.world().spawnParticle(Particle.LAVA, top, 6, 0.2, 0.2, 0.2, 0);
        sound(game, top, "entity.generic.extinguish_fire", 1f, 0.6f);
        game.msg("puzzle-cauldron-ruined");
    }
}
