package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Get somewhere: the end of a parkour, the top of the tower, a hidden alcove.
 * <pre>
 * type: reach
 * region: "@tower.top"
 * all: false          # true: every player must get there
 * </pre>
 */
public final class ReachPuzzle extends Puzzle {

    private BoundingBox region;
    private final Set<UUID> inside = new HashSet<>();

    public ReachPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        region = game.box(str("region", str("at", "@spawn")));
    }

    @Override
    public void onMove(Player p, Block to) {
        if (region == null || !region.contains(p.getLocation().toVector())) {
            return;
        }
        inside.add(p.getUniqueId());
        if (!bool("all", false) || inside.size() >= game.teamSize()) {
            solve(p);
        } else {
            com.sergicraftero.escapes.util.Msg.actionBar(p, "puzzle.reach-wait", "here", inside.size(), "total", game.teamSize());
        }
    }

    @Override
    public void tick() {
        if (region == null || !bool("all", false)) {
            return;
        }
        inside.removeIf(u -> game.players().stream().noneMatch(p -> p.getUniqueId().equals(u)
                && region.contains(p.getLocation().toVector())));
    }
}
