package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.util.BlockPos;
import com.sergicraftero.escaperoom.util.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Put the right block/item in specific positions: flowers in pots, blocks on pedestals...
 * A slot counts as filled when its block type is no longer the {@code reset-to} type.
 *
 * <pre>
 * type: placement
 * reset-to: FLOWER_POT    # AIR for block placement slots
 * ordered: true           # slots must be filled in the order listed
 * slots:
 *   "1,2,3": [POTTED_POPPY, POTTED_RED_TULIP]
 *   "2,2,3": [POTTED_DANDELION]
 * </pre>
 */
public final class PlacementPuzzle extends AbstractPuzzle {

    private final Map<BlockPos, Set<Material>> slots = new LinkedHashMap<>();
    private final List<BlockPos> order = new ArrayList<>();
    private final Material resetTo;
    private final boolean ordered;
    private final boolean refund;
    private final Set<BlockPos> filled = new LinkedHashSet<>();

    public PlacementPuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "placement", s, logger);
        Material reset = Material.matchMaterial(s.getString("reset-to", "AIR"));
        this.resetTo = reset == null ? Material.AIR : reset;
        this.ordered = s.getBoolean("ordered", false);
        this.refund = s.getBoolean("refund", true);
        positionMap(s, "slots").forEach((pos, value) -> {
            Set<Material> accepted = EnumSet.noneOf(Material.class);
            if (value instanceof List<?> list) {
                for (Object o : list) {
                    Material m = Material.matchMaterial(String.valueOf(o));
                    if (m != null) {
                        accepted.add(m);
                    }
                }
            } else {
                Material m = Material.matchMaterial(String.valueOf(value));
                if (m != null) {
                    accepted.add(m);
                }
            }
            slots.put(pos, accepted);
            order.add(pos);
        });
    }

    @Override
    public Set<BlockPos> interactables() {
        return slots.keySet();
    }

    @Override
    public Set<BlockPos> placeables() {
        return Blocks.isAir(resetTo) ? slots.keySet() : Set.of();
    }

    @Override
    public void reset(Game game) {
        filled.clear();
    }

    @Override
    public void onInteract(Game game, Player player, PlayerInteractEvent event) {
        BlockPos pos = BlockPos.of(event.getClickedBlock());
        if (filled.contains(pos)) {
            event.setCancelled(true);
            return;
        }
        scheduleCheck(game, player);
    }

    @Override
    public void onBlockPlace(Game game, Player player, BlockPlaceEvent event) {
        scheduleCheck(game, player);
    }

    private void scheduleCheck(Game game, Player player) {
        int runId = game.runId();
        Bukkit.getScheduler().runTask(game.plugin(), () -> {
            if (game.runId() == runId && game.isRunning() && state() == com.sergicraftero.escaperoom.puzzle.PuzzleState.ACTIVE) {
                evaluate(game, player);
            }
        });
    }

    private void evaluate(Game game, Player player) {
        for (BlockPos pos : order) {
            Block block = pos.block(game.world());
            Material type = block.getType();
            boolean occupied = type != resetTo && !Blocks.isAir(type);
            if (!occupied || filled.contains(pos)) {
                continue;
            }
            boolean right = slots.get(pos).contains(type) && (!ordered || order.indexOf(pos) == filled.size());
            if (!right) {
                fail(game, player);
                return;
            }
            filled.add(pos);
            game.world().spawnParticle(Particle.HAPPY_VILLAGER, pos.center(game.world()), 10, 0.3, 0.3, 0.3, 0);
            sound(game, pos.center(game.world()), "block.note_block.chime", 0.8f, 0.8f + 0.15f * filled.size());
        }
        if (filled.size() == slots.size()) {
            solve(game, player);
        }
    }

    private void fail(Game game, Player player) {
        for (BlockPos pos : order) {
            Block block = pos.block(game.world());
            Material type = block.getType();
            if (type != resetTo && !Blocks.isAir(type)) {
                if (refund && player != null) {
                    Material item = type.name().startsWith("POTTED_")
                            ? Material.matchMaterial(type.name().substring("POTTED_".length())) : type;
                    if (item != null && item.isItem()) {
                        game.give(player, new ItemStack(item));
                    }
                }
                block.setType(resetTo, false);
                game.world().spawnParticle(Particle.SMOKE, pos.center(game.world()), 8, 0.2, 0.2, 0.2, 0.01);
            }
        }
        filled.clear();
        sound(game, player != null ? player.getLocation() : order.get(0).center(game.world()),
                "block.note_block.bass", 1f, 0.5f);
        game.msg("puzzle-placement-wrong", Messages.ph("puzzle", displayName()));
    }
}
