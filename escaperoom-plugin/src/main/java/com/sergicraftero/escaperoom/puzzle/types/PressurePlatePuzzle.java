package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.room.ItemSpec;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Pressure plates that must be pressed at the same time (cooperative) or in sequence.
 * When {@code solo-alternative} is set, a solo player receives a heavy item (an anvil) that can
 * be placed on a plate to keep it pressed permanently.
 *
 * <pre>
 * type: plates
 * plates: ["1,2,3", "8,2,3"]
 * mode: simultaneous      # or sequence
 * solo-alternative: true
 * solo-item: yunque       # room item id given to solo players
 * </pre>
 */
public final class PressurePlatePuzzle extends AbstractPuzzle {

    private final String roomId;
    private final List<BlockPos> plates = new ArrayList<>();
    private final boolean sequence;
    private final boolean soloAlternative;
    private final String soloItem;
    private final String weightBlock;
    private final Set<BlockPos> weighted = new HashSet<>();
    private Set<BlockPos> lastPressed = new HashSet<>();
    private int sequenceIndex;
    private boolean soloItemGiven;

    public PressurePlatePuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "plates", s, logger);
        this.roomId = roomId;
        s.getStringList("plates").forEach(raw -> plates.add(BlockPos.parse(raw)));
        this.sequence = "sequence".equals(s.getString("mode", "simultaneous").toLowerCase(Locale.ROOT));
        this.soloAlternative = s.getBoolean("solo-alternative", true);
        this.soloItem = s.getString("solo-item", "");
        this.weightBlock = s.getString("weight-block", "minecraft:anvil[facing=north]");
    }

    @Override
    public Set<BlockPos> interactables() {
        return new LinkedHashSet<>(plates);
    }

    @Override
    public void reset(Game game) {
        weighted.clear();
        lastPressed = new HashSet<>();
        sequenceIndex = 0;
        soloItemGiven = false;
    }

    @Override
    public void onActivate(Game game) {
        giveSoloItem(game);
    }

    @Override
    public void onModeChange(Game game) {
        giveSoloItem(game);
    }

    private void giveSoloItem(Game game) {
        if (!soloAlternative || soloItemGiven || !game.isSolo() || soloItem.isEmpty()) {
            return;
        }
        ItemSpec spec = game.room().items().get(soloItem);
        Player player = game.participants().stream().findFirst().orElse(null);
        if (spec == null || player == null) {
            return;
        }
        soloItemGiven = true;
        game.give(player, spec.create(1));
        game.msg("puzzle-plates-solo", Messages.ph("puzzle", displayName()));
    }

    @Override
    public void onInteract(Game game, Player player, PlayerInteractEvent event) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!soloAlternative || soloItem.isEmpty() || !(roomId + ":" + soloItem).equals(ItemSpec.tagOf(hand))) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        BlockPos pos = BlockPos.of(block);
        if (weighted.contains(pos)) {
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        BlockData weight = Bukkit.createBlockData(weightBlock);
        block.setBlockData(weight, false);
        weighted.add(pos);
        sound(game, block.getLocation(), "block.anvil.land", 0.8f, 0.9f);
        game.msg("puzzle-plates-weight");
    }

    @Override
    public void tick(Game game, long tick) {
        if (tick % 4 != 0) {
            return;
        }
        Set<BlockPos> pressed = new HashSet<>();
        Player presser = null;
        for (BlockPos pos : plates) {
            Player on = playerOn(game, pos);
            if (weighted.contains(pos) || on != null || isPowered(pos.block(game.world()))) {
                pressed.add(pos);
                if (on != null) {
                    presser = on;
                }
            }
        }
        for (BlockPos pos : pressed) {
            if (!lastPressed.contains(pos)) {
                onPress(game, pos);
            }
        }
        lastPressed = pressed;
        if (!sequence && pressed.size() == plates.size()) {
            solve(game, presser);
        } else if (sequence && sequenceIndex >= plates.size()) {
            solve(game, presser);
        }
    }

    private void onPress(Game game, BlockPos pos) {
        game.world().spawnParticle(Particle.ELECTRIC_SPARK, pos.center(game.world()), 12, 0.3, 0.1, 0.3, 0.05);
        if (sequence) {
            if (plates.get(sequenceIndex).equals(pos)) {
                sequenceIndex++;
                sound(game, pos.center(game.world()), "block.note_block.bell", 0.8f, 0.8f + sequenceIndex * 0.15f);
            } else {
                sequenceIndex = plates.get(0).equals(pos) ? 1 : 0;
                sound(game, pos.center(game.world()), "block.note_block.bass", 0.8f, 0.5f);
            }
        } else {
            int count = lastPressed.size() + 1;
            sound(game, pos.center(game.world()), "block.stone_pressure_plate.click_on", 0.8f, 0.7f + 0.2f * count);
            game.actionBar(game.plugin().messages().get("puzzle-plates-progress",
                    Messages.ph("pressed", Math.min(count, plates.size())), Messages.ph("total", plates.size())));
        }
    }

    private static boolean isPowered(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof AnaloguePowerable analogue) {
            return analogue.getPower() > 0;
        }
        return data instanceof Powerable powerable && powerable.isPowered();
    }

    private static Player playerOn(Game game, BlockPos pos) {
        for (Player p : game.participants()) {
            if (BlockPos.of(p.getLocation()).equals(pos)) {
                return p;
            }
        }
        return null;
    }
}
